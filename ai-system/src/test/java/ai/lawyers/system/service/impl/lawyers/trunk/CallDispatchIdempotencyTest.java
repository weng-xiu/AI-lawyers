package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.queue.CallEventDispatcher;

/**
 * P3-B4：{@link CallDispatchServiceImpl#onCallEvent} 终态幂等兜底（第二道防线）。
 */
class CallDispatchIdempotencyTest
{
    private CallDispatchServiceImpl service;
    private AiCallDialLogMapper dialLogMapper;
    private CallEventDispatcher callEventDispatcher;

    @BeforeEach
    void setUp()
    {
        service = new CallDispatchServiceImpl();
        dialLogMapper = mock(AiCallDialLogMapper.class);
        callEventDispatcher = mock(CallEventDispatcher.class);
        ReflectionTestUtils.setField(service, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(service, "callEventDispatcher", callEventDispatcher);
    }

    private static AiCallDialLog dialLog(String callUuid, String status)
    {
        AiCallDialLog d = new AiCallDialLog();
        d.setLogId(1L);
        d.setCallUuid(callUuid);
        d.setDialStatus(status);
        return d;
    }

    @Test
    void finalStatus_ignoresDuplicateHangup()
    {
        when(dialLogMapper.selectByCallUuid("u1"))
                .thenReturn(dialLog("u1", DialStatusEnum.HANGUP.getCode()));

        service.onCallEvent("u1", "HANGUP", new HashMap<>());

        // 终态后直接返回：不落库、不进入 finishCall 双释放
        verify(dialLogMapper, never()).updateAiCallDialLog(any());
        verify(callEventDispatcher, never()).updateDialLogAsync(any());
    }

    @Test
    void finalStatus_ignoresLateAnswered()
    {
        when(dialLogMapper.selectByCallUuid("u1"))
                .thenReturn(dialLog("u1", DialStatusEnum.FAILED.getCode()));

        service.onCallEvent("u1", "ANSWERED", new HashMap<>());

        verify(dialLogMapper, never()).updateAiCallDialLog(any());
        verify(callEventDispatcher, never()).updateDialLogAsync(any());
    }

    @Test
    void nonFinalStatus_proceedsNormally()
    {
        when(dialLogMapper.selectByCallUuid("u1"))
                .thenReturn(dialLog("u1", DialStatusEnum.DIALING.getCode()));

        service.onCallEvent("u1", "RINGING", new HashMap<>());

        verify(callEventDispatcher).updateDialLogAsync(ArgumentMatchers.argThat(
                u -> DialStatusEnum.RINGING.getCode().equals(u.getDialStatus())));
    }
}
