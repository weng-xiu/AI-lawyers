package ai.lawyers.system.service.lawyers.voice.vad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.service.lawyers.voice.VoiceProperties;

/**
 * P2-12 方案A：Silero VAD 影子服务统计聚合单测（纯内存，无 Spring/DB）。
 *
 * @author ai-lawyers
 */
class SileroVadShadowServiceTest
{
    private static final int RATE = 16000;

    /** 构造一帧 20ms S16LE PCM：voiced=false 全零；voiced=true 低频方波（双 VAD 同判有声） */
    private static byte[] frame(boolean voiced)
    {
        int samples = RATE * 20 / 1000;
        byte[] pcm = new byte[samples * 2];
        if (voiced)
        {
            for (int i = 0; i < samples; i++)
            {
                short s = (short) (((i / 5) & 1) == 0 ? 5000 : -5000);
                pcm[i * 2] = (byte) (s & 0xFF);
                pcm[i * 2 + 1] = (byte) (s >> 8);
            }
        }
        return pcm;
    }

    private static SileroVadShadowService.VadPair newPair(SileroVadShadowService service,
            AtomicInteger legacyStarts)
    {
        return service.createShadowPair("sess-test", RATE, new VoiceActivityDetector.Listener()
        {
            @Override
            public void onSpeechStart()
            {
                legacyStarts.incrementAndGet();
            }

            @Override
            public void onSpeechEnd()
            {
            }
        });
    }

    @Test
    void feed_silentFrames_accumulatesBothSilent()
    {
        SileroVadShadowService service = new SileroVadShadowService(new VoiceProperties());
        AtomicInteger starts = new AtomicInteger();
        SileroVadShadowService.VadPair pair = newPair(service, starts);
        byte[] silence = frame(false);
        for (int i = 0; i < 10; i++)
        {
            pair.feed(silence);
        }

        SileroVadShadowService.DailyStats stats = service.snapshotDaily();
        assertEquals(10, stats.totalFrames);
        assertEquals(10, stats.bothSilentFrames);
        assertEquals(0, stats.bothVoiceFrames);
        assertEquals(1, stats.activeSessions);
        assertEquals(0, stats.legacyStartCount);

        pair.close();
        assertEquals(0, service.snapshotDaily().activeSessions);
    }

    @Test
    void feed_voicedFrames_passthroughLegacyStartAndComparesLatency()
    {
        SileroVadShadowService service = new SileroVadShadowService(new VoiceProperties());
        AtomicInteger legacyStarts = new AtomicInteger();
        SileroVadShadowService.VadPair pair = newPair(service, legacyStarts);
        byte[] voice = frame(true);
        for (int i = 0; i < 3; i++)
        {
            pair.feed(voice);
        }

        // 旧 VAD 监听透传：影子不改变主链路行为
        assertEquals(1, legacyStarts.get());
        SileroVadShadowService.DailyStats stats = service.snapshotDaily();
        assertEquals(1, stats.legacyStartCount);
        assertEquals(1, stats.sileroStartCount);
        assertTrue(stats.bothVoiceFrames >= 1);
        // 双方同时起始 → 延迟对比恰好一次（Mock 同判据，diff=0）
        assertEquals(1, stats.latencyDiffCount);
        assertEquals(0d, stats.avgLatencyDiffMs, 0.001d);

        pair.close();
    }

    @Test
    void close_removesSessionAndCountsEndedSessions()
    {
        SileroVadShadowService service = new SileroVadShadowService(new VoiceProperties());
        SileroVadShadowService.VadPair pair = newPair(service, new AtomicInteger());
        pair.feed(frame(false));
        pair.close();

        SileroVadShadowService.DailyStats stats = service.snapshotDaily();
        assertEquals(0, stats.activeSessions);
        assertEquals(1, stats.endedSessions);

        // 重复 close 幂等：会话已移除，不重复计数
        pair.close();
        assertEquals(1, service.snapshotDaily().endedSessions);
    }

    @Test
    void close_withoutFrames_doesNotCountEndedSession()
    {
        SileroVadShadowService service = new SileroVadShadowService(new VoiceProperties());
        SileroVadShadowService.VadPair pair = newPair(service, new AtomicInteger());
        pair.close();
        assertEquals(0, service.snapshotDaily().endedSessions);
    }

    @Test
    void dailyReport_disabledByDefault_doesNotReset()
    {
        VoiceProperties props = new VoiceProperties();
        SileroVadShadowService service = new SileroVadShadowService(props);
        SileroVadShadowService.VadPair pair = newPair(service, new AtomicInteger());
        pair.feed(frame(false));
        pair.close();

        service.dailyReport();
        assertEquals(1, service.snapshotDaily().totalFrames);
    }

    @Test
    void dailyReport_enabled_resetsCountersAndIsIdempotentSameDay()
    {
        VoiceProperties props = new VoiceProperties();
        props.setVadShadowEnabled(true);
        SileroVadShadowService service = new SileroVadShadowService(props);
        SileroVadShadowService.VadPair pair = newPair(service, new AtomicInteger());
        for (int i = 0; i < 5; i++)
        {
            pair.feed(frame(false));
        }
        pair.close();

        service.dailyReport();
        SileroVadShadowService.DailyStats after = service.snapshotDaily();
        assertEquals(0, after.totalFrames);
        assertEquals(0, after.endedSessions);

        // 再产生数据后同日重复调用应被幂等忽略（不重置，数据保留到次日）
        SileroVadShadowService.VadPair pair2 = newPair(service, new AtomicInteger());
        pair2.feed(frame(false));
        pair2.close();
        service.dailyReport();
        assertEquals(1, service.snapshotDaily().totalFrames);
    }

    @Test
    void dailyReport_emptyDay_noError()
    {
        VoiceProperties props = new VoiceProperties();
        props.setVadShadowEnabled(true);
        SileroVadShadowService service = new SileroVadShadowService(props);
        service.dailyReport();
        assertEquals(0, service.snapshotDaily().totalFrames);
    }

    @Test
    void snapshot_containsRatesAndCounters()
    {
        SileroVadShadowService service = new SileroVadShadowService(new VoiceProperties());
        SileroVadShadowService.VadPair pair = newPair(service, new AtomicInteger());
        pair.feed(frame(false));
        pair.close();

        var map = service.snapshot();
        assertEquals(1L, map.get("totalFrames"));
        assertEquals(1L, map.get("bothSilentFrames"));
        assertEquals(0.0d, (double) map.get("falseAlarmRate"), 0.0001d);
        assertEquals(0, map.get("activeSessions"));
        assertEquals(1L, map.get("endedSessions"));
    }
}
