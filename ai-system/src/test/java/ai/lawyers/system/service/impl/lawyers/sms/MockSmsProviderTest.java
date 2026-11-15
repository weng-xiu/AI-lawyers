package ai.lawyers.system.service.impl.lawyers.sms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.sms.SmsRequest;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;

/**
 * 本地模拟短信供应商语义契约：路由码固定 mock，发送恒成功并回执 MOCK 前缀消息ID，
 * 内容原样回显，空入参不抛错——与真实供应商"成功/失败 + 回执ID + 内容"契约一致。
 *
 * @author ai-lawyers
 */
class MockSmsProviderTest
{
    private final MockSmsProvider provider = new MockSmsProvider();

    private static SmsRequest request(String phone, String content)
    {
        SmsRequest r = new SmsRequest();
        r.setPhone(phone);
        r.setSignName("12348热线");
        r.setTemplateCode("SMS_001");
        r.setContent(content);
        return r;
    }

    @Test
    void code_isMock()
    {
        assertThat(provider.code()).isEqualTo("mock");
    }

    @Test
    void send_alwaysSuccessWithMockMsgIdAndEchoContent()
    {
        SmsResult result = provider.send(request("13800000000", "您的工单已受理"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMsgId()).startsWith("MOCK");
        assertThat(result.getContent()).isEqualTo("您的工单已受理");
    }

    @Test
    void send_nullFields_tolerated()
    {
        SmsResult result = provider.send(request(null, null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMsgId()).startsWith("MOCK");
        assertThat(result.getContent()).isNull();
    }
}
