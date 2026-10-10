package ai.lawyers.system.service.lawyers.voice.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.lawyers.system.service.impl.lawyers.voice.otp.WriteOpConfirmServiceImpl;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService.OtpIssueResult;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService.OtpVerifyStatus;
import ai.lawyers.system.service.lawyers.voice.otp.IWriteOpConfirmService.BeginResult;
import ai.lawyers.system.service.lawyers.voice.otp.IWriteOpConfirmService.TransitionResult;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpState;

/**
 * {@link WriteOpConfirmServiceImpl} 测试：DRAFT→OTP_SENT→VERIFIED→
 * AWAITING_CONFIRM→CONFIRMED→EXECUTED 全链路、非法前置状态守卫、
 * 核身错码/达上限、取消、TTL 懒过期、同号活跃会话互斥、白名单外动作拒绝。
 *
 * @author ai-lawyers
 */
class WriteOpConfirmServiceImplTest
{
    private static final String PHONE = "13800001234";

    private IOtpService otpService;

    private WriteOpConfirmServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        otpService = mock(IOtpService.class);
        service = new WriteOpConfirmServiceImpl();
        inject("otpService", otpService);
        inject("sessionTtlSeconds", 600);
        inject("nowOverride", -1L);
    }

    private void inject(String field, Object value) throws Exception
    {
        Field f = WriteOpConfirmServiceImpl.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(service, value);
    }

    private BeginResult beginSendSms()
    {
        return service.begin(WriteOpAction.SEND_SMS, "sess-1", 9L, PHONE,
                "{\"template\":\"guide\",\"phone\":\"13900005678\"}");
    }

    /** 推进到核身通过状态（VERIFIED） */
    private String beginAndVerify()
    {
        BeginResult b = beginSendSms();
        when(otpService.issue(eq(PHONE), anyString(), anyLong()))
                .thenReturn(OtpIssueResult.ok(300, "MSG1"));
        when(otpService.verify(eq(PHONE), anyString())).thenReturn(OtpVerifyStatus.VERIFIED);
        service.requestOtp(b.getOpId());
        TransitionResult v = service.verifyIdentity(b.getOpId(), "123456");
        assertThat(v.isSuccess()).isTrue();
        return b.getOpId();
    }

    @Test
    void happyPath_draftToExecuted()
    {
        when(otpService.issue(eq(PHONE), anyString(), any()))
                .thenReturn(OtpIssueResult.ok(300, "MSG1"));
        when(otpService.verify(eq(PHONE), eq("123456"))).thenReturn(OtpVerifyStatus.VERIFIED);

        BeginResult b = beginSendSms();
        assertThat(b.isSuccess()).isTrue();
        assertThat(service.get(b.getOpId()).getState()).isEqualTo(WriteOpState.DRAFT);

        assertThat(service.requestOtp(b.getOpId()).getState()).isEqualTo(WriteOpState.OTP_SENT);
        assertThat(service.verifyIdentity(b.getOpId(), "123456").getState())
                .isEqualTo(WriteOpState.VERIFIED);
        assertThat(service.armConfirm(b.getOpId(), "将为您代发短信，确认请按1，取消请按2").getState())
                .isEqualTo(WriteOpState.AWAITING_CONFIRM);
        assertThat(service.confirm(b.getOpId()).getState()).isEqualTo(WriteOpState.CONFIRMED);
        TransitionResult done = service.complete(b.getOpId());

        assertThat(done.isSuccess()).isTrue();
        assertThat(done.getState()).isEqualTo(WriteOpState.EXECUTED);
        // 终态收口：会话即焚
        assertThat(service.get(b.getOpId())).isNull();
    }

    @Test
    void verifyIdentity_wrongCode_staysOtpSent_andCountsAttempts()
    {
        when(otpService.issue(eq(PHONE), anyString(), any()))
                .thenReturn(OtpIssueResult.ok(300, "MSG1"));
        when(otpService.verify(eq(PHONE), eq("000000"))).thenReturn(OtpVerifyStatus.INVALID_CODE);

        BeginResult b = beginSendSms();
        service.requestOtp(b.getOpId());
        TransitionResult v = service.verifyIdentity(b.getOpId(), "000000");

        assertThat(v.isSuccess()).isFalse();
        assertThat(v.getState()).isEqualTo(WriteOpState.OTP_SENT);
        WriteOpSession s = service.get(b.getOpId());
        assertThat(s.getState()).isEqualTo(WriteOpState.OTP_SENT);
        assertThat(s.getIdentityAttempts()).isEqualTo(1);
    }

    @Test
    void verifyIdentity_tooManyAttempts_rejectsSession()
    {
        when(otpService.issue(eq(PHONE), anyString(), any()))
                .thenReturn(OtpIssueResult.ok(300, "MSG1"));
        when(otpService.verify(eq(PHONE), anyString()))
                .thenReturn(OtpVerifyStatus.TOO_MANY_ATTEMPTS);

        BeginResult b = beginSendSms();
        service.requestOtp(b.getOpId());
        TransitionResult v = service.verifyIdentity(b.getOpId(), "000000");

        assertThat(v.isSuccess()).isFalse();
        assertThat(v.getState()).isEqualTo(WriteOpState.REJECTED);
        assertThat(service.get(b.getOpId())).isNull();
    }

    @Test
    void confirm_beforeArmConfirm_rejected()
    {
        String opId = beginAndVerify();

        TransitionResult c = service.confirm(opId);

        assertThat(c.isSuccess()).isFalse();
        assertThat(c.getState()).isEqualTo(WriteOpState.VERIFIED);
    }

    @Test
    void armConfirm_blankSummary_rejected()
    {
        String opId = beginAndVerify();

        TransitionResult r = service.armConfirm(opId, "   ");

        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getState()).isEqualTo(WriteOpState.VERIFIED);
    }

    @Test
    void requestOtp_otpFailure_staysDraft_retryable()
    {
        when(otpService.issue(eq(PHONE), anyString(), any()))
                .thenReturn(OtpIssueResult.fail("发送过于频繁，请稍后再试"));

        BeginResult b = beginSendSms();
        TransitionResult r = service.requestOtp(b.getOpId());

        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getState()).isEqualTo(WriteOpState.DRAFT);
        // OTP 恢复后可重试
        when(otpService.issue(eq(PHONE), anyString(), any()))
                .thenReturn(OtpIssueResult.ok(300, "MSG2"));
        when(otpService.verify(eq(PHONE), anyString())).thenReturn(OtpVerifyStatus.VERIFIED);
        assertThat(service.requestOtp(b.getOpId()).getState()).isEqualTo(WriteOpState.OTP_SENT);
        assertThat(service.verifyIdentity(b.getOpId(), "123456").isSuccess()).isTrue();
    }

    @Test
    void reject_terminatesSession()
    {
        when(otpService.issue(eq(PHONE), anyString(), any()))
                .thenReturn(OtpIssueResult.ok(300, "MSG1"));
        BeginResult b = beginSendSms();
        service.requestOtp(b.getOpId());

        TransitionResult r = service.reject(b.getOpId());

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getState()).isEqualTo(WriteOpState.REJECTED);
        assertThat(service.get(b.getOpId())).isNull();
    }

    @Test
    void reject_onTerminalState_fails()
    {
        BeginResult b = beginSendSms();
        service.reject(b.getOpId());

        // 终态即焚：opId 失效，返回"会话不存在"（EXPIRED 语义）
        TransitionResult r = service.reject(b.getOpId());

        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getState()).isEqualTo(WriteOpState.EXPIRED);
    }

    @Test
    void sessionExpired_lazyCheck()
    {
        BeginResult b = beginSendSms();

        // 时钟推进越过会话 TTL
        try
        {
            inject("nowOverride", System.currentTimeMillis() + 601 * 1000L);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        WriteOpSession s = service.get(b.getOpId());
        assertThat(s.getState()).isEqualTo(WriteOpState.EXPIRED);
        // 过期后所有转换均拒绝
        assertThat(service.requestOtp(b.getOpId()).isSuccess()).isFalse();
    }

    @Test
    void begin_duplicateActivePhone_rejected_thenAllowedAfterReject()
    {
        when(otpService.issue(eq(PHONE), anyString(), any()))
                .thenReturn(OtpIssueResult.ok(300, "MSG1"));
        BeginResult first = beginSendSms();

        BeginResult second = beginSendSms();
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.getMessage()).contains("进行中的确认会话");

        // 取消第一个后同号可重新发起
        service.reject(first.getOpId());
        assertThat(beginSendSms().isSuccess()).isTrue();
    }

    @Test
    void begin_outOfWhitelistOrBlankPhone_rejected()
    {
        assertThat(service.begin(null, "s", 1L, PHONE, "{}").isSuccess()).isFalse();
        assertThat(service.begin(WriteOpAction.BOOK_CALLBACK, "s", 1L, "  ", "{}").isSuccess())
                .isFalse();
    }

    @Test
    void begin_unknownOpId_operationsFail()
    {
        assertThat(service.requestOtp("no-such-op").isSuccess()).isFalse();
        assertThat(service.verifyIdentity("no-such-op", "123456").isSuccess()).isFalse();
        assertThat(service.armConfirm("no-such-op", "摘要").isSuccess()).isFalse();
        assertThat(service.confirm("no-such-op").isSuccess()).isFalse();
        assertThat(service.complete("no-such-op").isSuccess()).isFalse();
        assertThat(service.get("no-such-op")).isNull();
    }
}
