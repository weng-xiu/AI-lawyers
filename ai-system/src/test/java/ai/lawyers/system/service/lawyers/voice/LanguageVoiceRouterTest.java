package ai.lawyers.system.service.lawyers.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * LanguageVoiceRouter 单测：普通话直通、粤语 TTS/ASR 参数注入、
 * 显式选项不覆盖与粤语供应商未配置时回退。
 *
 * @author ai-lawyers
 */
class LanguageVoiceRouterTest
{
    private VoiceProperties properties;
    private LanguageVoiceRouter router;

    @BeforeEach
    void setUp()
    {
        properties = new VoiceProperties();
        properties.setAsrLanguage("zh");
        properties.setCantoneseVoice("cantonese-voice");
        properties.setCantoneseTtsModel("cantonese-tts");
        properties.setCantoneseAsrLanguage("yue");
        router = new LanguageVoiceRouter(properties);
    }

    @Test
    void zhKeepsOptionsUnchanged()
    {
        Map<String, Object> options = new HashMap<>();

        Map<String, Object> result = router.applyTtsOptions(LanguageVoiceRouter.LANG_ZH_CN, options);

        assertThat(result).isEmpty();
    }

    @Test
    void nullOptionsBecomesEmptyMap()
    {
        Map<String, Object> result = router.applyTtsOptions(LanguageVoiceRouter.LANG_ZH_CN, null);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void yueInjectsCantoneseVoiceAndModel()
    {
        Map<String, Object> result = router.applyTtsOptions(LanguageVoiceRouter.LANG_YUE_CN, null);

        assertThat(result).containsEntry("voice", "cantonese-voice")
                .containsEntry("model", "cantonese-tts");
    }

    @Test
    void explicitVoiceNotOverridden()
    {
        Map<String, Object> options = new HashMap<>();
        options.put("voice", "custom");

        Map<String, Object> result = router.applyTtsOptions(LanguageVoiceRouter.LANG_YUE_CN, options);

        assertThat(result).containsEntry("voice", "custom")
                .containsEntry("model", "cantonese-tts");
    }

    @Test
    void yueWithoutCantoneseConfigFallsBackToNothing()
    {
        properties.setCantoneseVoice("");
        properties.setCantoneseTtsModel("");

        Map<String, Object> result = router.applyTtsOptions(LanguageVoiceRouter.LANG_YUE_CN, null);

        assertThat(result).isEmpty();
    }

    @Test
    void resolveAsrLanguageForYue()
    {
        assertThat(router.resolveAsrLanguage(LanguageVoiceRouter.LANG_YUE_CN)).isEqualTo("yue");
    }

    @Test
    void resolveAsrLanguageFallsBackWhenYueNotConfigured()
    {
        properties.setCantoneseAsrLanguage("");

        assertThat(router.resolveAsrLanguage(LanguageVoiceRouter.LANG_YUE_CN)).isEqualTo("zh");
    }

    @Test
    void resolveAsrLanguageForZh()
    {
        assertThat(router.resolveAsrLanguage(LanguageVoiceRouter.LANG_ZH_CN)).isEqualTo("zh");
        assertThat(router.resolveAsrLanguage(null)).isEqualTo("zh");
    }

    @Test
    void applyAsrOptionsInjectsLanguage()
    {
        Map<String, Object> result = router.applyAsrOptions(LanguageVoiceRouter.LANG_YUE_CN, null);

        assertThat(result).containsEntry("language", "yue");
    }

    @Test
    void explicitAsrLanguageNotOverridden()
    {
        Map<String, Object> options = new HashMap<>();
        options.put("language", "en");

        Map<String, Object> result = router.applyAsrOptions(LanguageVoiceRouter.LANG_YUE_CN, options);

        assertThat(result).containsEntry("language", "en");
    }
}
