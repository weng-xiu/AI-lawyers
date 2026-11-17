package ai.lawyers.system.domain.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 短信发送结果测试：成功/失败工厂与属性读写。
 *
 * @author ai-lawyers
 */
class SmsResultTest
{
    @Test
    void 成功工厂填充回执与内容()
    {
        SmsResult result = SmsResult.success("MSG-001", "【AI律师】您的验证码是 123456");

        assertTrue(result.isSuccess());
        assertEquals("MSG-001", result.getMsgId());
        assertEquals("【AI律师】您的验证码是 123456", result.getContent());
        assertEquals("发送成功", result.getMessage());
    }

    @Test
    void 失败工厂仅保留原因()
    {
        SmsResult result = SmsResult.fail("余额不足");

        assertFalse(result.isSuccess());
        assertEquals("余额不足", result.getMessage());
        assertNull(result.getMsgId());
        assertNull(result.getContent());
    }

    @Test
    void 属性读写()
    {
        SmsResult result = new SmsResult();
        result.setSuccess(true);
        result.setMessage("已提交");
        result.setMsgId("MSG-002");
        result.setContent("测试短信");

        assertTrue(result.isSuccess());
        assertEquals("已提交", result.getMessage());
        assertEquals("MSG-002", result.getMsgId());
        assertEquals("测试短信", result.getContent());
    }
}
