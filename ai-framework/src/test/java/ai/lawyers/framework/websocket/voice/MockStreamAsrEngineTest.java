package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * {@link MockStreamAsrEngine} 流式 Mock 行为测试（P3-A1）。
 *
 * @author ai-lawyers
 */
class MockStreamAsrEngineTest
{
    private final VoiceAsrContext ctx = new VoiceAsrContext("s1", "agent", "pcm", 16000);

    @Test
    void feed_emitsPartialAndClose_emitsFinalOnce() throws Exception
    {
        CopyOnWriteArrayList<String> partials = new CopyOnWriteArrayList<>();
        CountDownLatch partialLatch = new CountDownLatch(2);
        CountDownLatch finalLatch = new CountDownLatch(1);
        AtomicReference<String> finalText = new AtomicReference<>();

        MockStreamAsrEngine engine = new MockStreamAsrEngine();
        engine.open(ctx, new AsrStreamCallback()
        {
            @Override
            public void onPartial(String text)
            {
                partials.add(text);
                partialLatch.countDown();
            }

            @Override
            public void onFinal(String text)
            {
                finalText.set(text);
                finalLatch.countDown();
            }

            @Override
            public void onError(String code, String message)
            {
            }
        });

        engine.feed(new byte[64]);
        engine.feed(new byte[64]);
        assertThat(partialLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(partials).hasSize(2);
        assertThat(partials.get(1)).contains("2 帧").contains("128 字节");

        engine.close();
        assertThat(finalLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(finalText.get()).contains("【Mock 识别结果】").contains("2 帧").contains("128 字节").contains("16000Hz");

        // close 幂等，final 不重复
        engine.close();
        assertThat(finalLatch.getCount()).isZero();
    }

    @Test
    void feedAfterClose_isIgnored() throws Exception
    {
        CopyOnWriteArrayList<String> partials = new CopyOnWriteArrayList<>();
        CountDownLatch finalLatch = new CountDownLatch(1);
        MockStreamAsrEngine engine = new MockStreamAsrEngine();
        engine.open(ctx, new AsrStreamCallback()
        {
            @Override
            public void onPartial(String text)
            {
                partials.add(text);
            }

            @Override
            public void onFinal(String text)
            {
                finalLatch.countDown();
            }

            @Override
            public void onError(String code, String message)
            {
            }
        });
        engine.close();
        assertThat(finalLatch.await(3, TimeUnit.SECONDS)).isTrue();

        engine.feed(new byte[100]);
        Thread.sleep(300);
        assertThat(partials).isEmpty();
    }

    @Test
    void open_rejectsNullArgs()
    {
        MockStreamAsrEngine engine = new MockStreamAsrEngine();
        try
        {
            engine.open(null, null);
            org.junit.jupiter.api.Assertions.fail("expected exception");
        }
        catch (IllegalArgumentException expected)
        {
            // pass
        }
    }
}
