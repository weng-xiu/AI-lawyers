package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;

/**
 * P5：{@link OutboundEventCallback} 外呼网关事件回调测试。
 *
 * <p>覆盖入参/定位失败直接忽略、ANSWERED 建话单并回写号码与拨号日志、
 * 号码已接通或已终态时幂等跳过、HANGUP/FAILED 接通判定（拨号日志、回调参数、
 * 非数字容错）与终态收尾、dialLog 缺 calleeId 时按 recordId/号码/状态回退匹配、
 * 大小写归一与指标埋点缺失时的空安全。</p>
 *
 * @author ai-lawyers
 */
class OutboundEventCallbackTest
{
    private static final Long TASK_ID = 7L;

    private static final Long CALLEE_ID = 5L;

    private static final String UUID = "u1";

    private AiCallDialLogMapper dialLogMapper;

    private AiOutboundCalleeMapper calleeMapper;

    private IAiOutboundTaskService taskService;

    private OutboundCallFinalizer finalizer;

    private HotlineMetrics metrics;

    private OutboundEventCallback callback;

    @BeforeEach
    void setUp()
    {
        dialLogMapper = mock(AiCallDialLogMapper.class);
        calleeMapper = mock(AiOutboundCalleeMapper.class);
        taskService = mock(IAiOutboundTaskService.class);
        finalizer = mock(OutboundCallFinalizer.class);
        metrics = mock(HotlineMetrics.class);
        callback = new OutboundEventCallback();
        ReflectionTestUtils.setField(callback, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(callback, "calleeMapper", calleeMapper);
        ReflectionTestUtils.setField(callback, "taskService", taskService);
        ReflectionTestUtils.setField(callback, "finalizer", finalizer);
        ReflectionTestUtils.setField(callback, "metrics", metrics);
    }

    // ---------- 入参与定位 ----------

    @Test
    void handleIgnoresMissingCallUuidOrEventType()
    {
        callback.handle(null, "ANSWERED", Collections.emptyMap());
        callback.handle(UUID, null, Collections.emptyMap());

        verifyNoInteractions(dialLogMapper, calleeMapper, taskService, finalizer, metrics);
    }

    @Test
    void handleIgnoresUnknownCallUuid()
    {
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(null);

        callback.handle(UUID, "ANSWERED", Collections.emptyMap());

        verifyNoInteractions(calleeMapper, taskService, finalizer, metrics);
    }

    @Test
    void handleIgnoresDialLogWithoutTask()
    {
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog(null, CALLEE_ID));

        callback.handle(UUID, "ANSWERED", Collections.emptyMap());

        verifyNoInteractions(calleeMapper, taskService, finalizer, metrics);
    }

    @Test
    void handleIgnoresUnknownEventType()
    {
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog(TASK_ID, CALLEE_ID));
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(callee(CALLEE_ID, "2"));

        callback.handle(UUID, "RINGING", Collections.emptyMap());

        verifyNoInteractions(finalizer, metrics);
    }

    // ---------- ANSWERED ----------

    @Test
    void answeredCreatesRecordAndWritesBackIds()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, CALLEE_ID);
        AiOutboundCallee target = callee(CALLEE_ID, "1");
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(88L);
        AiOutboundTask task = new AiOutboundTask();
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(target);
        when(finalizer.createCallRecord(TASK_ID, target)).thenReturn(record);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        callback.handle(UUID, "answered", Collections.emptyMap());

        ArgumentCaptor<AiOutboundCallee> calleeUpd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(calleeUpd.capture());
        assertThat(calleeUpd.getValue().getCalleeId()).isEqualTo(CALLEE_ID);
        assertThat(calleeUpd.getValue().getCallStatus()).isEqualTo("2");
        assertThat(calleeUpd.getValue().getRecordId()).isEqualTo(88L);
        assertThat(calleeUpd.getValue().getUpdateBy()).isEqualTo("outbound-event");

        ArgumentCaptor<AiCallDialLog> logUpd = ArgumentCaptor.forClass(AiCallDialLog.class);
        verify(dialLogMapper).updateAiCallDialLog(logUpd.capture());
        assertThat(logUpd.getValue().getLogId()).isEqualTo(9L);
        assertThat(logUpd.getValue().getRecordId()).isEqualTo(88L);
        assertThat(logUpd.getValue().getCalleeId()).isEqualTo(CALLEE_ID);

        verify(finalizer).maybeCreateTicket(task, target, 88L, null);
        verify(metrics).incrementOutbound("answer");
        // 已按 calleeId 精确定位，不再按任务兜底查询
        verify(calleeMapper, never()).selectAiOutboundCalleeByTaskId(any());
    }

    @Test
    void answeredSkipsCalleeNotDialing()
    {
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog(TASK_ID, CALLEE_ID));
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(callee(CALLEE_ID, "2"));

        callback.handle(UUID, "ANSWERED", Collections.emptyMap());

        verify(finalizer, never()).createCallRecord(any(), any());
        verifyNoInteractions(metrics);
    }

    @Test
    void answeredWorksWithoutMetrics()
    {
        ReflectionTestUtils.setField(callback, "metrics", null);
        AiCallDialLog dialLog = dialLog(TASK_ID, CALLEE_ID);
        AiOutboundCallee target = callee(CALLEE_ID, "1");
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(90L);
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(target);
        when(finalizer.createCallRecord(TASK_ID, target)).thenReturn(record);

        callback.handle(UUID, "ANSWERED", Collections.emptyMap());

        verify(calleeMapper).updateCalleeStatus(any(AiOutboundCallee.class));
    }

    // ---------- HANGUP / FAILED ----------

    @Test
    void hangupUsesDialLogConnectedFlagAndTalkDuration()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, CALLEE_ID);
        dialLog.setIsConnected("1");
        dialLog.setTalkDuration(45);
        AiOutboundCallee target = callee(CALLEE_ID, "2");
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(target);

        callback.handle(UUID, "hangup", null);

        verify(finalizer).finalizeCallee(TASK_ID, target, true, 45, "HANGUP");
    }

    @Test
    void hangupUsesAnswerTimeAsConnected()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, CALLEE_ID);
        dialLog.setAnswerTime(new Date());
        AiOutboundCallee target = callee(CALLEE_ID, "2");
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(target);

        callback.handle(UUID, "HANGUP", null);

        verify(finalizer).finalizeCallee(TASK_ID, target, true, 0, "HANGUP");
    }

    @Test
    void failedUsesCallbackParams()
    {
        AiOutboundCallee target = callee(CALLEE_ID, "2");
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog(TASK_ID, CALLEE_ID));
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(target);
        Map<String, Object> params = new HashMap<>();
        params.put("isConnected", "1");
        params.put("talkDuration", "30");

        callback.handle(UUID, "FAILED", params);

        verify(finalizer).finalizeCallee(TASK_ID, target, true, 30, "FAILED");
    }

    @Test
    void callbackParamIsConnectedFalseOverridesTalkDuration()
    {
        AiOutboundCallee target = callee(CALLEE_ID, "2");
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog(TASK_ID, CALLEE_ID));
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(target);
        Map<String, Object> params = new HashMap<>();
        params.put("isConnected", "0");
        params.put("talkDuration", "30");

        callback.handle(UUID, "HANGUP", params);

        verify(finalizer).finalizeCallee(TASK_ID, target, false, 30, "HANGUP");
    }

    @Test
    void nonNumericTalkDurationTreatedAsNotConnected()
    {
        AiOutboundCallee target = callee(CALLEE_ID, "2");
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog(TASK_ID, CALLEE_ID));
        when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(target);
        Map<String, Object> params = new HashMap<>();
        params.put("talkDuration", "abc");

        callback.handle(UUID, "HANGUP", params);

        verify(finalizer).finalizeCallee(TASK_ID, target, false, 0, "HANGUP");
    }

    @Test
    void hangupSkipsCalleeInTerminalStatus()
    {
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog(TASK_ID, CALLEE_ID));
        for (String status : Arrays.asList("3", "4", "5"))
        {
            when(calleeMapper.selectAiOutboundCalleeByCalleeId(CALLEE_ID)).thenReturn(callee(CALLEE_ID, status));

            callback.handle(UUID, "HANGUP", null);
        }

        verify(finalizer, never()).finalizeCallee(any(), any(), anyBoolean(), anyInt(), anyString());
    }

    // ---------- 回退匹配 ----------

    @Test
    void fallbackMatchesByRecordId()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, null);
        dialLog.setRecordId(30L);
        AiOutboundCallee matched = callee(21L, "2");
        matched.setRecordId(30L);
        AiOutboundCallee other = callee(22L, "2");
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByTaskId(TASK_ID)).thenReturn(Arrays.asList(other, matched));

        callback.handle(UUID, "HANGUP", null);

        verify(calleeMapper, never()).selectAiOutboundCalleeByCalleeId(any());
        verify(finalizer).finalizeCallee(eq(TASK_ID), eq(matched), eq(false), eq(0), eq("HANGUP"));
    }

    @Test
    void fallbackMatchesByCalleeNumber()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, null);
        dialLog.setCalleeNumber("13900000000");
        AiOutboundCallee other = callee(21L, "2");
        AiOutboundCallee matched = callee(22L, "2");
        matched.setCalleeNumber("13900000000");
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByTaskId(TASK_ID)).thenReturn(Arrays.asList(other, matched));

        callback.handle(UUID, "HANGUP", null);

        verify(finalizer).finalizeCallee(eq(TASK_ID), eq(matched), eq(false), eq(0), eq("HANGUP"));
    }

    @Test
    void fallbackPrefersDialingStatusForAnswered()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, null);
        AiOutboundCallee connected = callee(21L, "2");
        AiOutboundCallee dialing = callee(22L, "1");
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(66L);
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByTaskId(TASK_ID)).thenReturn(Arrays.asList(connected, dialing));
        when(finalizer.createCallRecord(TASK_ID, dialing)).thenReturn(record);

        callback.handle(UUID, "ANSWERED", Collections.emptyMap());

        verify(finalizer).createCallRecord(TASK_ID, dialing);
    }

    @Test
    void fallbackPrefersConnectedStatusForHangup()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, null);
        AiOutboundCallee dialing = callee(21L, "1");
        AiOutboundCallee connected = callee(22L, "2");
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByTaskId(TASK_ID)).thenReturn(Arrays.asList(dialing, connected));

        callback.handle(UUID, "HANGUP", null);

        verify(finalizer).finalizeCallee(eq(TASK_ID), eq(connected), eq(false), eq(0), eq("HANGUP"));
    }

    @Test
    void fallbackGivesUpWhenNoStatusMatches()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, null);
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByTaskId(TASK_ID)).thenReturn(Arrays.asList(callee(21L, "0")));

        callback.handle(UUID, "HANGUP", null);

        verifyNoInteractions(finalizer);
    }

    @Test
    void fallbackGivesUpOnEmptyCalleeList()
    {
        AiCallDialLog dialLog = dialLog(TASK_ID, null);
        when(dialLogMapper.selectByCallUuid(UUID)).thenReturn(dialLog);
        when(calleeMapper.selectAiOutboundCalleeByTaskId(TASK_ID)).thenReturn(Collections.emptyList());

        callback.handle(UUID, "ANSWERED", Collections.emptyMap());

        verifyNoInteractions(finalizer);
    }

    private static AiCallDialLog dialLog(Long taskId, Long calleeId)
    {
        AiCallDialLog dialLog = new AiCallDialLog();
        dialLog.setLogId(9L);
        dialLog.setCallUuid(UUID);
        dialLog.setTaskId(taskId);
        dialLog.setCalleeId(calleeId);
        return dialLog;
    }

    private static AiOutboundCallee callee(Long calleeId, String callStatus)
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        callee.setCalleeId(calleeId);
        callee.setTaskId(TASK_ID);
        callee.setCallStatus(callStatus);
        callee.setCalleeNumber("13800000000");
        return callee;
    }
}
