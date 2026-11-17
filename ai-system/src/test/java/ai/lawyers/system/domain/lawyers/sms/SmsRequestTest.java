package ai.lawyers.system.domain.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 短信发送请求测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class SmsRequestTest
{
    @Test
    void 属性读写()
    {
        SmsRequest request = new SmsRequest();
        request.setPhone("13800000000");
        request.setSignName("AI律师");
        request.setTemplateCode("SMS_100001");
        request.setContent("尊敬的张三，您的咨询已受理");

        assertEquals("13800000000", request.getPhone());
        assertEquals("AI律师", request.getSignName());
        assertEquals("SMS_100001", request.getTemplateCode());
        assertEquals("尊敬的张三，您的咨询已受理", request.getContent());
    }

    @Test
    void 默认值为空()
    {
        SmsRequest request = new SmsRequest();

        assertNull(request.getPhone());
        assertNull(request.getSignName());
        assertNull(request.getTemplateCode());
        assertNull(request.getContent());
    }
}
