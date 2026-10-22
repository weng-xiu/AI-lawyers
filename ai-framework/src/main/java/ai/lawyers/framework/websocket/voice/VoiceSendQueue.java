package ai.lawyers.framework.websocket.voice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 语音会话单线程保序发送队列（P5 从 {@link VoiceSession} 拆出）。
 *
 * <p>所有下发帧入队后由唯一 sender 线程经 {@code getBasicRemote()} 阻塞写出，
 * 符合 JSR-356 "同一 RemoteEndpoint 禁止多线程并发发送"约束。barge-in/打断时
 * 可整批清空尚未发出的 tts 分片；{@link #poison()} 幂等终止 sender 线程，
 * 供 {@code @OnClose} 释放。</p>
 *
 * @author ai-lawyers
 */
final class VoiceSendQueue
{
    private static final Logger log = LoggerFactory.getLogger(VoiceSendQueue.class);

    /** 队列满时入队等待上限（ms） */
    private static final long ENQUEUE_TIMEOUT_MS = 200L;

    private final Session wsSession;

    private final String connId;

    private final LinkedBlockingQueue<Entry> queue;

    private final Thread sender;

    private final AtomicLong droppedFrames = new AtomicLong();

    private volatile boolean closed;

    VoiceSendQueue(Session wsSession, String connId, int capacity)
    {
        this.wsSession = wsSession;
        this.connId = connId;
        int cap = capacity > 0 ? capacity : 1000;
        this.queue = new LinkedBlockingQueue<>(cap);
        this.sender = new Thread(this::runSender, "voice-sender-" + connId);
        this.sender.setDaemon(true);
        this.sender.start();
    }

    /** 下发一帧；队列已终止后静默丢弃，队列满时计数丢帧并周期性告警 */
    void send(Entry.Kind kind, String payload)
    {
        if (closed)
        {
            return;
        }
        try
        {
            if (queue.offer(new Entry(kind, payload), ENQUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            {
                return;
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        long n = droppedFrames.incrementAndGet();
        if (n == 1 || n % 100 == 0)
        {
            log.warn("VoiceWS 发送队列满，丢帧 conn={}, 累计丢弃={}", connId, n);
        }
    }

    /** barge-in/打断：清空队列中尚未发出的 tts 分片，其余帧保序保留 */
    void drainTts()
    {
        List<Entry> kept = new ArrayList<>();
        queue.drainTo(kept);
        for (Entry e : kept)
        {
            if (e.kind != Entry.Kind.TTS)
            {
                queue.offer(e);
            }
        }
    }

    /** 终止 sender 线程（幂等）：终止后 send 静默丢弃 */
    void poison()
    {
        closed = true;
        queue.offer(Entry.POISON);
    }

    long getDroppedFrames()
    {
        return droppedFrames.get();
    }

    private void runSender()
    {
        while (true)
        {
            try
            {
                Entry e = queue.take();
                if (e == Entry.POISON)
                {
                    return;
                }
                if (wsSession.isOpen())
                {
                    // 单线程 + BasicRemote：天然保序且线程安全
                    wsSession.getBasicRemote().sendText(e.payload);
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (IOException e)
            {
                // 对端断开期间 @OnClose 会随后触发并 shutdown，这里不重复关闭
                log.debug("VoiceWS send fail conn={}: {}", connId, e.getMessage());
            }
            catch (Exception e)
            {
                log.error("VoiceWS sender 异常 conn={}", connId, e);
            }
        }
    }

    /** 发送队列条目：区分 TTS 分片以便 barge-in 整批丢弃，POISON 终止 sender */
    static final class Entry
    {
        static final Entry POISON = new Entry(Kind.POISON, "");

        enum Kind
        {
            TTS, OTHER, POISON
        }

        final Kind kind;

        final String payload;

        Entry(Kind kind, String payload)
        {
            this.kind = kind;
            this.payload = payload;
        }
    }
}
