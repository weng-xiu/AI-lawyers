package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;

/**
 * 语音下发保序发送队列测试：单线程保序、队列满丢帧计数、barge-in 仅清 tts、
 * poison 幂等终止、对端关闭不发送、单帧 IOException 不中断后续发送。
 *
 * @author ai-lawyers
 */
class VoiceSendQueueTest
{
    private Session wsSession;

    private RemoteEndpoint.Basic remote;

    private CountDownLatch started;

    private CountDownLatch gate;

    private VoiceSendQueue queue;

    @BeforeEach
    void setUp()
    {
        wsSession = mock(Session.class);
        remote = mock(RemoteEndpoint.Basic.class);
        when(wsSession.getBasicRemote()).thenReturn(remote);
        when(wsSession.isOpen()).thenReturn(true);
        started = new CountDownLatch(1);
        gate = new CountDownLatch(1);
    }

    @AfterEach
    void tearDown()
    {
        gate.countDown();
        if (queue != null)
        {
            queue.poison();
        }
    }

    /** 首帧发送阻塞在闸门上，便于构造“在途 1 帧 + 队列积压”的确定性场景 */
    private void blockFirstSend() throws Exception
    {
        doAnswer(invocation ->
        {
            started.countDown();
            gate.await(3, TimeUnit.SECONDS);
            return null;
        }).when(remote).sendText(anyString());
    }

    @Test
    void 多帧按入队顺序发送() throws Exception
    {
        blockFirstSend();
        queue = new VoiceSendQueue(wsSession, "c-1", 16);

        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "1");
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "2");
        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "3");
        gate.countDown();

        verify(remote, timeout(3000).times(3)).sendText(anyString());
        InOrder order = inOrder(remote);
        order.verify(remote).sendText("1");
        order.verify(remote).sendText("2");
        order.verify(remote).sendText("3");
    }

    @Test
    void 队列满时丢弃并累加丢帧数() throws Exception
    {
        blockFirstSend();
        queue = new VoiceSendQueue(wsSession, "c-2", 1);

        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "in-flight");
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "queued");
        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "overflow");

        assertThat(queue.getDroppedFrames()).isEqualTo(1);
        gate.countDown();
        verify(remote, timeout(3000).times(2)).sendText(anyString());
    }

    @Test
    void 打断时只清空未发送的tts分片() throws Exception
    {
        blockFirstSend();
        queue = new VoiceSendQueue(wsSession, "c-3", 16);

        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "o1");
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        queue.send(VoiceSendQueue.Entry.Kind.TTS, "t1");
        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "o2");
        queue.send(VoiceSendQueue.Entry.Kind.TTS, "t2");
        queue.drainTts();
        gate.countDown();

        verify(remote, timeout(3000).times(2)).sendText(anyString());
        verify(remote, never()).sendText("t1");
        verify(remote, never()).sendText("t2");
        InOrder order = inOrder(remote);
        order.verify(remote).sendText("o1");
        order.verify(remote).sendText("o2");
    }

    @Test
    void poison幂等且终止后静默丢弃() throws Exception
    {
        queue = new VoiceSendQueue(wsSession, "c-4", 16);

        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "x");
        queue.poison();
        queue.poison();
        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "y");

        verify(remote, timeout(3000)).sendText("x");
        verify(remote, after(300).never()).sendText("y");
        assertThat(queue.getDroppedFrames()).isZero();
    }

    @Test
    void 对端已关闭时不再发送() throws Exception
    {
        when(wsSession.isOpen()).thenReturn(false);
        queue = new VoiceSendQueue(wsSession, "c-5", 16);

        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "x");
        queue.poison();

        verify(remote, after(300).never()).sendText(anyString());
    }

    @Test
    void 单帧发送异常不中断后续帧() throws Exception
    {
        doThrow(new IOException("broken pipe")).doNothing().when(remote).sendText(anyString());
        queue = new VoiceSendQueue(wsSession, "c-6", 16);

        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "a");
        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "b");

        verify(remote, timeout(3000).times(2)).sendText(anyString());
    }

    @Test
    void 非正容量回退默认容量() throws Exception
    {
        queue = new VoiceSendQueue(wsSession, "c-7", 0);

        queue.send(VoiceSendQueue.Entry.Kind.OTHER, "x");

        verify(remote, timeout(3000)).sendText("x");
    }
}
