package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 来电登记台账实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallLedgerTest
{
    @Test
    void 属性读写()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerId(1L);
        ledger.setLedgerNo("LG20261118001");
        ledger.setRecordId(2L);
        ledger.setTicketId(3L);
        ledger.setCallerName("孙七");
        ledger.setCallerPhone("13700000000");
        ledger.setCallerIdCard("110101198501010000");
        ledger.setCallerGender("1");
        ledger.setCallerAge(40);
        ledger.setConsultContent("房屋租赁押金纠纷");

        assertEquals(1L, ledger.getLedgerId());
        assertEquals("LG20261118001", ledger.getLedgerNo());
        assertEquals(2L, ledger.getRecordId());
        assertEquals(3L, ledger.getTicketId());
        assertEquals("孙七", ledger.getCallerName());
        assertEquals("13700000000", ledger.getCallerPhone());
        assertEquals("110101198501010000", ledger.getCallerIdCard());
        assertEquals("1", ledger.getCallerGender());
        assertEquals(40, ledger.getCallerAge());
        assertEquals("房屋租赁押金纠纷", ledger.getConsultContent());
    }

    @Test
    void 服务与回访字段读写()
    {
        Date visitTime = new Date();
        BigDecimal amount = new BigDecimal("12000.50");
        AiCallLedger ledger = new AiCallLedger();
        ledger.setCategoryId(5L);
        ledger.setCategoryName("民事纠纷");
        ledger.setSubCategory("租赁合同");
        ledger.setServiceType("1");
        ledger.setSourceChannel("12348热线");
        ledger.setLawyerId(6L);
        ledger.setLawyerName("周律师");
        ledger.setInvolveAmount(amount);
        ledger.setLawyerAnswer("可主张返还押金");
        ledger.setConsultDuration(30);
        ledger.setSatisfaction("1");
        ledger.setIsVisit("1");
        ledger.setVisitTime(visitTime);
        ledger.setVisitBy("回访员");
        ledger.setVisitOpinion("客户满意");
        ledger.setDelFlag("0");

        assertEquals(5L, ledger.getCategoryId());
        assertEquals("民事纠纷", ledger.getCategoryName());
        assertEquals("租赁合同", ledger.getSubCategory());
        assertEquals("1", ledger.getServiceType());
        assertEquals("12348热线", ledger.getSourceChannel());
        assertEquals(6L, ledger.getLawyerId());
        assertEquals("周律师", ledger.getLawyerName());
        assertEquals(amount, ledger.getInvolveAmount());
        assertEquals("可主张返还押金", ledger.getLawyerAnswer());
        assertEquals(30, ledger.getConsultDuration());
        assertEquals("1", ledger.getSatisfaction());
        assertEquals("1", ledger.getIsVisit());
        assertEquals(visitTime, ledger.getVisitTime());
        assertEquals("回访员", ledger.getVisitBy());
        assertEquals("客户满意", ledger.getVisitOpinion());
        assertEquals("0", ledger.getDelFlag());
    }

    @Test
    void 默认值为空()
    {
        AiCallLedger ledger = new AiCallLedger();

        assertNull(ledger.getLedgerId());
        assertNull(ledger.getLedgerNo());
        assertNull(ledger.getCallerName());
        assertNull(ledger.getInvolveAmount());
        assertNull(ledger.getSatisfaction());
        assertNull(ledger.getDelFlag());
        assertNull(ledger.getTokenGroups());
    }

    @Test
    void 字符串输出包含台账号与律师姓名()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerNo("LG20261118002");
        ledger.setLawyerName("吴律师");

        String text = ledger.toString();
        assertTrue(text.contains("LG20261118002"));
        assertTrue(text.contains("吴律师"));
    }
}
