package ai.lawyers.system.service.impl.lawyers.voice.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ai.lawyers.system.domain.lawyers.AiCallback;
import ai.lawyers.system.service.lawyers.IAiCallbackService;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpExecutor.ExecutionResult;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpState;

/**
 * {@link BookCallbackWriteOpExecutor} 测试：成功建单（号码/状态/回访人锚定）、
 * 缺 visitTime、时间非法、写入失败、服务未就绪。
 *
 * @author ai-lawyers
 */
class BookCallbackWriteOpExecutorTest
{
    private static final String PHONE = "13800001234";

    private IAiCallbackService callbackService;

    private BookCallbackWriteOpExecutor executor;

    @BeforeEach
    void setUp()
    {
        callbackService = mock(IAiCallbackService.class);
        executor = new BookCallbackWriteOpExecutor();
        executor.setCallbackService(callbackService);
    }

    private WriteOpSession session(String payloadJson)
    {
        WriteOpSession s = new WriteOpSession("op-1", WriteOpAction.BOOK_CALLBACK, "sess-1", 9L,
                PHONE, payloadJson, System.currentTimeMillis());
        s.setState(WriteOpState.CONFIRMED);
        return s;
    }

    @Test
    void 成功建单且字段按契约锚定()
    {
        when(callbackService.insertAiCallback(any(AiCallback.class))).thenReturn(1);

        ExecutionResult r = executor.execute(session(
                "{\"visitTime\":\"2026-10-12 09:30\",\"callerName\":\"张三\",\"ledgerId\":7}"));
        assertThat(r.isSuccess()).isTrue();

        ArgumentCaptor<AiCallback> captor = ArgumentCaptor.forClass(AiCallback.class);
        verify(callbackService).insertAiCallback(captor.capture());
        AiCallback saved = captor.getValue();
        assertThat(saved.getCallerNumber()).isEqualTo(PHONE);
        assertThat(saved.getCallerName()).isEqualTo("张三");
        assertThat(saved.getLedgerId()).isEqualTo(7L);
        assertThat(saved.getVisitTime())
                .isEqualTo(ai.lawyers.common.utils.DateUtils.parseDate("2026-10-12 09:30"));
        assertThat(saved.getVisitBy()).isEqualTo("voice-robot");
        assertThat(saved.getStatus()).isEqualTo("0");
    }

    @Test
    void 缺少visitTime拒绝()
    {
        ExecutionResult r = executor.execute(session("{\"callerName\":\"张三\"}"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("visitTime");
    }

    @Test
    void 时间格式非法拒绝()
    {
        ExecutionResult r = executor.execute(session("{\"visitTime\":\"下周三上午\"}"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("格式非法");
    }

    @Test
    void 写入失败返回失败()
    {
        when(callbackService.insertAiCallback(any(AiCallback.class))).thenReturn(0);
        ExecutionResult r = executor.execute(session("{\"visitTime\":\"2026-10-12 09:30\"}"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("写入失败");
    }

    @Test
    void 服务未就绪拒绝()
    {
        executor.setCallbackService(null);
        ExecutionResult r = executor.execute(session("{\"visitTime\":\"2026-10-12 09:30\"}"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("未就绪");
    }
}
