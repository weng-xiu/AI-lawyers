package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * #1：{@link NoopPbxDownlinkAudioSink} 下行丢弃语义测试（P5 点名缺测）。
 *
 * <p>空实现必须始终返回 true（上游防抖合片照常推进时序）、容忍 null 分片，
 * 并仅在每 200 次写入记一条低频日志（索引 1/201/401... 命中日志分支）。</p>
 *
 * @author ai-lawyers
 */
class NoopPbxDownlinkAudioSinkTest
{
    @Test
    void write_alwaysAcceptsAndCounts()
    {
        NoopPbxDownlinkAudioSink sink = new NoopPbxDownlinkAudioSink();

        assertThat(sink.write(new byte[] {1, 2, 3}, 16000)).isTrue();
        assertThat(sink.write(null, 8000)).isTrue();

        assertThat(ReflectionTestUtils.getField(sink, "writes")).isEqualTo(2L);
    }

    @Test
    void write_repeatedIncludingLogBoundary_neverThrows()
    {
        NoopPbxDownlinkAudioSink sink = new NoopPbxDownlinkAudioSink();
        byte[] pcm = new byte[320];

        // 覆盖首次写入与第 201 次写入两条低频日志分支
        for (int i = 0; i < 201; i++)
        {
            assertThat(sink.write(pcm, 16000)).isTrue();
        }

        assertThat(ReflectionTestUtils.getField(sink, "writes")).isEqualTo(201L);
    }
}
