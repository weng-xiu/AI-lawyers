package ai.lawyers.system.service.lawyers.compliance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * N4 AI 生成内容标识配置（《人工智能生成合成内容标识办法》2025-09-01 施行）。
 *
 * <p>总开关默认关闭：政务内网模型/备案就绪后由环境变量显式开启，
 * 关闭时 {@link AiContentLabelService} 全方法原样透传、零行为差异。</p>
 *
 * @author ai-lawyers
 */
@Component
@ConfigurationProperties(prefix = "ai.compliance.label")
public class AiContentLabelProperties
{
    /** 标识总开关（显式/隐式标识统一受控） */
    private boolean enabled = false;

    /** 文本显式标识：是否在 AI 回答首尾追加标识 */
    private boolean textLabelEnabled = true;

    /** 文本显式标识前缀 */
    private String textPrefix = "【AI生成】";

    /** 文本显式标识后缀（留空则不追加） */
    private String textSuffix = "（以上内容由人工智能生成，仅供参考）";

    /** 语音显式标识：是否在 TTS 播报首句前插入语音提示 */
    private boolean voiceLabelEnabled = true;

    /** 语音提示语（随正文一起送 TTS，满足音频开头语音提示要求） */
    private String voicePrefix = "以下内容由人工智能生成，仅供参考。";

    /** 隐式标识：服务端元数据标签（写入数据字段/响应体，供前端角标与留痕） */
    private boolean metadataEnabled = true;

    /** 隐式标识标签值 */
    private String metadataTag = "AI_GENERATED";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isTextLabelEnabled()
    {
        return textLabelEnabled;
    }

    public void setTextLabelEnabled(boolean textLabelEnabled)
    {
        this.textLabelEnabled = textLabelEnabled;
    }

    public String getTextPrefix()
    {
        return textPrefix;
    }

    public void setTextPrefix(String textPrefix)
    {
        this.textPrefix = textPrefix;
    }

    public String getTextSuffix()
    {
        return textSuffix;
    }

    public void setTextSuffix(String textSuffix)
    {
        this.textSuffix = textSuffix;
    }

    public boolean isVoiceLabelEnabled()
    {
        return voiceLabelEnabled;
    }

    public void setVoiceLabelEnabled(boolean voiceLabelEnabled)
    {
        this.voiceLabelEnabled = voiceLabelEnabled;
    }

    public String getVoicePrefix()
    {
        return voicePrefix;
    }

    public void setVoicePrefix(String voicePrefix)
    {
        this.voicePrefix = voicePrefix;
    }

    public boolean isMetadataEnabled()
    {
        return metadataEnabled;
    }

    public void setMetadataEnabled(boolean metadataEnabled)
    {
        this.metadataEnabled = metadataEnabled;
    }

    public String getMetadataTag()
    {
        return metadataTag;
    }

    public void setMetadataTag(String metadataTag)
    {
        this.metadataTag = metadataTag;
    }
}
