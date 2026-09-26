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
}
