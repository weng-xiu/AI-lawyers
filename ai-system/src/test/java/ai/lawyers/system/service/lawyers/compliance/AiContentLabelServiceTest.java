package ai.lawyers.system.service.lawyers.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * N4 {@link AiContentLabelService} 单元测试：
 * 总开关关闭透传、文本首尾标识、语音前缀幂等、隐式标签、空值安全。
 *
 * @author ai-lawyers
 */
class AiContentLabelServiceTest
{
    private AiContentLabelService serviceWith(AiContentLabelProperties props)
    {
        return new AiContentLabelService(props);
    }

    private AiContentLabelProperties enabledProps()
    {
        AiContentLabelProperties p = new AiContentLabelProperties();
        p.setEnabled(true);
        return p;
    }

    @Test
    void disabled_byDefault_allMethodsPassThrough()
    {
        AiContentLabelService svc = serviceWith(new AiContentLabelProperties());
        assertThat(svc.applyTextLabel("原始回答")).isEqualTo("原始回答");
        assertThat(svc.applyVoicePrefix("播报文本")).isEqualTo("播报文本");
        assertThat(svc.metadataTag()).isNull();
    }

    @Test
    void enabled_textLabel_wrapsWithPrefixAndSuffix()
    {
        AiContentLabelService svc = serviceWith(enabledProps());
        String out = svc.applyTextLabel("可以申请劳动仲裁");
        assertThat(out).startsWith("【AI生成】").contains("可以申请劳动仲裁")
                .endsWith("（以上内容由人工智能生成，仅供参考）");
    }

    @Test
    void enabled_voicePrefix_prependedOnceAndIdempotent()
    {
        AiContentLabelService svc = serviceWith(enabledProps());
        String once = svc.applyVoicePrefix("您可以主张经济补偿。");
        assertThat(once).startsWith("以下内容由人工智能生成，仅供参考。");
        // 重复调用不重复追加（兜底重试/重复合成幂等）
        assertThat(svc.applyVoicePrefix(once)).isEqualTo(once);
    }

    @Test
    void enabled_metadataTag_returnsConfiguredTag()
    {
        AiContentLabelService svc = serviceWith(enabledProps());
        assertThat(svc.metadataTag()).isEqualTo("AI_GENERATED");
    }

    @Test
    void enabled_metadataDisabled_tagIsNull()
    {
        AiContentLabelProperties p = enabledProps();
        p.setMetadataEnabled(false);
        AiContentLabelService svc = serviceWith(p);
        assertThat(svc.metadataTag()).isNull();
    }

    @Test
    void enabled_textSubSwitchOff_passesThrough()
    {
        AiContentLabelProperties p = enabledProps();
        p.setTextLabelEnabled(false);
        AiContentLabelService svc = serviceWith(p);
        assertThat(svc.applyTextLabel("原文")).isEqualTo("原文");
        // 语音子开关不受影响
        assertThat(svc.applyVoicePrefix("播报")).startsWith("以下内容由人工智能生成");
    }

    @Test
    void enabled_voiceSubSwitchOff_passesThrough()
    {
        AiContentLabelProperties p = enabledProps();
        p.setVoiceLabelEnabled(false);
        AiContentLabelService svc = serviceWith(p);
        assertThat(svc.applyVoicePrefix("播报")).isEqualTo("播报");
    }

    @Test
    void blankContent_returnedAsIsEvenWhenEnabled()
    {
        AiContentLabelService svc = serviceWith(enabledProps());
        assertThat(svc.applyTextLabel(null)).isNull();
        assertThat(svc.applyTextLabel("")).isEqualTo("");
        assertThat(svc.applyVoicePrefix(null)).isNull();
        assertThat(svc.applyVoicePrefix("  ")).isEqualTo("  ");
    }

    @Test
    void customSuffixBlank_onlyPrefixApplied()
    {
        AiContentLabelProperties p = enabledProps();
        p.setTextSuffix("");
        AiContentLabelService svc = serviceWith(p);
        assertThat(svc.applyTextLabel("答复")).isEqualTo("【AI生成】答复");
    }
}
