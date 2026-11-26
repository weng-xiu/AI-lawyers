package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.service.lawyers.voice.VoiceProperties;

/**
 * {@link VoiceEngineRegistry} 引擎选择单测（P3-A2/A3）。
 *
 * @author ai-lawyers
 */
class VoiceEngineRegistryTest
{
    @Test
    void createAsr_mockAlwaysAvailable()
    {
        VoiceEngineRegistry reg = new VoiceEngineRegistry();
        assertThat(reg.createAsr("mock")).isInstanceOf(MockStreamAsrEngine.class);
        // 空编码默认 mock
        assertThat(reg.createAsr(null)).isInstanceOf(MockStreamAsrEngine.class);
        assertThat(reg.createAsr("")).isInstanceOf(MockStreamAsrEngine.class);
    }

    @Test
    void createAsr_dashscopeNullWithoutProperties()
    {
        VoiceEngineRegistry reg = new VoiceEngineRegistry();
        assertThat(reg.createAsr("dashscope")).isNull();
    }

    @Test
    void createAsr_dashscopeNullWithoutApiKey()
    {
        VoiceEngineRegistry reg = new VoiceEngineRegistry(new VoiceProperties());
        assertThat(reg.createAsr("dashscope")).isNull();
    }

    @Test
    void createAsr_dashscopeAvailableWithApiKey()
    {
        VoiceProperties props = new VoiceProperties();
        props.setDashscopeApiKey("sk-test");
        VoiceEngineRegistry reg = new VoiceEngineRegistry(props);
        assertThat(reg.createAsr("dashscope")).isInstanceOf(DashScopeStreamAsrEngine.class);
    }

    // ---------- N1②：paraformer-realtime-v2 流式 ASR 凭证与接线 ----------

    @Test
    void createAsr_dashscopeCaseInsensitive()
    {
        VoiceProperties props = new VoiceProperties();
        props.setDashscopeApiKey("sk-test");
        VoiceEngineRegistry reg = new VoiceEngineRegistry(props);

        assertThat(reg.createAsr("DASHSCOPE")).isInstanceOf(DashScopeStreamAsrEngine.class);
        assertThat(reg.createAsr("DashScope")).isInstanceOf(DashScopeStreamAsrEngine.class);
    }

    @Test
    void asrModel_defaultsToParaformerRealtimeV2_andOverridable()
    {
        // 锁定语雀 7.3 N1② 指定的实时 ASR 模型默认值
        VoiceProperties defaults = new VoiceProperties();
        assertThat(defaults.getDashscopeAsrModel()).isEqualTo("paraformer-realtime-v2");

        // 模型名可经配置覆盖，引擎按 properties 透传到 run-task
        VoiceProperties overridden = new VoiceProperties();
        overridden.setDashscopeAsrModel("paraformer-realtime-v2-8k");
        assertThat(overridden.getDashscopeAsrModel()).isEqualTo("paraformer-realtime-v2-8k");
    }

    @Test
    void createAsr_unknownReturnsNull()
    {
        VoiceEngineRegistry reg = new VoiceEngineRegistry();
        assertThat(reg.createAsr("whisper")).isNull();
    }

    @Test
    void createAsr_eachCallReturnsNewInstance()
    {
        VoiceEngineRegistry reg = new VoiceEngineRegistry();
        assertThat(reg.createAsr("mock")).isNotSameAs(reg.createAsr("mock"));
    }

    @Test
    void createTts_mockSingletonAndDashscopeNullWithoutKey()
    {
        VoiceEngineRegistry reg = new VoiceEngineRegistry();
        assertThat(reg.createTts("mock")).isSameAs(MockStreamTtsEngine.INSTANCE);
        assertThat(reg.createTts("dashscope")).isNull();
        assertThat(reg.createTts("unknown")).isNull();
    }

    @Test
    void createTts_dashscopeAvailableWithApiKey()
    {
        VoiceProperties props = new VoiceProperties();
        props.setDashscopeApiKey("sk-test");
        VoiceEngineRegistry reg = new VoiceEngineRegistry(props);
        assertThat(reg.createTts("dashscope")).isInstanceOf(DashScopeStreamTtsEngine.class);
    }

    // ---------- N1③：内网 CosyVoice 2 流式 TTS ----------

    @Test
    void createTts_cosyvoice2_withBaseUrl_returnsEngine()
    {
        VoiceProperties props = new VoiceProperties();
        props.setCosyvoice2TtsBaseUrl("http://10.0.0.8:9880/v1");
        VoiceEngineRegistry reg = new VoiceEngineRegistry(props);

        StreamTtsEngine tts = reg.createTts("cosyvoice2");
        assertThat(tts).isInstanceOf(CosyVoice2StreamTtsEngine.class);
        assertThat(tts.engineCode()).isEqualTo("cosyvoice2");
    }

    @Test
    void createTts_cosyvoice2_caseInsensitive()
    {
        VoiceProperties props = new VoiceProperties();
        props.setCosyvoice2TtsBaseUrl("http://10.0.0.8:9880/v1");
        VoiceEngineRegistry reg = new VoiceEngineRegistry(props);

        assertThat(reg.createTts("COSYVOICE2")).isInstanceOf(CosyVoice2StreamTtsEngine.class);
    }

    @Test
    void createTts_cosyvoice2_withoutBaseUrl_returnsNull()
    {
        VoiceEngineRegistry reg = new VoiceEngineRegistry(new VoiceProperties());

        assertThat(reg.createTts("cosyvoice2")).isNull();
    }
}
