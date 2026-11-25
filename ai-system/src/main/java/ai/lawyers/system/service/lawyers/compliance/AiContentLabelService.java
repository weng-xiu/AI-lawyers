package ai.lawyers.system.service.lawyers.compliance;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * N4 AI 生成内容标识统一出口（《人工智能生成合成内容标识办法》合规）。
 *
 * <p>所有 AI 合成内容（公众端文本回答、机器人 TTS 播报）在离开服务端前必须经本类处理：</p>
 * <ul>
 *   <li>{@link #applyTextLabel(String)}：文本通道显式标识，首尾追加可配置提示语；</li>
 *   <li>{@link #applyVoicePrefix(String)}：语音通道显式标识，首句前插入语音提示，随正文送 TTS；</li>
 *   <li>{@link #metadataTag()}：隐式标识，供响应体字段/落库留痕，前端可据此渲染角标。</li>
 * </ul>
 *
 * <p>总开关关闭（默认）时全部方法原样透传，保证未开启环境零行为差异；
 * 本类无外部依赖、不抛异常，可安全在兜底话术路径调用。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiContentLabelService
{
    private final AiContentLabelProperties properties;

    public AiContentLabelService(AiContentLabelProperties properties)
    {
        this.properties = properties;
    }

    /**
     * 文本通道显式标识：在 AI 回答首尾追加标识。
     *
     * @param content 原始 AI 回答（可为空，空值原样返回）
     * @return 追加标识后的文本；开关关闭时原样返回
     */
    public String applyTextLabel(String content)
    {
        if (!shouldLabelText() || !StringUtils.hasText(content))
        {
            return content;
        }
        StringBuilder sb = new StringBuilder(content.length() + 64);
        if (StringUtils.hasText(properties.getTextPrefix()))
        {
            sb.append(properties.getTextPrefix());
        }
        sb.append(content);
        if (StringUtils.hasText(properties.getTextSuffix()))
        {
            sb.append(properties.getTextSuffix());
        }
        return sb.toString();
    }

    /**
     * 语音通道显式标识：在 TTS 播报文本前插入提示语（随正文一次合成，保证音频开头可闻）。
     *
     * @param text 原始播报文本（可为空，空值原样返回）
     * @return 带语音提示的播报文本；开关关闭时原样返回
     */
    public String applyVoicePrefix(String text)
    {
        if (!shouldLabelVoice() || !StringUtils.hasText(text))
        {
            return text;
        }
        // 已带前缀的文本不重复追加（兜底重试/重复合成场景幂等）
        if (text.startsWith(properties.getVoicePrefix()))
        {
            return text;
        }
        return properties.getVoicePrefix() + text;
    }

    /**
     * 隐式标识标签：开关开启时返回配置标签值，否则返回 {@code null}（调用方据此决定是否写字段）。
     *
     * @return 隐式标识标签值；未启用返回 null
     */
    public String metadataTag()
    {
        if (!properties.isEnabled() || !properties.isMetadataEnabled())
        {
            return null;
        }
        return properties.getMetadataTag();
    }

    /** 总开关 + 文本显式标识子开关 */
    private boolean shouldLabelText()
    {
        return properties.isEnabled() && properties.isTextLabelEnabled();
    }

    /** 总开关 + 语音显式标识子开关 */
    private boolean shouldLabelVoice()
    {
        return properties.isEnabled() && properties.isVoiceLabelEnabled();
    }
}
