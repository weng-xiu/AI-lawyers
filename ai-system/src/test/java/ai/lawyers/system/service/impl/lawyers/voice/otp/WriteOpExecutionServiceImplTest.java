package ai.lawyers.system.service.impl.lawyers.voice.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.lawyers.system.service.impl.lawyers.voice.otp.WriteOpExecutorTestSupport.StubExecutor;
import ai.lawyers.system.service.lawyers.voice.otp.IWriteOpConfirmService;
import ai.lawyers.system.service.lawyers.voice.otp.IWriteOpConfirmService.TransitionResult;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpExecutor.ExecutionResult;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpState;

/**
 * {@link WriteOpExecutionServiceImpl} 测试：状态守卫（仅 CONFIRMED）、
 * 无执行器、执行成功回执 complete（→EXECUTED）、失败保持 CONFIRMED 可重试、
 * 回执失败、执行器异常隔离。
 *
 * @author ai-lawyers
 */
class WriteOpExecutionServiceImplTest
{
    private static final String OP = "op-1";

    private IWriteOpConfirmService confirmService;

    private WriteOpExecutionServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        confirmService = mock(IWriteOpConfirmService.class);
        service = new WriteOpExecutionServiceImpl();
        service.setExecutors(Collections.emptyList());
        Field f = WriteOpExecutionServiceImpl.class.getDeclaredField("confirmService");
        f.setAccessible(true);
        f.set(service, confirmService);
    }

    private WriteOpSession session(WriteOpState state)
    {
        WriteOpSession s = new WriteOpSession(OP, WriteOpAction.SEND_SMS, "sess-1", 9L,
                "13800001234", "{\"templateId\":1}", System.currentTimeMillis());
        s.setState(state);
        return s;
    }

    @Test
    void 会话不存在返回失败()
    {
        when(confirmService.get(OP)).thenReturn(null);
        ExecutionResult r = service.executeConfirmed(OP);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("会话不存在");
    }

    @Test
    void 非CONFIRMED状态拒绝执行()
    {
        when(confirmService.get(OP)).thenReturn(session(WriteOpState.AWAITING_CONFIRM));
        ExecutionResult r = service.executeConfirmed(OP);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("CONFIRMED");
        verify(confirmService, never()).complete(anyString());
    }

    @Test
    void 无执行器返回失败且不回执()
    {
        when(confirmService.get(OP)).thenReturn(session(WriteOpState.CONFIRMED));
        ExecutionResult r = service.executeConfirmed(OP);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("无执行器");
        verify(confirmService, never()).complete(anyString());
    }

    @Test
    void 执行成功回执complete并透传引用()
    {
        when(confirmService.get(OP)).thenReturn(session(WriteOpState.CONFIRMED));
        when(confirmService.complete(OP))
                .thenReturn(TransitionResult.ok(WriteOpState.EXECUTED));
        service.setExecutors(Collections.singletonList(new StubExecutor(true, "ref-9")));
        ExecutionResult r = service.executeConfirmed(OP);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getRef()).isEqualTo("ref-9");
        verify(confirmService).complete(OP);
    }

    @Test
    void 执行失败保持CONFIRMED不回执()
    {
        when(confirmService.get(OP)).thenReturn(session(WriteOpState.CONFIRMED));
        service.setExecutors(Collections.singletonList(new StubExecutor(false, null)));
        ExecutionResult r = service.executeConfirmed(OP);
        assertThat(r.isSuccess()).isFalse();
        verify(confirmService, never()).complete(anyString());
    }

    @Test
    void 落库成功但回执失败返回失败()
    {
        when(confirmService.get(OP)).thenReturn(session(WriteOpState.CONFIRMED));
        when(confirmService.complete(OP))
                .thenReturn(TransitionResult.fail("当前状态不允许回执完成", WriteOpState.CONFIRMED));
        service.setExecutors(Collections.singletonList(new StubExecutor(true, null)));
        ExecutionResult r = service.executeConfirmed(OP);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("回执失败");
    }

    @Test
    void 执行器异常被隔离为失败()
    {
        when(confirmService.get(OP)).thenReturn(session(WriteOpState.CONFIRMED));
        service.setExecutors(Collections.singletonList(new StubExecutor(true, null)
        {
            @Override
            public ExecutionResult execute(WriteOpSession s)
            {
                throw new IllegalStateException("boom");
            }
        }));
        ExecutionResult r = service.executeConfirmed(OP);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("执行异常");
        verify(confirmService, never()).complete(anyString());
    }
}
