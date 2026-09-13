package ai.lawyers.system.service.lawyers.voice;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;

/**
 * F1 语种路由：按来电档案 languagePreference 向 ASR/TTS 引擎 options 注入语种参数。
 *
 * <p>当前支持：</p>
 * <ul>
 *   <li>zh-CN 普通话（默认，走全局 voice/asr 配置）；</li>
 *   <li>yue-CN 粤语：TTS 注入 cantonese-voice/cantonese-tts-model，ASR 注入 yue 语种码。</li>
 * </ul>
 *
 * <p><b>Provider 桩说明：</b>粤语真实发音人/识别模型由 {@link VoiceProperties} 的 cantonese-* 配置注入，
 * 未配置时回退普通话引擎，IVR 主链路接入粤语供应商后本类无需改动（仅补配置）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class LanguageVoiceRouter
{
    /** 语种偏好：普通话 */
    public static final String LANG_ZH_CN = "zh-CN";

    /** 语种偏好：粤语 */
    public static final String LANG_YUE_CN = "yue-CN";

    private final VoiceProperties properties;

    public LanguageVoiceRouter(VoiceProperties properties)
    {
        this.properties = properties;
    }

    /**
     * 依据语种偏好补全 TTS options（调用方已显式指定的键不覆盖）。
     *
     * @param languagePreference 档案语种偏好（空/zh-CN 返回原 options）
     * @param options            既有 options（可为 null）
     * @return 补全后的 options
     */
    public Map<String, Object> applyTtsOptions(String languagePreference, Map<String, Object> options)
    {
        Map<String, Object> result = options == null ? new HashMap<>() : options;
        if (LANG_YUE_CN.equals(languagePreference))
        {
            if (StringUtils.isNotEmpty(properties.getCantoneseVoice()))
            {
                result.putIfAbsent("voice", properties.getCantoneseVoice());
            }
            if (StringUtils.isNotEmpty(properties.getCantoneseTtsModel()))
            {
                result.putIfAbsent("model", properties.getCantoneseTtsModel());
            }
        }
        return result;
    }

    /**
     * 依据语种偏好解析 ASR language 参数；粤语供应商未就绪时回退默认语种。
     */
    public String resolveAsrLanguage(String languagePreference)
    {
        if (LANG_YUE_CN.equals(languagePreference)
                && StringUtils.isNotEmpty(properties.getCantoneseAsrLanguage()))
        {
            return properties.getCantoneseAsrLanguage();
        }
        return properties.getAsrLanguage();
    }

    /**
     * 依据语种偏好补全 ASR options（language 键）。
     */
    public Map<String, Object> applyAsrOptions(String languagePreference, Map<String, Object> options)
    {
        Map<String, Object> result = options == null ? new HashMap<>() : options;
        result.putIfAbsent("language", resolveAsrLanguage(languagePreference));
        return result;
    }
}
