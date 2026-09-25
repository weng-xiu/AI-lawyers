package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * {@link MockStreamTtsEngine} 流式合成与取消测试（P3-A1）。
 *
 * @author ai-lawyers
 */
class MockStreamTtsEngineTest
{
    /** 收集到的音频分片 */
    private static final class Chunk
    {
        final int seq;

        final int chunkCount;

        final byte[] pcm;

        Chunk(int seq, int chunkCount, byte[] pcm)
        {
            this.seq = seq;
            this.chunkCount = chunkCount;
            this.pcm = pcm;
        }
    }

    @Test
    void synthesize16k_emitsOrderedChunksThenEnd() throws Exception
    {
        // "你好" 2 字 → 160ms（下限）→ 8 个 20ms 分片
        CopyOnWriteArrayList<Chunk> chunks = new CopyOnWriteArrayList<>();
        CountDownLatch endLatch = new CountDownLatch(1);
        MockStreamTtsEngine engine = new MockStreamTtsEngine();
        engine.synthesizeStream("你好", null, 16000, callback(chunks, endLatch, new CountDownLatch(1)));

        assertThat(endLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(chunks).hasSize(8);
        for (int i = 0; i < chunks.size(); i++)
        {
            Chunk c = chunks.get(i);
            assertThat(c.seq).isEqualTo(i);
            assertThat(c.chunkCount).isEqualTo(8);
            // 16kHz × 20ms × S16 单声道 = 640 字节
            assertThat(c.pcm).hasSize(640);
        }
    }

    @Test
    void synthesize8k_emits320ByteFrames() throws Exception
    {
        CopyOnWriteArrayList<Chunk> chunks = new CopyOnWriteArrayList<>();
        CountDownLatch endLatch = new CountDownLatch(1);
        new MockStreamTtsEngine().synthesizeStream("测", null, 8000,
                callback(chunks, endLatch, new CountDownLatch(1)));
        assertThat(endLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(chunks).isNotEmpty();
        for (Chunk c : chunks)
        {
            // 8kHz × 20ms × 2 字节 = 320
            assertThat(c.pcm).hasSize(320);
        }
    }

    @Test
    void cancel_stopsFurtherChunksAndNoEnd() throws Exception
    {
        CopyOnWriteArrayList<Chunk> chunks = new CopyOnWriteArrayList<>();
        CountDownLatch endLatch = new CountDownLatch(1);
        // 50 字 → 夹取 4000ms → 200 分片（约 8s），取消后必须很快停止
        StringBuilder sb = new StringBuilder(50);
        for (int i = 0; i < 50; i++)
        {
            sb.append('法');
        }
        StreamSynthesis handle = new MockStreamTtsEngine().synthesizeStream(
                sb.toString(), null, 16000, callback(chunks, endLatch, new CountDownLatch(1)));
        handle.cancel();

        assertThat(endLatch.await(1, TimeUnit.SECONDS)).isFalse();
        // 取消在首片前后生效，最多允许少量在途分片
        assertThat(chunks.size()).isLessThan(3);
        assertThat(handle.isCancelled()).isTrue();
    }

    @Test
    void invalidArgs_throw()
    {
        MockStreamTtsEngine engine = new MockStreamTtsEngine();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> engine.synthesizeStream(" ", null, 16000, null));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> engine.synthesizeStream("x", null, 44100, callback(
                        new CopyOnWriteArrayList<>(), new CountDownLatch(1), new CountDownLatch(1))));
    }

    private TtsStreamCallback callback(List<Chunk> chunks, CountDownLatch endLatch, CountDownLatch errLatch)
    {
        return new TtsStreamCallback()
        {
            @Override
            public void onAudio(int seq, int chunkCount, byte[] pcm)
            {
                chunks.add(new Chunk(seq, chunkCount, pcm));
            }

            @Override
            public void onEnd()
            {
                endLatch.countDown();
            }

            @Override
            public void onError(String code, String message)
            {
                errLatch.countDown();
            }
        };
    }
}
