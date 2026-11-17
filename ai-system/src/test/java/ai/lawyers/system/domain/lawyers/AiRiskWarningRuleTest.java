package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 智能风险预警规则实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiRiskWarningRuleTest
{
    @Test
    void 属性读写()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        rule.setRuleId(1L);
        rule.setRuleName("敏感词预警");
        rule.setRuleType("敏感词");
        rule.setRuleLevel("1");
        rule.setKeywords("自杀,轻生");
        rule.setSuggestTransferType("LEGAL_AID");
        rule.setSuggestOrgId(6L);
        rule.setIsEnabled("1");

        assertEquals(1L, rule.getRuleId());
        assertEquals("敏感词预警", rule.getRuleName());
        assertEquals("敏感词", rule.getRuleType());
        assertEquals("1", rule.getRuleLevel());
        assertEquals("自杀,轻生", rule.getKeywords());
        assertEquals("LEGAL_AID", rule.getSuggestTransferType());
        assertEquals(6L, rule.getSuggestOrgId());
        assertEquals("1", rule.getIsEnabled());
    }

    @Test
    void 默认值为空()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();

        assertNull(rule.getRuleId());
        assertNull(rule.getRuleName());
        assertNull(rule.getRuleType());
        assertNull(rule.getRuleLevel());
        assertNull(rule.getKeywords());
        assertNull(rule.getSuggestTransferType());
        assertNull(rule.getSuggestOrgId());
        assertNull(rule.getIsEnabled());
    }

    @Test
    void 字符串输出包含规则名称与关键词()
    {
        AiRiskWarningRule rule = new AiRiskWarningRule();
        rule.setRuleName("情绪异常预警");
        rule.setKeywords("威胁,投诉");

        String text = rule.toString();
        assertTrue(text.contains("情绪异常预警"));
        assertTrue(text.contains("威胁,投诉"));
    }
}
