package ai.lawyers.system.domain.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 短信发送记录实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiSmsLogTest
{
    @Test
    void 属性读写()
    {
        AiSmsLog log = new AiSmsLog();
        log.setLogId(1L);
        log.setPhone("13800000000");
        log.setTemplateId(2L);
        log.setConfigId(3L);
        log.setParamsJson("{\"name\":\"张三\"}");
        log.setContent("尊敬的张三，您的咨询已受理");
        log.setSendStatus("1");
        log.setProviderMsgId("msg-001");
        log.setFailReason("无");
        log.setSessionId("sess-001");
        log.setRecordId(4L);
        log.setTemplateName("咨询受理通知");

        assertEquals(1L, log.getLogId());
        assertEquals("13800000000", log.getPhone());
        assertEquals(2L, log.getTemplateId());
        assertEquals(3L, log.getConfigId());
        assertEquals("{\"name\":\"张三\"}", log.getParamsJson());
        assertEquals("尊敬的张三，您的咨询已受理", log.getContent());
        assertEquals("1", log.getSendStatus());
        assertEquals("msg-001", log.getProviderMsgId());
        assertEquals("无", log.getFailReason());
        assertEquals("sess-001", log.getSessionId());
        assertEquals(4L, log.getRecordId());
        assertEquals("咨询受理通知", log.getTemplateName());
    }

    @Test
    void 默认值为空()
    {
        AiSmsLog log = new AiSmsLog();

        assertNull(log.getLogId());
        assertNull(log.getPhone());
        assertNull(log.getSendStatus());
        assertNull(log.getProviderMsgId());
        assertNull(log.getRecordId());
        assertNull(log.getTemplateName());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiSmsLog log = new AiSmsLog();
        log.setLogId(1L);
        log.setPhone("13800000000");
        log.setSendStatus("1");

        String text = log.toString();

        assertTrue(text.contains("13800000000"));
        assertTrue(text.contains("sendStatus"));
        assertTrue(text.contains("logId"));
    }
}
