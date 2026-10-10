package ai.lawyers.system.service.lawyers.voice.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.service.impl.lawyers.voice.otp.OtpServiceImpl;
import ai.lawyers.system.service.lawyers.sms.ISmsService;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService.OtpIssueResult;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService.OtpVerifyStatus;

/**
 * {@link OtpServiceImpl} 测试：签发/校验闭环、重发间隔、TTL 过期、
 * 错码次数上限、一次性消费、短信通道失败撤销签发、本地模式（无短信服务）签发。
 *
 * @author ai-lawyers
 */
class OtpServiceImplTest
{
    private static final String PHONE = "13800001234";

    private ISmsService smsService;

    private OtpServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        smsService = mock(ISmsService.class);
        service = new OtpServiceImpl();
        inject("smsService", smsService);
        inject("otpTemplateId", "101");
        inject("ttlSeconds", 300);
        inject("resendIntervalSeconds", 60);
        inject("maxVerifyAttempts", 5);
        inject("nowOverride", -1L);
    }

    private void inject(String field, Object value) throws Exception
    {
        Field f = OtpServiceImpl.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    void issue_andVerify_roundtrip_ok()
    {
        when(smsService.send(eq(PHONE), eq(101L), any(), anyString(), any()))
                .thenReturn(SmsResult.success("MSG1", "您的核身码是123456"));

        OtpIssueResult r = service.issue(PHONE, "sess-1", 9L);

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getTtlSeconds()).isEqualTo(300);
        assertThat(r.getMsgId()).isEqualTo("MSG1");
        // 模板参数里的核身码为 6 位数字
        ArgumentCaptor<Map<String, String>> cap = ArgumentCaptor.forClass(Map.class);
        verify(smsService).send(eq(PHONE), eq(101L), cap.capture(), eq("sess-1"), eq(9L));
        assertThat(cap.getValue().get("code")).matches("\\d{6}");

        String code = cap.getValue().get("code");
        assertThat(service.verify(PHONE, code)).isEqualTo(OtpVerifyStatus.VERIFIED);
    }

    @Test
    void issue_smsFailure_returnsFail_andNothingIssued()
    {
        when(smsService.send(eq(PHONE), eq(101L), any(), any(), any()))
                .thenReturn(SmsResult.fail("通道限流"));

        OtpIssueResult r = service.issue(PHONE, "sess-1", null);

        assertThat(r.isSuccess()).isFalse();
        assertThat(service.verify(PHONE, "123456")).isEqualTo(OtpVerifyStatus.NOT_FOUND);
    }

    @Test
    void issue_noSmsService_localMode_stillIssuable() throws Exception
    {
        inject("smsService", null);

        OtpIssueResult r = service.issue(PHONE, "sess-1", null);

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getMsgId()).isNull();
    }

    @Test
    void issue_resendWithinInterval_rejected()
    {
        when(smsService.send(eq(PHONE), eq(101L), any(), any(), any()))
                .thenReturn(SmsResult.success("MSG1", "内容"));

        assertThat(service.issue(PHONE, "s", null).isSuccess()).isTrue();
        OtpIssueResult second = service.issue(PHONE, "s", null);

        assertThat(second.isSuccess()).isFalse();
        assertThat(second.getMessage()).contains("频繁");
    }

    @Test
    void verify_wrongCodeThenCorrect_ok()
    {
        when(smsService.send(eq(PHONE), eq(101L), any(), any(), any()))
                .thenReturn(SmsResult.success("MSG1", "内容"));
        service.issue(PHONE, "s", null);

        assertThat(service.verify(PHONE, "000000")).isEqualTo(OtpVerifyStatus.INVALID_CODE);

        ArgumentCaptor<Map<String, String>> cap = ArgumentCaptor.forClass(Map.class);
        verify(smsService).send(eq(PHONE), eq(101L), cap.capture(), any(), any());
        assertThat(service.verify(PHONE, cap.getValue().get("code")))
                .isEqualTo(OtpVerifyStatus.VERIFIED);
    }

    @Test
    void verify_afterMaxAttempts_invalidated()
    {
        when(smsService.send(eq(PHONE), eq(101L), any(), any(), any()))
                .thenReturn(SmsResult.success("MSG1", "内容"));
        service.issue(PHONE, "s", null);
        ArgumentCaptor<Map<String, String>> cap = ArgumentCaptor.forClass(Map.class);
        verify(smsService).send(eq(PHONE), eq(101L), cap.capture(), any(), any());
        String code = cap.getValue().get("code");

        // 前 4 次错码：可重试
        for (int i = 0; i < 4; i++)
        {
            assertThat(service.verify(PHONE, "000000")).isEqualTo(OtpVerifyStatus.INVALID_CODE);
        }
        // 第 5 次错码：达上限，条目作废
        assertThat(service.verify(PHONE, "000000")).isEqualTo(OtpVerifyStatus.TOO_MANY_ATTEMPTS);
        // 作废后即使回输正确码也失败
        assertThat(service.verify(PHONE, code)).isEqualTo(OtpVerifyStatus.NOT_FOUND);
    }

    @Test
    void verify_expired_afterTtl() throws Exception
    {
        when(smsService.send(eq(PHONE), eq(101L), any(), any(), any()))
                .thenReturn(SmsResult.success("MSG1", "内容"));
        service.issue(PHONE, "s", null);

        // 时钟推进越过 TTL
        inject("nowOverride", System.currentTimeMillis() + 301 * 1000L);

        assertThat(service.verify(PHONE, "123456")).isEqualTo(OtpVerifyStatus.EXPIRED);
    }

    @Test
    void verify_oneTime_secondAttemptNotFound()
    {
        when(smsService.send(eq(PHONE), eq(101L), any(), any(), any()))
                .thenReturn(SmsResult.success("MSG1", "内容"));
        service.issue(PHONE, "s", null);
        ArgumentCaptor<Map<String, String>> cap = ArgumentCaptor.forClass(Map.class);
        verify(smsService).send(eq(PHONE), eq(101L), cap.capture(), any(), any());

        assertThat(service.verify(PHONE, cap.getValue().get("code")))
                .isEqualTo(OtpVerifyStatus.VERIFIED);
        // 一次性：同一码不可复用
        assertThat(service.verify(PHONE, cap.getValue().get("code")))
                .isEqualTo(OtpVerifyStatus.NOT_FOUND);
    }

    @Test
    void issue_blankPhone_rejected()
    {
        assertThat(service.issue("  ", "s", null).isSuccess()).isFalse();
        assertThat(service.verify("", "123456")).isEqualTo(OtpVerifyStatus.NOT_FOUND);
    }
}
