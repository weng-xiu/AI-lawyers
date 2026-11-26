package ai.lawyers.framework.websocket.voice;

import java.io.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * #1：下行防抖缓冲（100~200ms 参数化）。
 *
 * <p>行业实践：机器人下行播放需按 100~200ms 窗口合片防抖（避免逐 TTS 分片
 * 直写 PBX 造成抖动）。offer 累积 PCM 至窗口阈值后一次性写入 sink；
 * 正常合成结束 {@link #flush} 补写尾片，打断/取消 {@link #discard} 整窗丢弃
 * （与浏览器侧 drainPendingTts 对齐）。</p>
 *
 * <p>窗口时长钳制到 [100, 200]ms（调研报告 6.3-A 参数化区间）；非线程安全场景
 * 已加 synchronized：单一 TTS 合成回调线程调用，锁无竞争。</p>
 *
 * @author ai-lawyers
 */
public class DownlinkDebouncer
{
    private static final Logger log = LoggerFactory.getLogger(DownlinkDebouncer.class);

    /** 默认防抖窗口（毫秒） */
    public static final int DEFAULT_BUFFER_MS = 150;

    private static final int MIN_BUFFER_MS = 100;

    private static final int MAX_BUFFER_MS = 200;

    private final PbxDownlinkAudioSink sink;

    private final int bufferMillis;

    /** N6：指标门面（null 时丢帧埋点静默跳过，不影响防抖行为） */
    private final HotlineMetrics metrics;

    /** N1④：会话标识，预算超支 WARN 时定位 */
    private final String sessionId;

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream(8192);

    /** 已累积时长（纳秒） */
    private long bufferedNanos;

    /** N1④：当前批次首片进入的墙钟时间（纳秒），空窗时为 0 */
    private long batchStartNanos;

    /** 上次写入 sink 的采样率（flush 用） */
    private int lastRate;

    public DownlinkDebouncer(PbxDownlinkAudioSink sink, int bufferMillis)
    {
        this(sink, bufferMillis, null, null);
    }

    public DownlinkDebouncer(PbxDownlinkAudioSink sink, int bufferMillis, HotlineMetrics metrics)
    {
        this(sink, bufferMillis, metrics, null);
    }

    public DownlinkDebouncer(PbxDownlinkAudioSink sink, int bufferMillis, HotlineMetrics metrics, String sessionId)
    {
        this.sink = sink;
        this.metrics = metrics;
        this.sessionId = sessionId;
        int b = bufferMillis;
        if (b < MIN_BUFFER_MS)
        {
            b = MIN_BUFFER_MS;
        }
        if (b > MAX_BUFFER_MS)
        {
            b = MAX_BUFFER_MS;
        }
        this.bufferMillis = b;
    }

    /** 实际生效的防抖窗口（毫秒，已钳制） */
    public int getBufferMillis()
    {
        return bufferMillis;
    }

    /** 累积一段 PCM；达到窗口阈值即合片写入 sink */
    public synchronized void offer(byte[] pcm, int sampleRate)
    {
        if (pcm == null || pcm.length == 0 || sampleRate <= 0)
        {
            return;
        }
        this.lastRate = sampleRate;
        if (buf.size() == 0)
        {
            // N1④：新批次首片进入，记录墙钟起点（空窗后第一片）
            batchStartNanos = System.nanoTime();
        }
        buf.write(pcm, 0, pcm.length);
        bufferedNanos += pcmNanos(pcm.length, sampleRate);
        if (bufferedNanos >= millisToNanos(bufferMillis))
        {
            drain();
        }
    }

    /** 补写不足一个窗口的尾片（正常合成结束时调用；空窗为 no-op） */
    public synchronized void flush()
    {
        drain();
    }

    /** 整窗丢弃（打断/取消/异常时调用，与浏览器侧清播放队列对齐） */
    public synchronized void discard()
    {
        if (buf.size() > 0)
        {
            log.debug("[PbxDownlink] 丢弃防抖缓冲 bytes={} ms≈{}", buf.size(),
                    nanosToMs(bufferedNanos));
        }
        buf.reset();
        bufferedNanos = 0;
        batchStartNanos = 0;
    }

    /** 会话释放（幂等：清残余缓冲，不重复 close） */
    public synchronized void release()
    {
        buf.reset();
        bufferedNanos = 0;
        batchStartNanos = 0;
        try
        {
            sink.close();
        }
        catch (Exception e)
        {
            log.debug("[PbxDownlink] sink close 异常: {}", e.getMessage());
        }
    }

    private void drain()
    {
        int size = buf.size();
        if (size == 0)
        {
            return;
        }
        byte[] out = buf.toByteArray();
        buf.reset();
        bufferedNanos = 0;
        // N1④：批次缓冲墙钟耗时（首片→写出），验证 7.3.2 第⑤段 100~200ms 合片预算
        long bufferWallMs = nanosToMs(System.nanoTime() - batchStartNanos);
        batchStartNanos = 0;
        // N6：sink 拒绝（false）或写异常均计 dropped，供 Grafana 算下行丢帧率
        boolean accepted = false;
        try
        {
            accepted = sink.write(out, lastRate);
        }
        catch (Exception e)
        {
            log.warn("[PbxDownlink] sink 写入异常（分片丢弃）: {}", e.getMessage());
        }
        if (metrics != null)
        {
            metrics.incrementVoiceDownlinkFrame(accepted);
            metrics.recordVoiceDownlinkBufferMs(bufferWallMs, sessionId);
        }
    }

    /** 16bit 单声道 PCM 字节数 → 纳秒时长 */
    static long pcmNanos(int bytes, int sampleRate)
    {
        long frames = bytes / 2L;
        return frames * 1_000_000_000L / sampleRate;
    }

    private static long millisToNanos(int ms)
    {
        return ms * 1_000_000L;
    }

    private static long nanosToMs(long nanos)
    {
        return nanos / 1_000_000L;
    }
}
