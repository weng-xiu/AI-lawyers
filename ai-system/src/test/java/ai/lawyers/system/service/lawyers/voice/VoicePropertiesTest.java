package ai.lawyers.system.service.lawyers.voice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 语音引擎配置测试：默认值、抽样率闭区间钳制、常用项读写。
 *
 * @author ai-lawyers
 */
class VoicePropertiesTest
{
    @Test
    void 默认配置值()
    {
        VoiceProperties props = new VoiceProperties();

        assertThat(props.getEngine()).isEqualTo("mock");
        assertThat(props.getDashscopeAsrModel()).isEqualTo("paraformer-realtime-v2");
        assertThat(props.getDashscopeTtsModel()).isEqualTo("cosyvoice-v1");
        assertThat(props.getDashscopeSampleRate()).isEqualTo(16000);
        assertThat(props.getAsrLanguage()).isEqualTo("zh");
        assertThat(props.getCantoneseAsrLanguage()).isEqualTo("yue");
        assertThat(props.isVadEnabled()).isTrue();
        assertThat(props.isVadShadowEnabled()).isFalse();
        assertThat(props.getVadShadowThreshold()).isEqualTo(0.5f);
        assertThat(props.getVadShadowSessionSampleRate()).isZero();
        assertThat(props.getVadShadowModelPath()).isEqualTo("models/silero_vad_int8.onnx");
    }

    @Test
    void 影子抽样率钳制在0到1之间()
    {
        VoiceProperties props = new VoiceProperties();

        props.setVadShadowSessionSampleRate(-0.5d);
        assertThat(props.getVadShadowSessionSampleRate()).isZero();

        props.setVadShadowSessionSampleRate(1.8d);
        assertThat(props.getVadShadowSessionSampleRate()).isEqualTo(1d);

        props.setVadShadowSessionSampleRate(0.35d);
        assertThat(props.getVadShadowSessionSampleRate()).isEqualTo(0.35d);
    }

    @Test
    void 常用配置项读写()
    {
        VoiceProperties props = new VoiceProperties();

        props.setEngine("dashscope");
        props.setDashscopeApiKey("key-1");
        props.setDashscopeVoice("longwan");
        props.setCantoneseVoice("yue-female");
        props.setVadEnabled(false);
        props.setVadShadowEnabled(true);

        assertThat(props.getEngine()).isEqualTo("dashscope");
        assertThat(props.getDashscopeApiKey()).isEqualTo("key-1");
        assertThat(props.getDashscopeVoice()).isEqualTo("longwan");
        assertThat(props.getCantoneseVoice()).isEqualTo("yue-female");
        assertThat(props.isVadEnabled()).isFalse();
        assertThat(props.isVadShadowEnabled()).isTrue();
    }
}
