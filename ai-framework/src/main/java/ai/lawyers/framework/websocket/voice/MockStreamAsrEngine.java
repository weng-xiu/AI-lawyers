package ai.lawyers.framework.websocket.voice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock 流式 ASR 引擎（P3-A1，无凭证/无外网联调用）。
 *
 * <p><b>显式 Mock，禁止静默假成功</b>：open 时打 WARN 声明当前为模拟引擎，
 * 产出文本明确带"【Mock】"前缀，不会被误认为真实识别结果。行为：</p>
 * <ul>
 *   <li>每收到一帧 PCM，延迟约 {@value #PARTIAL_DELAY_MS}ms 回一条 partial（累计帧数/字节数），
 *       模拟流式首包延迟；</li>
 *   <li>{@link #close()} 产出唯一一条 final（汇总帧数字节），随后关闭单线程工作池，
 *       保证 {@code @OnClose} 无线程泄漏；</li>
 *   <li>feed/回调异常不外抛，经 onError 下发 error 帧。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public class MockStreamAsrEngine implements StreamAsrEngine
{
    public static final String ENGINE_CODE = "mock";

    private static final Logger log = LoggerFactory.getLogger(MockStreamAsrEngine.class);

    /** 单帧 partial 模拟延迟（ms），用于前端联调看到"边说边出字"效果 */
    static final long PARTIAL_DELAY_MS = 50L;

    /** close 等待 final 排空的上限（ms） */
    private static final long CLOSE_WAIT_MS = 2000L;

    private final AtomicBoolean warned = new AtomicBoolean();

    private volatile VoiceAsrContext context;

    private volatile AsrStreamCallback callback;

    private ExecutorService worker;

    private final AtomicLong frameCount = new AtomicLong();

    private final AtomicLong byteCount = new AtomicLong();

    private final AtomicBoolean finalEmitted = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public String engineCode()
    {
        return ENGINE_CODE;
    }

    @Override
    public void open(VoiceAsrContext ctx, AsrStreamCallback cb)
    {
        if (ctx == null || cb == null)
        {
            throw new IllegalArgumentException("context/callback required");
        }
        this.context = ctx;
        this.callback = cb;
        final String threadName = "voice-mock-asr-" + ctx.getSessionId() + "-" + ctx.getRole();
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
        if (warned.compareAndSet(false, true))
        {
            log.warn("========== Mock 语音引擎 ========== 流式 ASR 使用 Mock 实现（{}），"
                    + "仅回显音频帧统计用于无凭证联调，不产生真实识别结果；真实 Provider 随 P3-A2 接入",
                    threadName);
        }
        else
        {
            log.warn("流式 ASR 使用 Mock 实现（{}），仅回显联调", threadName);
        }
    }

    @Override
    public void feed(byte[] frame)
    {
        if (closed.get() || worker == null || frame == null || frame.length == 0)
        {
            return;
        }
        final long frames = frameCount.incrementAndGet();
        final long bytes = byteCount.addAndGet(frame.length);
        final AsrStreamCallback cb = this.callback;
        worker.submit(() -> {
            try
            {
                Thread.sleep(PARTIAL_DELAY_MS);
                if (!closed.get())
                {
                    cb.onPartial("【Mock】已接收 " + frames + " 帧 / " + bytes + " 字节 PCM");
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            catch (Exception e)
            {
                safeError(cb, "ASR_MOCK_ERROR", e.getMessage());
            }
        });
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        final AsrStreamCallback cb = this.callback;
        final VoiceAsrContext ctx = this.context;
        final ExecutorService pool = this.worker;
        if (pool == null)
        {
            return;
        }
        // final 必须在工作线程内排在已入队的 partial 之后，保证顺序
        pool.submit(() -> emitFinal(cb, ctx));
        pool.shutdown();
        try
        {
            if (!pool.awaitTermination(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS))
            {
                log.warn("Mock ASR close 等待 final 排空超时 {}ms，强制关闭 session={}",
                        CLOSE_WAIT_MS, ctx == null ? "" : ctx.getSessionId());
                pool.shutdownNow();
            }
        }
        catch (InterruptedException e)
        {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void emitFinal(AsrStreamCallback cb, VoiceAsrContext ctx)
    {
        if (cb == null || !finalEmitted.compareAndSet(false, true))
        {
            return;
        }
        int rate = ctx == null ? 16000 : ctx.getSampleRate();
        cb.onFinal("【Mock 识别结果】共收到 " + frameCount.get() + " 帧 / " + byteCount.get()
                + " 字节 PCM（" + rate + "Hz S16LE 单声道），非真实识别内容，仅用于链路联调");
    }

    private void safeError(AsrStreamCallback cb, String code, String message)
    {
        try
        {
            if (cb != null)
            {
                cb.onError(code, message);
            }
        }
        catch (Exception ignore)
        {
            // 回调异常不再上抛
        }
    }
}
