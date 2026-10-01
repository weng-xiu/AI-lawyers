package ai.lawyers.system.service.impl.lawyers.quality;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;

/**
 * P1-7（V2.59）：质检规则引擎（零模型成本、结果确定性可复核）。
 *
 * <p>当前规则：① 高敏违禁/违规话术（承诺胜诉/包赢/私自收费/风险代理等，词表可经
 * {@code ai.quality.banned-words} 覆盖）；② 服务规范用语（问候/自报身份/结束语）。</p>
 *
 * <p>未启用项：长静默、抢话、语速等声学/时间轴规则——需 ASR 输出带时间戳的
 * 逐词结果，当前转写为纯文本，待语音侧补齐后在此扩展（不改动评分融合契约）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class QualityRuleEngine
{
    /** 高敏违禁词默认表（逗号分隔，可被配置整体覆盖） */
    static final String DEFAULT_BANNED_WORDS =
            "承诺胜诉,包赢,保证打赢,官司包在我身上,私自收费,私下收费,风险代理,私了费,拿钱办事";

    /** 问候语任一命中即可 */
    private static final String[] GREETING_WORDS = {"您好", "你好"};

    /** 自报身份任一命中即可 */
    private static final String[] IDENTITY_WORDS = {"12348", "公共法律服务", "法律援助中心"};

    /** 结束语任一命中即可 */
    private static final String[] CLOSING_WORDS = {"再见", "感谢您的来电", "祝您生活愉快", "感谢来电"};

    @Value("${ai.quality.banned-words:" + DEFAULT_BANNED_WORDS + "}")
    private String bannedWordsConfig;

    /**
     * 规则引擎结果。
     */
    public static class RuleResult
    {
        /** 命中的违禁词（去重，供风险联动） */
        private final List<String> bannedHits;
        /** 违规明细（供 violationJson 合并），每项已含 keyword/reason */
        private final List<RuleViolation> violations;
        /** 合规话术维度建议分（0-100） */
        private final double complianceScore;
        /** 服务规范维度建议分（0-100） */
        private final double serviceNormScore;

        RuleResult(List<String> bannedHits, List<RuleViolation> violations,
                   double complianceScore, double serviceNormScore)
        {
            this.bannedHits = bannedHits;
            this.violations = violations;
            this.complianceScore = complianceScore;
            this.serviceNormScore = serviceNormScore;
        }

        public List<String> getBannedHits() { return bannedHits; }
        public List<RuleViolation> getViolations() { return violations; }
        public double getComplianceScore() { return complianceScore; }
        public double serviceNormScore() { return serviceNormScore; }
    }

    /** 单条规则违规 */
    public static class RuleViolation
    {
        private final String keyword;
        private final String reason;

        public RuleViolation(String keyword, String reason)
        {
            this.keyword = keyword;
            this.reason = reason;
        }

        public String getKeyword() { return keyword; }
        public String getReason() { return reason; }
    }

    /**
     * 对转写文本执行规则检查。
     */
    public RuleResult evaluate(String transcript)
    {
        String text = transcript == null ? "" : transcript;
        List<String> bannedHits = new ArrayList<>();
        List<RuleViolation> violations = new ArrayList<>();

        // ① 违禁词
        for (String word : parseBannedWords())
        {
            if (text.contains(word))
            {
                bannedHits.add(word);
                violations.add(new RuleViolation(word, "规则引擎命中高敏违规话术：" + word));
            }
        }
        // ② 服务规范
        int serviceNorm = 100;
        if (!containsAny(text, GREETING_WORDS))
        {
            serviceNorm -= 20;
            violations.add(new RuleViolation("问候缺失", "通话开头未检测到问候语（您好）"));
        }
        if (!containsAny(text, IDENTITY_WORDS))
        {
            serviceNorm -= 30;
            violations.add(new RuleViolation("身份缺失", "未检测到坐席自报身份（12348/公共法律服务）"));
        }
        if (!containsAny(text, CLOSING_WORDS))
        {
            serviceNorm -= 20;
            violations.add(new RuleViolation("结束语缺失", "未检测到规范结束语"));
        }

        // 合规分：100 起，每个违禁词 -25，下限 0
        int compliance = Math.max(0, 100 - bannedHits.size() * 25);
        return new RuleResult(bannedHits, violations, compliance, Math.max(0, serviceNorm));
    }

    /** 解析违禁词配置（去空白/去重/保序） */
    private List<String> parseBannedWords()
    {
        Set<String> words = new LinkedHashSet<>();
        String config = StringUtils.isNotEmpty(bannedWordsConfig) ? bannedWordsConfig : DEFAULT_BANNED_WORDS;
        for (String item : Arrays.asList(config.split(",")))
        {
            String w = item.trim();
            if (!w.isEmpty())
            {
                words.add(w);
            }
        }
        return new ArrayList<>(words);
    }

    private boolean containsAny(String text, String[] candidates)
    {
        for (String c : candidates)
        {
            if (text.contains(c))
            {
                return true;
            }
        }
        return false;
    }
}
