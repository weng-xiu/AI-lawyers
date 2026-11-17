package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 智能风险预警记录实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiRiskWarningTest
{
    @Test
    void 属性读写()
    {
        Date triggerTime = new Date();
        Date handleTime = new Date(triggerTime.getTime() + 120000L);
        AiRiskWarning warning = new AiRiskWarning();
        warning.setWarningId(1L);
        warning.setWarningType("敏感词");
        warning.setWarningLevel("1");
        warning.setSourceType("通话");
        warning.setSourceId(10L);
        warning.setRuleId(2L);
        warning.setSuggestTransferType("LEGAL_AID");
        warning.setTransferId(3L);
        warning.setRuleName("敏感词预警");
        warning.setRuleSuggestOrgId(4L);
        warning.setTicketNo("TK20261118001");
        warning.setContent("我活不下去了");
        warning.setCustomerName("王女士");
        warning.setStatus("2");
        warning.setHandlerName("李律师");
        warning.setHandlerId(8L);
        warning.setHandleResult("已安抚并转法律援助");
        warning.setHandleTime(handleTime);
        warning.setTriggerTime(triggerTime);

        assertEquals(1L, warning.getWarningId());
        assertEquals("敏感词", warning.getWarningType());
        assertEquals("1", warning.getWarningLevel());
        assertEquals("通话", warning.getSourceType());
        assertEquals(10L, warning.getSourceId());
        assertEquals(2L, warning.getRuleId());
        assertEquals("LEGAL_AID", warning.getSuggestTransferType());
        assertEquals(3L, warning.getTransferId());
        assertEquals("敏感词预警", warning.getRuleName());
        assertEquals(4L, warning.getRuleSuggestOrgId());
        assertEquals("TK20261118001", warning.getTicketNo());
        assertEquals("我活不下去了", warning.getContent());
        assertEquals("王女士", warning.getCustomerName());
        assertEquals("2", warning.getStatus());
        assertEquals("李律师", warning.getHandlerName());
        assertEquals(8L, warning.getHandlerId());
        assertEquals("已安抚并转法律援助", warning.getHandleResult());
        assertEquals(handleTime, warning.getHandleTime());
        assertEquals(triggerTime, warning.getTriggerTime());
    }

    @Test
    void 默认值为空()
    {
        AiRiskWarning warning = new AiRiskWarning();

        assertNull(warning.getWarningId());
        assertNull(warning.getWarningType());
        assertNull(warning.getWarningLevel());
        assertNull(warning.getSourceType());
        assertNull(warning.getSourceId());
        assertNull(warning.getRuleId());
        assertNull(warning.getSuggestTransferType());
        assertNull(warning.getTransferId());
        assertNull(warning.getContent());
        assertNull(warning.getCustomerName());
        assertNull(warning.getStatus());
        assertNull(warning.getHandlerName());
        assertNull(warning.getHandleResult());
        assertNull(warning.getTriggerTime());
    }

    @Test
    void 字符串输出包含预警类型与触发内容()
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setWarningType("合规风险");
        warning.setContent("要求承诺胜诉");

        String text = warning.toString();
        assertTrue(text.contains("合规风险"));
        assertTrue(text.contains("要求承诺胜诉"));
    }
}
