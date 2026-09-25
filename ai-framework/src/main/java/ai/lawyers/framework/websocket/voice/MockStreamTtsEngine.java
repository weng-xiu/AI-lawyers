package ai.lawyers.framework.websocket.voice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock 流式 TTS 引擎（P3-A1，无凭证/无外网联调用）。
 *
 * <p><b>显式 Mock</b>：合成内容为低幅正弦音（非真实语音），按 20ms/片切分 S16LE 单声道 PCM，
 * 片间 {@value #CHUNK_INTERVAL_MS}ms 间隔模拟流式首包/续包，可经浏览器 AudioContext 直接播放联调。
 * 真实 Provider（DashScope 余弦等）随 P3-A3 接入；未配置真实引擎时由会话层显式回 error，
 * 本类不冒充任何真实音色。</p>
 *
 * <p>线程模型：全局共享守护线程池（按合成任务数伸缩，空闲回收），单连接关闭不遗留线程；
 * 每个合成任务返回可取消句柄，barge-in 时中断 sleep、停止后续分片。</p>
 *
 * @author ai-lawyers
 */
public class MockStreamTtsEngine implements StreamTtsEngine
{
    public static final String ENGINE_CODE = "mock";

    private static final Logger log = LoggerFactory.getLogger(MockStreamTtsEngine.class);

    /** 单帧 20ms */
    static final int FRAME_MS = 20;

    /** 片间流式间隔（ms） */
    static final long CHUNK_INTERVAL_MS = 40L;

    /** 单字估算时长（ms），夹取范围 */
    static final long MS_PER_CHAR = 80L;

    static final long MIN_DURATION_MS = 160L;

    static final long MAX_DURATION_MS = 4000L;

    /** 低幅正弦音量（S16 满幅 32767，取约 1/40 避免刺耳） */
    static final int SINE_AMPLITUDE = 800;

    static final int SINE_FREQ_HZ = 220;

    /** 共享合成线程池（守护线程，60s 空闲回收） */
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "voice-mock-tts");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    /** 无状态单例（合成任务互不影响） */
    public static final MockStreamTtsEngine INSTANCE = new MockStreamTtsEngine();

    public MockStreamTtsEngine()
    {
    }

    @Override
    public String engineCode()
    {
        return ENGINE_CODE;
    }

    @Override
    public StreamSynthesis synthesizeStream(String text, String voice, int sampleRate, TtsStreamCallback callback)
    {
        if (text == null || text.trim().isEmpty())
        {
            throw new IllegalArgumentException("text required");
        }
        if (sampleRate != 8000 && sampleRate != 16000)
        {
            throw new IllegalArgumentException("unsupported sampleRate: " + sampleRate);
        }
        if (WARNED.compareAndSet(false, true))
        {
            log.warn("========== Mock 语音引擎 ========== 流式 TTS 使用 Mock 实现，输出低幅正弦 PCM 音调"
                    + "（非真实语音），仅用于无凭证链路联调；真实 Provider 随 P3-A3 接入");
        }
        else
        {
            log.debug("流式 TTS Mock 合成 chars={}, sampleRate={}, voice={}", text.length(), sampleRate, voice);
        }

        long durationMs = Math.max(MIN_DURATION_MS,
                Math.min(MAX_DURATION_MS, (long) text.length() * MS_PER_CHAR));
        int chunkCount = (int) ((durationMs + FRAME_MS - 1) / FRAME_MS);
        int samplesPerChunk = sampleRate * FRAME_MS / 1000;

        MockSynthesis handle = new MockSynthesis();
        Future<?> future = POOL.submit(() ->
                runSynthesis(handle, chunkCount, samplesPerChunk, sampleRate, callback));
        handle.setFuture(future);
        return handle;
    }

    private void runSynthesis(MockSynthesis handle, int chunkCount, int samplesPerChunk,
            int sampleRate, TtsStreamCallback callback)
    {
        try
        {
            for (int seq = 0; seq < chunkCount; seq++)
            {
                if (handle.isCancelled())
                {
                    return;
                }
                byte[] pcm = sineFrame(seq, samplesPerChunk, sampleRate);
                if (handle.isCancelled())
                {
                    return;
                }
                callback.onAudio(seq, chunkCount, pcm);
                Thread.sleep(CHUNK_INTERVAL_MS);
            }
            if (!handle.isCancelled())
            {
                callback.onEnd();
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (Exception e)
        {
            if (!handle.isCancelled())
            {
                try
                {
                    callback.onError("TTS_MOCK_ERROR", e.getMessage());
                }
                catch (Exception ignore)
                {
                    // 回调异常不再上抛
                }
            }
        }
    }

    /** 生成一帧低幅正弦 PCM（S16LE 单声道），相位跨帧连续避免咔哒声 */
    private byte[] sineFrame(int seq, int samples, int sampleRate)
    {
        byte[] buf = new byte[samples * 2];
        int startSample = seq * samples;
        for (int i = 0; i < samples; i++)
        {
            double angle = 2.0 * Math.PI * SINE_FREQ_HZ * (startSample + i) / sampleRate;
            short v = (short) (SINE_AMPLITUDE * Math.sin(angle));
            buf[i * 2] = (byte) (v & 0xff);
            buf[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
        }
        return buf;
    }

    /** 合成句柄：AtomicBoolean + Future 双保险取消 */
    private static class MockSynthesis implements StreamSynthesis
    {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private volatile Future<?> future;

        void setFuture(Future<?> future)
        {
            this.future = future;
        }

        @Override
        public void cancel()
        {
            if (cancelled.compareAndSet(false, true))
            {
                Future<?> f = future;
                if (f != null)
                {
                    f.cancel(true);
                }
            }
        }

        @Override
        public boolean isCancelled()
        {
            return cancelled.get();
        }
    }
}
