package ai.lawyers.system.service.lawyers.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * {@link HotlineMetrics} A5 语音链路埋点测试：
 * 指标按文档命名（hotline_voice_*_ms）、超预算不抛异常（WARN 日志治理）、
 * 未装配 MeterRegistry 时全部静默跳过。
 *
 * @author ai-lawyers
 */
class HotlineMetricsTest
{
    /** 反射注入 MeterRegistry（字段为 @Autowired 私有，无 setter） */
    private static HotlineMetrics withRegistry(SimpleMeterRegistry registry) throws Exception
    {
        HotlineMetrics m = new HotlineMetrics();
        Field f = HotlineMetrics.class.getDeclaredField("registry");
        f.setAccessible(true);
        f.set(m, registry);
        return m;
    }

    @Test
    void voiceTimers_recordWithDocumentedNames() throws Exception
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotlineMetrics m = withRegistry(registry);

        m.recordVoiceVadMs(60, "s1");
        m.recordVoiceAsrFirstMs(250, "s1");
        m.recordVoiceTtsFirstMs(180, "s1");
        m.recordVoiceE2eFirstMs(1100, "s1");
        m.recordVoiceBargeinStopMs(120, "vad", "s1");
        m.recordVoiceBargeinStopMs(90, "bargein", "s1");
        // E3：机器人 RAG/LLM 与回合 E2E（source=robot）
        m.recordVoiceRagMs(30, "s1");
        m.recordVoiceLlmFirstTokenMs(500, "s1");
        m.recordVoiceE2eFirstMs(900, "robot", "s1");

        // SimpleMeterRegistry 保留点分名（hotline_voice.vad.ms）；
        // Prometheus 导出时统一转下划线（hotline_voice_vad_ms），与文档指标名一致
        Timer vad = registry.find("hotline_voice.vad.ms").timer();
        Timer asr = registry.find("hotline_voice.asr.first.ms").timer();
        Timer tts = registry.find("hotline_voice.tts.first.ms").timer();
        // e2e 现有 source=start/robot 两个 tag 实例，须带 tag 精确查找
        Timer e2e = registry.find("hotline_voice.e2e.first.ms").tag("source", "start").timer();
        assertThat(vad).isNotNull();
        assertThat(asr).isNotNull();
        assertThat(tts).isNotNull();
        assertThat(e2e).isNotNull();
        assertThat(vad.count()).isEqualTo(1);
        assertThat(vad.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(60.0);
        assertThat(asr.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250.0);
        assertThat(tts.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(180.0);
        assertThat(e2e.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1100.0);
        // bargein 按 trigger 分 tag
        assertThat(registry.find("hotline_voice.bargein.stop.ms").tag("trigger", "vad").timer().count()).isEqualTo(1);
        assertThat(registry.find("hotline_voice.bargein.stop.ms").tag("trigger", "bargein").timer().count()).isEqualTo(1);
        // E3 指标名与 e2e source tag
        assertThat(registry.find("hotline_voice.rag.ms").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(30.0);
        assertThat(registry.find("hotline_voice.llm.first.token.ms").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(500.0);
        assertThat(registry.find("hotline_voice.e2e.first.ms").tag("source", "start").timer().count()).isEqualTo(1);
        assertThat(registry.find("hotline_voice.e2e.first.ms").tag("source", "robot").timer().totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(900.0);
    }

    @Test
    void overBudget_stillRecords_andDoesNotThrow() throws Exception
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotlineMetrics m = withRegistry(registry);

        // 全维度超预算：仍记录且只产生 WARN（日志不断言，治理语义）
        assertThatCode(() -> {
            m.recordVoiceVadMs(HotlineMetrics.BUDGET_VOICE_VAD_MS + 1, "s2");
            m.recordVoiceAsrFirstMs(HotlineMetrics.BUDGET_VOICE_ASR_FIRST_MS + 1, "s2");
            m.recordVoiceTtsFirstMs(HotlineMetrics.BUDGET_VOICE_TTS_FIRST_MS + 1, "s2");
            m.recordVoiceE2eFirstMs(HotlineMetrics.BUDGET_VOICE_E2E_FIRST_MS + 1, "s2");
            m.recordVoiceBargeinStopMs(HotlineMetrics.BUDGET_VOICE_BARGEIN_STOP_MS + 1, "vad", "s2");
            m.recordVoiceRagMs(HotlineMetrics.BUDGET_VOICE_RAG_MS + 1, "s2");
            m.recordVoiceLlmFirstTokenMs(HotlineMetrics.BUDGET_VOICE_LLM_FIRST_TOKEN_MS + 1, "s2");
        }).doesNotThrowAnyException();

        assertThat(registry.find("hotline_voice.vad.ms").timer().count()).isEqualTo(1);
        assertThat(registry.find("hotline_voice.e2e.first.ms").timer().count()).isEqualTo(1);
        assertThat(registry.find("hotline_voice.rag.ms").timer().count()).isEqualTo(1);
        assertThat(registry.find("hotline_voice.llm.first.token.ms").timer().count()).isEqualTo(1);
    }

    @Test
    void noRegistry_allNoop()
    {
        HotlineMetrics m = new HotlineMetrics();
        assertThatCode(() -> {
            m.recordVoiceVadMs(10, "s3");
            m.recordVoiceAsrFirstMs(10, "s3");
            m.recordVoiceTtsFirstMs(10, "s3");
            m.recordVoiceE2eFirstMs(10, "s3");
            m.recordVoiceBargeinStopMs(10, "vad", "s3");
            m.recordVoiceRagMs(10, "s3");
            m.recordVoiceLlmFirstTokenMs(10, "s3");
            m.recordVoiceE2eFirstMs(10, "robot", "s3");
        }).doesNotThrowAnyException();
    }

    @Test
    void pbxFork_metricsWithDocumentedNames() throws Exception
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotlineMetrics m = withRegistry(registry);

        m.incrementPbxConnect();
        m.incrementPbxReject("auth_fail");
        m.incrementPbxReject("disabled");
        m.incrementPbxClose("stop");
        m.incrementPbxClose("abnormal");
        m.recordPbxSession(1200);
        m.incrementPbxBytes(640);
        m.incrementPbxAudioFrame("binary");
        m.incrementPbxAudioFrame("json");
        m.incrementPbxBadFrame("bad_json");
        m.incrementPbxBadFrame("bad_base64");

        // SimpleMeterRegistry 保留点分逻辑名；Prometheus 导出转下划线，Timer 再追加 _seconds
        assertThat(registry.find("hotline_pbx.fork.connect.total").counter().count()).isEqualTo(1);
        assertThat(registry.find("hotline_pbx.fork.reject.total")
                .tag("reason", "auth_fail").counter().count()).isEqualTo(1);
        assertThat(registry.find("hotline_pbx.fork.reject.total")
                .tag("reason", "disabled").counter().count()).isEqualTo(1);
        assertThat(registry.find("hotline_pbx.fork.close.total")
                .tag("reason", "stop").counter().count()).isEqualTo(1);
        assertThat(registry.find("hotline_pbx.fork.close.total")
                .tag("reason", "abnormal").counter().count()).isEqualTo(1);
        // 生命周期 Timer：1200ms = 1.2s，导出名 hotline_pbx_fork_session_seconds
        Timer session = registry.find("hotline_pbx.fork.session").timer();
        assertThat(session.count()).isEqualTo(1);
        assertThat(session.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1200.0);
        assertThat(registry.find("hotline_pbx.fork.bytes.total").counter().count())
                .isEqualTo(640.0);
        assertThat(registry.find("hotline_pbx.fork.audio.frame.total")
                .tag("kind", "binary").counter().count()).isEqualTo(1);
        assertThat(registry.find("hotline_pbx.fork.audio.frame.total")
                .tag("kind", "json").counter().count()).isEqualTo(1);
        assertThat(registry.find("hotline_pbx.fork.bad.frame.total")
                .tag("reason", "bad_json").counter().count()).isEqualTo(1);
        assertThat(registry.find("hotline_pbx.fork.bad.frame.total")
                .tag("reason", "bad_base64").counter().count()).isEqualTo(1);

        // 两个 gauge：名称即 Prometheus 导出名（gauge 不追加后缀）
        Object holder = new Object();
        m.gaugePbxActive(holder, o -> 3);
        m.gaugePbxSilence(holder, o -> 1.5);
        assertThat(registry.find("hotline_pbx_fork_active").gauge().value()).isEqualTo(3);
        assertThat(registry.find("hotline_pbx_fork_audio_silence_seconds").gauge().value())
                .isEqualTo(1.5);
    }

    @Test
    void pbxFork_noRegistry_allNoop()
    {
        HotlineMetrics m = new HotlineMetrics();
        Object holder = new Object();
        assertThatCode(() -> {
            m.incrementPbxConnect();
            m.incrementPbxReject("auth_fail");
            m.incrementPbxClose("abnormal");
            m.recordPbxSession(10);
            m.incrementPbxBytes(10);
            m.incrementPbxAudioFrame("binary");
            m.incrementPbxBadFrame("bad_json");
            m.gaugePbxActive(holder, o -> 1);
            m.gaugePbxSilence(holder, o -> 1);
        }).doesNotThrowAnyException();
    }
}
