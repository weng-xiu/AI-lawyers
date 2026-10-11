package ai.lawyers.system.service.impl.lawyers.voice.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.service.lawyers.sms.ISmsService;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpExecutor.ExecutionResult;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;

/**
 * {@link SendSmsWriteOpExecutor} 测试：成功发送（号码取锚定会话）、
 * payload 缺 templateId / 非法 JSON、短信失败、服务未就绪。
 *
 * @author ai-lawyers
 */
class SendSmsWriteOpExecutorTest
{
    private static final String PHONE = "13800001234";

    private ISmsService smsService;

    private SendSmsWriteOpExecutor executor;

    @BeforeEach
    void setUp()
    {
        smsService = mock(ISmsService.class);
        executor = new SendSmsWriteOpExecutor();
        executor.setSmsService(smsService);
    }

    private WriteOpSession session(String payloadJson)
    {
        WriteOpSession s = new WriteOpSession("op-1", WriteOpAction.SEND_SMS, "sess-1", 9L,
                PHONE, payloadJson, System.currentTimeMillis());
        s.setState(ai.lawyers.system.service.lawyers.voice.otp.WriteOpState.CONFIRMED);
        return s;
    }

    @Test
    void 成功发送且号码取会话锚定值()
    {
        when(smsService.send(anyString(), anyLong(), any(), anyString(), anyLong()))
                .thenReturn(SmsResult.success("msg-1", "内容"));
        ExecutionResult r = executor.execute(session("{\"templateId\":12,\"params\":{\"code\":\"x\"}}"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getRef()).isEqualTo("msg-1");
        verify(smsService).send(eq(PHONE), eq(12L), any(), eq("sess-1"), eq(9L));
    }

    @Test
    void 缺少templateId拒绝()
    {
        ExecutionResult r = executor.execute(session("{\"params\":{}}"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("templateId");
    }

    @Test
    void 非法payload拒绝()
    {
        ExecutionResult r = executor.execute(session("{bad json"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("JSON");
    }

    @Test
    void 短信失败透传原因()
    {
        when(smsService.send(anyString(), anyLong(), any(), anyString(), anyLong()))
                .thenReturn(SmsResult.fail("通道不可用"));
        ExecutionResult r = executor.execute(session("{\"templateId\":12}"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("通道不可用");
    }

    @Test
    void 服务未就绪拒绝()
    {
        executor.setSmsService(null);
        ExecutionResult r = executor.execute(session("{\"templateId\":12}"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("未就绪");
    }
}
