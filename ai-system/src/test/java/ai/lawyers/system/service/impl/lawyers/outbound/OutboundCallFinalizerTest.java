package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundResultService;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.queue.CallEventDispatcher;

/**
 * P5：{@link OutboundCallFinalizer} 外呼落库收尾测试。
 *
 * <p>覆盖合规跳过（状态/原因前缀/完成计数、异常吞没）、终态失败（号码/结果/失败与
 * 完成计数、指标）、接通话单生成（任务缺失兜底）、IVR 结果回写（分类/转人工状态/
 * 备注、异常吞没）、自动工单（开关、回访与转人工触发、幂等条件、异常吞没）、
 * 外呼结果组装（IVR 摘要/意图/流程数据）以及网关事件最终化（接通、重试退避、
 * 已接未接、失败、重试耗尽、任务缺失）。</p>
 *
 * @author ai-lawyers
 */
class OutboundCallFinalizerTest
{
    private static final Long TASK_ID = 7L;

    private static final Long CALLEE_ID = 11L;

    private IAiOutboundTaskService taskService;

    private IAiOutboundResultService resultService;

    private IAiCallRecordService callRecordService;

    private IAiCallTicketService callTicketService;

    private AiOutboundTaskMapper taskMapper;

    private AiOutboundCalleeMapper calleeMapper;

    private CallEventDispatcher callEventDispatcher;

    private HotlineMetrics metrics;

    private TransactionTemplate transactionTemplate;

    private OutboundCallFinalizer finalizer;

    @BeforeEach
    void setUp()
    {
        taskService = mock(IAiOutboundTaskService.class);
        resultService = mock(IAiOutboundResultService.class);
        callRecordService = mock(IAiCallRecordService.class);
        callTicketService = mock(IAiCallTicketService.class);
        taskMapper = mock(AiOutboundTaskMapper.class);
        calleeMapper = mock(AiOutboundCalleeMapper.class);
        callEventDispatcher = mock(CallEventDispatcher.class);
        metrics = mock(HotlineMetrics.class);
        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation ->
        {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        finalizer = new OutboundCallFinalizer();
        ReflectionTestUtils.setField(finalizer, "taskService", taskService);
        ReflectionTestUtils.setField(finalizer, "resultService", resultService);
        ReflectionTestUtils.setField(finalizer, "callRecordService", callRecordService);
        ReflectionTestUtils.setField(finalizer, "callTicketService", callTicketService);
        ReflectionTestUtils.setField(finalizer, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(finalizer, "calleeMapper", calleeMapper);
        ReflectionTestUtils.setField(finalizer, "callEventDispatcher", callEventDispatcher);
        ReflectionTestUtils.setField(finalizer, "metrics", metrics);
        ReflectionTestUtils.setField(finalizer, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(finalizer, "autoCreateTicket", true);
        ReflectionTestUtils.setField(finalizer, "retryBackoffBaseMs", 1000L);
    }

    // ---------- 合规跳过 ----------

    @Test
    void markCalleeSkippedUpdatesStatusAndCounts()
    {
        AiOutboundCallee callee = callee();

        finalizer.markCalleeSkipped(callee, "退订");

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCalleeId()).isEqualTo(CALLEE_ID);
        assertThat(upd.getValue().getCallStatus()).isEqualTo("4");
        assertThat(upd.getValue().getFailReason()).isEqualTo("合规拦截:退订");
        assertThat(upd.getValue().getCallTime()).isNotNull();
        assertThat(upd.getValue().getUpdateBy()).isEqualTo("outbound-executor");
        verify(taskMapper).incrementCompletedCount(TASK_ID);
        verifyNoInteractions(taskService, resultService);
    }

    @Test
    void markCalleeSkippedTruncatesReason()
    {
        AiOutboundCallee callee = callee();
        finalizer.markCalleeSkipped(callee, repeat('r', 600));

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getFailReason()).hasSize(500);
    }

    @Test
    void markCalleeSkippedSwallowsFailure()
    {
        doThrow(new IllegalStateException("db down")).when(calleeMapper).updateCalleeStatus(any());

        finalizer.markCalleeSkipped(callee(), "退订");

        verify(taskMapper, never()).incrementCompletedCount(any());
    }

    // ---------- 终态失败 ----------

    @Test
    void markCalleeFailedWritesCalleeResultAndCounters()
    {
        AiOutboundTask task = task();
        AiOutboundCallee callee = callee();
        callee.setCalleeName("张三");
        callee.setCalleeNumber("13800138000");

        finalizer.markCalleeFailed(task, callee, "空号");

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCallStatus()).isEqualTo("4");
        assertThat(upd.getValue().getFailReason()).isEqualTo("空号");

        ArgumentCaptor<AiOutboundResult> result = ArgumentCaptor.forClass(AiOutboundResult.class);
        verify(resultService).insertAiOutboundResult(result.capture());
        assertThat(result.getValue().getCallResult()).isEqualTo("7");
        assertThat(result.getValue().getCallDuration()).isZero();
        verify(taskMapper).incrementFailedCount(TASK_ID);
        verify(taskMapper).incrementCompletedCount(TASK_ID);
        verify(metrics).incrementOutbound("fail");
    }

    @Test
    void markCalleeFailedWorksWithoutMetrics()
    {
        ReflectionTestUtils.setField(finalizer, "metrics", null);

        finalizer.markCalleeFailed(task(), callee(), "空号");

        verify(taskMapper).incrementFailedCount(TASK_ID);
    }

    // ---------- 话单生成 ----------

    @Test
    void createCallRecordIncludesTaskContext()
    {
        AiOutboundTask task = task();
        task.setCreateBy("tester");
        AiOutboundCallee callee = callee();
        callee.setCalleeName("张三");
        callee.setCalleeAddress("杭州市");
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        AiCallRecord record = finalizer.createCallRecord(TASK_ID, callee);

        assertThat(record.getCallerNumber()).isEqualTo("057100000000");
        assertThat(record.getCallerName()).isEqualTo("张三");
        assertThat(record.getCallerAddress()).isEqualTo("杭州市");
        assertThat(record.getStatus()).isEqualTo("1");
        assertThat(record.getContent()).contains("T-001").contains("13800138000");
        assertThat(record.getRemark()).contains("任务:T-001");
        assertThat(record.getCreateBy()).isEqualTo("tester");
        assertThat(record.getCallTime()).isNotNull();
        verify(callRecordService).insertAiCallRecord(record);
    }

    @Test
    void createCallRecordToleratesMissingTask()
    {
        AiOutboundCallee callee = callee();
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(null);

        AiCallRecord record = finalizer.createCallRecord(TASK_ID, callee);

        assertThat(record.getCallerNumber()).isNull();
        assertThat(record.getContent()).doesNotContain("null");
        assertThat(record.getCreateBy()).isEqualTo("outbound-executor");
    }

    // ---------- IVR 结果回写 ----------

    @Test
    void updateRecordWithFlowKeepsStatusWithoutTransfer()
    {
        IvrExecuteResult flow = new IvrExecuteResult();
        flow.setFlowName("咨询流程");
        flow.setMatchedIntention("labor");
        flow.setMatchedIntentionName("劳动纠纷");
        flow.setCategoryName("劳动争议");

        finalizer.updateRecordWithFlow(88L, flow);

        ArgumentCaptor<AiCallRecord> upd = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callEventDispatcher).updateCallRecordAsync(upd.capture());
        assertThat(upd.getValue().getRecordId()).isEqualTo(88L);
        assertThat(upd.getValue().getStatus()).isNull();
        assertThat(upd.getValue().getCategoryId()).isNull();
        assertThat(upd.getValue().getRemark())
                .contains("流程[咨询流程]")
                .contains("劳动纠纷(labor)")
                .contains("分类=劳动争议");
    }

    @Test
    void updateRecordWithFlowMarksTransferStatus()
    {
        IvrExecuteResult flow = new IvrExecuteResult();
        flow.setFlowName("转人工");
        flow.setTransferTarget("坐席ID 7");
        flow.setCategoryId(5L);

        finalizer.updateRecordWithFlow(88L, flow);

        ArgumentCaptor<AiCallRecord> upd = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callEventDispatcher).updateCallRecordAsync(upd.capture());
        assertThat(upd.getValue().getStatus()).isEqualTo("2");
        assertThat(upd.getValue().getCategoryId()).isEqualTo(5L);
    }

    @Test
    void updateRecordWithFlowSwallowsFailure()
    {
        doThrow(new IllegalStateException("queue down")).when(callEventDispatcher)
                .updateCallRecordAsync(any(AiCallRecord.class));

        finalizer.updateRecordWithFlow(88L, new IvrExecuteResult());

        verify(callEventDispatcher).updateCallRecordAsync(any(AiCallRecord.class));
    }

    // ---------- 自动工单 ----------

    @Test
    void autoTicketDisabledDoesNothing()
    {
        ReflectionTestUtils.setField(finalizer, "autoCreateTicket", false);

        finalizer.maybeCreateTicket(task(), callee(), 88L, null);

        verifyNoInteractions(callTicketService);
    }

    @Test
    void visitTaskCreatesTicket()
    {
        AiOutboundTask task = task();
        task.setTaskType("2");
        AiOutboundCallee callee = callee();
        callee.setCalleeName("张三");

        finalizer.maybeCreateTicket(task, callee, 88L, null);

        ArgumentCaptor<AiCallTicket> ticket = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(callTicketService).insertAiCallTicket(ticket.capture());
        assertThat(ticket.getValue().getRecordId()).isEqualTo(88L);
        assertThat(ticket.getValue().getTitle()).isEqualTo("回访工单：欠薪回访");
        assertThat(ticket.getValue().getContent()).contains("13800138000").contains("张三");
        assertThat(ticket.getValue().getPriority()).isEqualTo("2");
        assertThat(ticket.getValue().getStatus()).isEqualTo("0");
        assertThat(ticket.getValue().getAssignUserName()).isEqualTo("王五");
        assertThat(ticket.getValue().getCreateBy()).isEqualTo("tester");
    }

    @Test
    void transferFlowCreatesFollowUpTicket()
    {
        AiOutboundTask task = task();
        IvrExecuteResult flow = new IvrExecuteResult();
        flow.setTransferTarget("坐席ID 7");
        flow.setMatchedIntentionName("劳动纠纷");

        finalizer.maybeCreateTicket(task, callee(), 88L, flow);

        ArgumentCaptor<AiCallTicket> ticket = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(callTicketService).insertAiCallTicket(ticket.capture());
        assertThat(ticket.getValue().getTitle()).isEqualTo("跟进工单：欠薪回访");
        assertThat(ticket.getValue().getContent()).contains("识别意图:劳动纠纷");
    }

    @Test
    void ticketSkippedForNonVisitTaskWithoutTransfer()
    {
        finalizer.maybeCreateTicket(task(), callee(), 88L, new IvrExecuteResult());

        verifyNoInteractions(callTicketService);
    }

    @Test
    void ticketSkippedWithoutTaskOrRecord()
    {
        finalizer.maybeCreateTicket(null, callee(), 88L, null);
        finalizer.maybeCreateTicket(task(), callee(), null, null);

        verifyNoInteractions(callTicketService);
    }

    @Test
    void ticketInsertFailureIsSwallowed()
    {
        AiOutboundTask task = task();
        task.setTaskType("2");
        doThrow(new IllegalStateException("db down")).when(callTicketService).insertAiCallTicket(any());

        finalizer.maybeCreateTicket(task, callee(), 88L, null);

        verify(callTicketService).insertAiCallTicket(any());
    }

    // ---------- 外呼结果 ----------

    @Test
    void outboundResultCarriesIvrSummary()
    {
        AiOutboundTask task = task();
        AiOutboundCallee callee = callee();
        IvrExecuteResult flow = new IvrExecuteResult();
        flow.setFlowName("咨询流程");
        flow.setMatchedIntention("labor");
        flow.setMatchedIntentionName("劳动纠纷");
        flow.setSteps(Arrays.asList(new IvrNodeStep(1L, "say", "欢迎", "SAY", "欢迎语")));

        finalizer.createOutboundResult(task, callee, 88L, "1", new Date(), new Date(), 45, flow);

        ArgumentCaptor<AiOutboundResult> result = ArgumentCaptor.forClass(AiOutboundResult.class);
        verify(resultService).insertAiOutboundResult(result.capture());
        assertThat(result.getValue().getTaskId()).isEqualTo(TASK_ID);
        assertThat(result.getValue().getCalleeId()).isEqualTo(CALLEE_ID);
        assertThat(result.getValue().getRecordId()).isEqualTo(88L);
        assertThat(result.getValue().getCallResult()).isEqualTo("1");
        assertThat(result.getValue().getCallDuration()).isEqualTo(45);
        assertThat(result.getValue().getIntentionCode()).isEqualTo("labor");
        assertThat(result.getValue().getIntentionName()).isEqualTo("劳动纠纷");
        assertThat(result.getValue().getSummary()).contains("共1个节点");
        assertThat(result.getValue().getFlowData()).contains("\"flowName\":\"咨询流程\"");
        assertThat(result.getValue().getCreateBy()).isEqualTo("tester");
    }

    @Test
    void outboundResultWithoutFlowUsesPlainSummary()
    {
        finalizer.createOutboundResult(task(), callee(), 88L, "2", new Date(), new Date(), 0, null);

        ArgumentCaptor<AiOutboundResult> result = ArgumentCaptor.forClass(AiOutboundResult.class);
        verify(resultService).insertAiOutboundResult(result.capture());
        assertThat(result.getValue().getSummary()).isEqualTo("外呼完成（无IVR流程）");
        assertThat(result.getValue().getFlowData()).isNull();
        assertThat(result.getValue().getIntentionCode()).isNull();
    }

    // ---------- 网关事件最终化 ----------

    @Test
    void finalizeSkipsMissingTask()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(null);

        finalizer.finalizeCallee(TASK_ID, callee(), true, 30, "HANGUP");

        verifyNoInteractions(calleeMapper, resultService);
    }

    @Test
    void finalizeConnectedWritesAnswered()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task());

        finalizer.finalizeCallee(TASK_ID, callee(), true, 30, "HANGUP");

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCallStatus()).isEqualTo("5");
        assertThat(upd.getValue().getCallDuration()).isEqualTo(30);
        assertThat(upd.getValue().getUpdateBy()).isEqualTo("outbound-event");

        ArgumentCaptor<AiOutboundResult> result = ArgumentCaptor.forClass(AiOutboundResult.class);
        verify(resultService).insertAiOutboundResult(result.capture());
        assertThat(result.getValue().getCallResult()).isEqualTo("1");
        verify(taskMapper).incrementAnsweredCount(TASK_ID);
        verify(taskMapper).incrementCompletedCount(TASK_ID);
    }

    @Test
    void finalizeNoAnswerSchedulesRetry()
    {
        AiOutboundTask task = task();
        task.setRetryCount(2);
        AiOutboundCallee callee = callee();
        callee.setRetryTimes(0);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        finalizer.finalizeCallee(TASK_ID, callee, false, 0, "HANGUP");

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCallStatus()).isEqualTo("0");
        assertThat(upd.getValue().getRetryTimes()).isEqualTo(1);
        assertThat(upd.getValue().getLastRetryTime()).isNotNull();
        verify(calleeMapper).updateNextRetryTime(eq(CALLEE_ID), any(Date.class), eq("outbound-event"));
        verifyNoInteractions(resultService);
        verify(taskMapper, never()).incrementCompletedCount(any());
    }

    @Test
    void finalizeFailedEventIsTerminal()
    {
        AiOutboundTask task = task();
        task.setRetryCount(2);
        AiOutboundCallee callee = callee();
        callee.setRetryTimes(0);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        finalizer.finalizeCallee(TASK_ID, callee, false, 0, "FAILED");

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCallStatus()).isEqualTo("4");
        assertThat(upd.getValue().getFailReason()).isEqualTo("网关事件:FAILED");

        ArgumentCaptor<AiOutboundResult> result = ArgumentCaptor.forClass(AiOutboundResult.class);
        verify(resultService).insertAiOutboundResult(result.capture());
        assertThat(result.getValue().getCallResult()).isEqualTo("4");
        verify(taskMapper).incrementFailedCount(TASK_ID);
        verify(taskMapper).incrementCompletedCount(TASK_ID);
        verify(calleeMapper, never()).updateNextRetryTime(any(), any(), any());
    }

    @Test
    void finalizeNoAnswerAfterRetriesExhaustedIsTerminal()
    {
        AiOutboundTask task = task();
        task.setRetryCount(2);
        AiOutboundCallee callee = callee();
        callee.setRetryTimes(2);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        finalizer.finalizeCallee(TASK_ID, callee, false, 0, "HANGUP");

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCallStatus()).isEqualTo("3");
        verify(taskMapper).incrementNoAnswerCount(TASK_ID);
        verify(taskMapper).incrementCompletedCount(TASK_ID);
        verify(taskMapper, never()).incrementFailedCount(any());
    }

    @Test
    void finalizeWithNullRetryFieldsIsTerminal()
    {
        AiOutboundTask task = task();
        task.setRetryCount(null);
        AiOutboundCallee callee = callee();
        callee.setRetryTimes(null);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        finalizer.finalizeCallee(TASK_ID, callee, false, 0, "HANGUP");

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCallStatus()).isEqualTo("3");
    }

    private static AiOutboundTask task()
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(TASK_ID);
        task.setTaskNo("T-001");
        task.setTaskName("欠薪回访");
        task.setCallerNumber("057100000000");
        task.setCallerName("王五");
        task.setRetryCount(0);
        task.setCreateBy("tester");
        return task;
    }

    private static AiOutboundCallee callee()
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        callee.setCalleeId(CALLEE_ID);
        callee.setTaskId(TASK_ID);
        callee.setCalleeNumber("13800138000");
        callee.setRecordId(88L);
        callee.setCallTime(new Date(1_700_000_000_000L));
        return callee;
    }

    private static String repeat(char ch, int count)
    {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++)
        {
            sb.append(ch);
        }
        return sb.toString();
    }
}
