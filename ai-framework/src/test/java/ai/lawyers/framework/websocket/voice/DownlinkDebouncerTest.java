package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * {@link DownlinkDebouncer} 测试：窗口钳制 100~200、满窗排空、未满窗 flush 补写、
 * discard 整窗丢弃、release 关闭 sink、异常分片丢弃不外抛、PCM 时长换算。
 *
 * @author ai-lawyers
 */
class DownlinkDebouncerTest
{
    /** 捕获写入的桩 sink */
    static class CapturingSink implements PbxDownlinkAudioSink
    {
        final List<byte[]> chunks = new ArrayList<>();

        final List<Integer> rates = new ArrayList<>();

        boolean failNext;

        /** N6：write 返回值桩（默认受理） */
        boolean accept = true;

        @Override
        public boolean write(byte[] pcm, int sampleRate)
        {
            if (failNext)
            {
                failNext = false;
                throw new IllegalStateException("sink boom");
            }
            chunks.add(pcm);
            rates.add(sampleRate);
            return accept;
        }
    }

    /** 16bit 单声道：1ms = 2*rate 字节 */
    private static byte[] msPcm(int ms, int rate)
    {
        return new byte[ms * 2 * rate / 1000];
    }

    @Test
    void 窗口钳制到100到200()
    {
        assertThat(new DownlinkDebouncer(new CapturingSink(), 30).getBufferMillis()).isEqualTo(100);
        assertThat(new DownlinkDebouncer(new CapturingSink(), 150).getBufferMillis()).isEqualTo(150);
        assertThat(new DownlinkDebouncer(new CapturingSink(), 999).getBufferMillis()).isEqualTo(200);
    }

    @Test
    void 未满窗不写入()
    {
        CapturingSink sink = new CapturingSink();
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 200);
        d.offer(msPcm(50, 16000), 16000);
        d.offer(msPcm(50, 16000), 16000);
        assertThat(sink.chunks).isEmpty();
    }

    @Test
    void 满窗即合片写入一次()
    {
        CapturingSink sink = new CapturingSink();
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 100);
        d.offer(msPcm(60, 16000), 16000);
        d.offer(msPcm(60, 16000), 16000);
        assertThat(sink.chunks).hasSize(1);
        assertThat(sink.chunks.get(0).length).isEqualTo(msPcm(120, 16000).length);
        assertThat(sink.rates.get(0)).isEqualTo(16000);
    }

    @Test
    void flush补写尾片()
    {
        CapturingSink sink = new CapturingSink();
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 200);
        d.offer(msPcm(80, 16000), 16000);
        d.flush();
        assertThat(sink.chunks).hasSize(1);
        // flush 后再 flush 为 no-op
        d.flush();
        assertThat(sink.chunks).hasSize(1);
    }

    @Test
    void discard丢弃整窗()
    {
        CapturingSink sink = new CapturingSink();
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 200);
        d.offer(msPcm(80, 16000), 16000);
        d.discard();
        d.flush();
        assertThat(sink.chunks).isEmpty();
    }

    @Test
    void sink异常不外抛分片丢弃()
    {
        CapturingSink sink = new CapturingSink();
        sink.failNext = true;
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 100);
        d.offer(msPcm(150, 16000), 16000);
        // 异常后缓冲已清空，后续可继续写入
        d.offer(msPcm(150, 16000), 16000);
        assertThat(sink.chunks).hasSize(1);
    }

    @Test
    void release清缓冲并关闭sink()
    {
        CapturingSink sink = new CapturingSink();
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 200);
        d.offer(msPcm(80, 16000), 16000);
        d.release();
        d.flush();
        assertThat(sink.chunks).isEmpty();
    }

    @Test
    void 空分片与非法采样率忽略()
    {
        CapturingSink sink = new CapturingSink();
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 100);
        d.offer(null, 16000);
        d.offer(new byte[0], 16000);
        d.offer(msPcm(50, 16000), 0);
        d.flush();
        assertThat(sink.chunks).isEmpty();
    }

    /* ================= N6（语雀 7.8-4）：下行帧 accepted/dropped 打点 ================= */

    @Test
    void n6受理帧计accepted()
    {
        PbxDownlinkAudioSink sink = mock(PbxDownlinkAudioSink.class);
        when(sink.write(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        HotlineMetrics metrics = mock(HotlineMetrics.class);
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 100, metrics);

        d.offer(msPcm(120, 16000), 16000);
        d.offer(msPcm(120, 16000), 16000);

        verify(metrics, times(2)).incrementVoiceDownlinkFrame(true);
        verify(metrics, never()).incrementVoiceDownlinkFrame(false);
    }

    @Test
    void n6拒绝帧计dropped()
    {
        PbxDownlinkAudioSink sink = mock(PbxDownlinkAudioSink.class);
        when(sink.write(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        HotlineMetrics metrics = mock(HotlineMetrics.class);
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 100, metrics);

        d.offer(msPcm(120, 16000), 16000);

        verify(metrics).incrementVoiceDownlinkFrame(false);
        verify(metrics, never()).incrementVoiceDownlinkFrame(true);
    }

    @Test
    void n6写异常计dropped且不外抛()
    {
        CapturingSink sink = new CapturingSink();
        sink.failNext = true;
        HotlineMetrics metrics = mock(HotlineMetrics.class);
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 100, metrics);

        d.offer(msPcm(150, 16000), 16000);

        verify(metrics).incrementVoiceDownlinkFrame(false);
    }

    @Test
    void n6metrics可空时行为不变()
    {
        CapturingSink sink = new CapturingSink();
        // 两参构造器（metrics 缺省）：无埋点路径，防抖行为保持
        DownlinkDebouncer d = new DownlinkDebouncer(sink, 100);
        d.offer(msPcm(120, 16000), 16000);
        d.flush();
        assertThat(sink.chunks).hasSize(1);
    }

    @Test
    void pcm纳秒换算正确()
    {
        // 16bit 单声道 16kHz：1ms = 32 字节 → 32000 字节 = 1000ms
        assertThat(DownlinkDebouncer.pcmNanos(32000, 16000)).isEqualTo(1_000_000_000L);
        // 8kHz：1ms = 16 字节
        assertThat(DownlinkDebouncer.pcmNanos(160, 8000)).isEqualTo(10_000_000L);
    }
}
