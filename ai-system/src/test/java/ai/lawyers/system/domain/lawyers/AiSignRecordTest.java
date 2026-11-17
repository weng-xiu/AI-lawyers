package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 签署记录实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiSignRecordTest
{
    @Test
    void 属性读写()
    {
        Date signTime = new Date();
        AiSignRecord record = new AiSignRecord();
        record.setSignId(1L);
        record.setBusinessNo("BIZ20261118001");
        record.setSignTemplate("委托代理协议");
        record.setSignParty("王先生");
        record.setSignPartyPhone("13911112222");
        record.setSignStatus("2");
        record.setSignTime(signTime);
        record.setESignId("ESIGN-001");
        record.setCertificateNo("CERT-001");
        record.setSignFileUrl("https://example.com/sign/1.pdf");

        assertEquals(1L, record.getSignId());
        assertEquals("BIZ20261118001", record.getBusinessNo());
        assertEquals("委托代理协议", record.getSignTemplate());
        assertEquals("王先生", record.getSignParty());
        assertEquals("13911112222", record.getSignPartyPhone());
        assertEquals("2", record.getSignStatus());
        assertEquals(signTime, record.getSignTime());
        assertEquals("ESIGN-001", record.getESignId());
        assertEquals("CERT-001", record.getCertificateNo());
        assertEquals("https://example.com/sign/1.pdf", record.getSignFileUrl());
    }

    @Test
    void 默认值为空()
    {
        AiSignRecord record = new AiSignRecord();

        assertNull(record.getSignId());
        assertNull(record.getBusinessNo());
        assertNull(record.getSignTemplate());
        assertNull(record.getSignParty());
        assertNull(record.getSignPartyPhone());
        assertNull(record.getSignStatus());
        assertNull(record.getSignTime());
        assertNull(record.getESignId());
        assertNull(record.getCertificateNo());
        assertNull(record.getSignFileUrl());
    }

    @Test
    void 字符串输出包含业务流水号与签署方()
    {
        AiSignRecord record = new AiSignRecord();
        record.setBusinessNo("BIZ002");
        record.setSignParty("赵女士");

        String text = record.toString();
        assertTrue(text.contains("BIZ002"));
        assertTrue(text.contains("赵女士"));
    }
}
