package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;
import ai.lawyers.system.service.lawyers.compliance.ComplianceGuard;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

/**
 * P5：{@link OutboundDialer} 单号码外呼拨号协作测试。
 *
 * <p>覆盖合规退订与高频置底（REJECT 跳过、PRIORITY 沉底）拦截、DialRequest 组装
 * （号码/优先级/应答动作/创建人/备注）、下发失败的重试与终态分流（可重试错误、
 * 次数耗尽、原因截断）、模拟接通落库事务与计数、IVR 流程执行（样例输入优先级、
 * 变量透传、异常降级）以及指标缺失时的空安全。</p>
 *
 * @author ai-lawyers
 */
class OutboundDialerTest
{
    private static final Long TASK_ID = 7L;

    private static final Long CALLEE_ID = 11L;

    private ICallDispatchService callDispatchService;

    private IIvrEngineService ivrEngineService;

    private AiOutboundCalleeMapper calleeMapper;

    private AiOutboundTaskMapper taskMapper;

    private ComplianceGuard complianceGuard;

    private HotlineMetrics metrics;

    private OutboundCallFinalizer finalizer;

    private IAiHotspotSuppressService hotspotSuppressService;

    private TransactionTemplate transactionTemplate;

    private OutboundDialer dialer;

    @BeforeEach
    void setUp()
    {
        callDispatchService = mock(ICallDispatchService.class);
        ivrEngineService = mock(IIvrEngineService.class);
        calleeMapper = mock(AiOutboundCalleeMapper.class);
        taskMapper = mock(AiOutboundTaskMapper.class);
        complianceGuard = mock(ComplianceGuard.class);
        metrics = mock(HotlineMetrics.class);
        finalizer = mock(OutboundCallFinalizer.class);
        hotspotSuppressService = mock(IAiHotspotSuppressService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation ->
        {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        dialer = new OutboundDialer();
        ReflectionTestUtils.setField(dialer, "callDispatchService", callDispatchService);
        ReflectionTestUtils.setField(dialer, "ivrEngineService", ivrEngineService);
        ReflectionTestUtils.setField(dialer, "calleeMapper", calleeMapper);
        ReflectionTestUtils.setField(dialer, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(dialer, "complianceGuard", complianceGuard);
        ReflectionTestUtils.setField(dialer, "metrics", metrics);
        ReflectionTestUtils.setField(dialer, "finalizer", finalizer);
        ReflectionTestUtils.setField(dialer, "hotspotSuppressService", hotspotSuppressService);
        ReflectionTestUtils.setField(dialer, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(dialer, "hotspotEnabled", true);
        ReflectionTestUtils.setField(dialer, "simulateAnswer", true);
        ReflectionTestUtils.setField(dialer, "simulateTalkSeconds", 45);
        ReflectionTestUtils.setField(dialer, "retryBackoffBaseMs", 1000L);
    }

    // ---------- 合规与高频置底拦截 ----------

    @Test
    void unsubscribedCalleeIsSkipped()
    {
        AiOutboundTask task = task();
        AiOutboundCallee callee = callee();
        when(complianceGuard.isUnsubscribed("13800138000")).thenReturn(true);

        dialer.dialCallee(task, callee);

        verify(finalizer).markCalleeSkipped(callee, "UNSUBSCRIBED");
        verifyNoInteractions(callDispatchService, hotspotSuppressService);
    }

    @Test
    void hotspotRejectCalleeIsSkipped()
    {
        AiOutboundTask task = task();
        AiOutboundCallee callee = callee();
        when(hotspotSuppressService.matchOutbound("13800138000", TASK_ID)).thenReturn(suppress("REJECT"));

        dialer.dialCallee(task, callee);

        verify(finalizer).markCalleeSkipped(callee, "高频置底拦截:骚扰规则");
        verifyNoInteractions(callDispatchService);
    }

    @Test
    void hotspotDisabledSkipsMatch()
    {
        ReflectionTestUtils.setField(dialer, "hotspotEnabled", false);
        ReflectionTestUtils.setField(dialer, "simulateAnswer", false);
        AiOutboundTask task = task();
        AiOutboundCallee callee = callee();
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));

        dialer.dialCallee(task, callee);

        verify(hotspotSuppressService, never()).matchOutbound(any(), any());
        verify(callDispatchService).dialWithQueue(any(DialRequest.class));
    }

    @Test
    void hotspotPrioritySinksQueuePriority()
    {
        ReflectionTestUtils.setField(dialer, "simulateAnswer", false);
        AiOutboundTask task = task();
        AiOutboundCallee callee = callee();
        when(hotspotSuppressService.matchOutbound("13800138000", TASK_ID)).thenReturn(suppress("PRIORITY"));
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));

        dialer.dialCallee(task, callee);

        DialRequest request = captureDialRequest();
        assertThat(request.getPriority()).isEqualTo(10050);
    }

    @Test
    void complianceGuardOptional()
    {
        ReflectionTestUtils.setField(dialer, "complianceGuard", null);
        ReflectionTestUtils.setField(dialer, "simulateAnswer", false);
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));

        dialer.dialCallee(task(), callee());

        verify(callDispatchService).dialWithQueue(any(DialRequest.class));
    }

    // ---------- 请求组装 ----------

    @Test
    void buildsDialRequestWithTaskContext()
    {
        ReflectionTestUtils.setField(dialer, "simulateAnswer", false);
        AiOutboundTask task = task();
        task.setPriority(null);
        task.setCreateBy(null);
        AiOutboundCallee callee = callee();
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));

        dialer.dialCallee(task, callee);

        DialRequest request = captureDialRequest();
        assertThat(request.getCalleeNumber()).isEqualTo("13800138000");
        assertThat(request.getCallerNumber()).isEqualTo("057100000000");
        assertThat(request.getTaskId()).isEqualTo(TASK_ID);
        assertThat(request.getCalleeId()).isEqualTo(CALLEE_ID);
        assertThat(request.getPriority()).isEqualTo(100);
        assertThat(request.getAnswerAction()).isEqualTo("BRIDGE_AGENT");
        assertThat(request.getIvrFlowId()).isNull();
        assertThat(request.isEnableRecord()).isTrue();
        assertThat(request.getCreateBy()).isEqualTo("outbound-executor");
        assertThat(request.getRemark()).isEqualTo("外呼任务:T-001");
    }

    @Test
    void usesIvrAnswerActionWhenFlowConfigured()
    {
        ReflectionTestUtils.setField(dialer, "simulateAnswer", false);
        AiOutboundTask task = task();
        task.setIvrFlowId(9L);
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));

        dialer.dialCallee(task, callee());

        DialRequest request = captureDialRequest();
        assertThat(request.getAnswerAction()).isEqualTo("IVR");
        assertThat(request.getIvrFlowId()).isEqualTo(9L);
    }

    // ---------- 模拟接通 ----------

    @Test
    void simulatedAnswerFinalizesCalleeAndCounts()
    {
        AiOutboundTask task = task();
        AiOutboundCallee callee = callee();
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));
        stubCallRecord(88L);

        dialer.dialCallee(task, callee);

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCalleeId()).isEqualTo(CALLEE_ID);
        assertThat(upd.getValue().getCallStatus()).isEqualTo("5");
        assertThat(upd.getValue().getRecordId()).isEqualTo(88L);
        assertThat(upd.getValue().getCallDuration()).isEqualTo(45);
        assertThat(upd.getValue().getUpdateBy()).isEqualTo("outbound-executor");

        verify(finalizer).createOutboundResult(eq(task), eq(callee), eq(88L), eq("1"),
                any(Date.class), any(Date.class), eq(45), isNull());
        verify(taskMapper).incrementAnsweredCount(TASK_ID);
        verify(taskMapper).incrementCompletedCount(TASK_ID);
        verify(finalizer).maybeCreateTicket(task, callee, 88L, null);
        verify(finalizer, never()).updateRecordWithFlow(anyLong(), any());
        verify(metrics).incrementOutbound("dial");
        verify(metrics).incrementOutbound("answer");
    }

    @Test
    void simulatedAnswerClampsNegativeTalkSeconds()
    {
        ReflectionTestUtils.setField(dialer, "simulateTalkSeconds", -1);
        AiOutboundTask task = task();
        AiOutboundCallee callee = callee();
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));
        stubCallRecord(88L);

        dialer.dialCallee(task, callee);

        verify(finalizer).createOutboundResult(eq(task), eq(callee), eq(88L), eq("1"),
                any(Date.class), any(Date.class), eq(0), isNull());
    }

    @Test
    void waitsForGatewayEventWhenSimulationDisabled()
    {
        ReflectionTestUtils.setField(dialer, "simulateAnswer", false);
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));

        dialer.dialCallee(task(), callee());

        verify(finalizer, never()).createCallRecord(anyLong(), any());
        verify(metrics).incrementOutbound("dial");
        verify(metrics, never()).incrementOutbound("answer");
    }

    @Test
    void worksWithoutMetrics()
    {
        ReflectionTestUtils.setField(dialer, "metrics", null);
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));
        stubCallRecord(88L);

        dialer.dialCallee(task(), callee());

        verify(taskMapper).incrementAnsweredCount(TASK_ID);
    }

    // ---------- 下发失败 ----------

    @Test
    void retryableFailureGoesBackToPendingWithBackoff()
    {
        AiOutboundTask task = task();
        task.setRetryCount(2);
        AiOutboundCallee callee = callee();
        callee.setRetryTimes(0);
        when(callDispatchService.dialWithQueue(any(DialRequest.class)))
                .thenReturn(DialResult.fail("GATEWAY_ERROR", "gw down"));

        dialer.dialCallee(task, callee);

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getCallStatus()).isEqualTo("0");
        assertThat(upd.getValue().getRetryTimes()).isEqualTo(1);
        assertThat(upd.getValue().getFailReason()).isEqualTo("gw down");
        verify(calleeMapper).updateNextRetryTime(eq(CALLEE_ID), any(Date.class), eq("outbound-executor"));
        verify(metrics).incrementOutbound("retry");
        verify(finalizer, never()).markCalleeFailed(any(), any(), any());
    }

    @Test
    void retryReasonIsTruncatedToColumnWidth()
    {
        AiOutboundTask task = task();
        task.setRetryCount(2);
        AiOutboundCallee callee = callee();
        String longReason = repeat('x', 600);
        when(callDispatchService.dialWithQueue(any(DialRequest.class)))
                .thenReturn(DialResult.fail("QUEUE_FULL", longReason));

        dialer.dialCallee(task, callee);

        ArgumentCaptor<AiOutboundCallee> upd = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(upd.capture());
        assertThat(upd.getValue().getFailReason()).hasSize(500);
    }

    @Test
    void nonRetryableFailureIsMarkedFailed()
    {
        AiOutboundTask task = task();
        task.setRetryCount(2);
        AiOutboundCallee callee = callee();
        when(callDispatchService.dialWithQueue(any(DialRequest.class)))
                .thenReturn(DialResult.fail("INVALID_NUMBER", "空号"));

        dialer.dialCallee(task, callee);

        verify(finalizer).markCalleeFailed(task, callee, "空号");
        verify(calleeMapper, never()).updateNextRetryTime(any(), any(), any());
        verify(metrics, never()).incrementOutbound("retry");
    }

    @Test
    void exhaustedRetriesIsMarkedFailed()
    {
        AiOutboundTask task = task();
        task.setRetryCount(2);
        AiOutboundCallee callee = callee();
        callee.setRetryTimes(2);
        when(callDispatchService.dialWithQueue(any(DialRequest.class)))
                .thenReturn(DialResult.fail("GATEWAY_ERROR", "gw down"));

        dialer.dialCallee(task, callee);

        verify(finalizer).markCalleeFailed(task, callee, "gw down");
        verify(calleeMapper, never()).updateCalleeStatus(any());
    }

    // ---------- IVR 流程 ----------

    @Test
    void runsIvrFlowWithSampleInputAndVariables()
    {
        AiOutboundTask task = task();
        task.setIvrFlowId(5L);
        AiOutboundCallee callee = callee();
        callee.setCalleeName("张三");
        callee.setCalleeParams("{\"input\":\"我要投诉欠薪\"}");
        IvrExecuteResult flowResult = new IvrExecuteResult();
        flowResult.setMatchedIntentionName("欠薪投诉");
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));
        stubCallRecord(88L);
        when(ivrEngineService.executeFlow(any(IvrExecuteRequest.class))).thenReturn(flowResult);

        dialer.dialCallee(task, callee);

        ArgumentCaptor<IvrExecuteRequest> request = ArgumentCaptor.forClass(IvrExecuteRequest.class);
        verify(ivrEngineService).executeFlow(request.capture());
        assertThat(request.getValue().getFlowId()).isEqualTo(5L);
        assertThat(request.getValue().getRecordId()).isEqualTo(88L);
        assertThat(request.getValue().getCallerNumber()).isEqualTo("057100000000");
        assertThat(request.getValue().getCalleeNumber()).isEqualTo("13800138000");
        assertThat(request.getValue().getInputs()).containsExactly("我要投诉欠薪");
        assertThat(request.getValue().getVariables())
                .containsEntry("taskId", TASK_ID)
                .containsEntry("taskNo", "T-001")
                .containsEntry("taskName", "欠薪回访")
                .containsEntry("calleeName", "张三");
        verify(finalizer).updateRecordWithFlow(88L, flowResult);
        verify(finalizer).maybeCreateTicket(task, callee, 88L, flowResult);
        verify(metrics).incrementOutbound("answer");
    }

    @Test
    void sampleInputFallsBackToCalleeName()
    {
        AiOutboundTask task = task();
        task.setIvrFlowId(5L);
        AiOutboundCallee callee = callee();
        callee.setCalleeName("李四");
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));
        stubCallRecord(88L);

        dialer.dialCallee(task, callee);

        ArgumentCaptor<IvrExecuteRequest> request = ArgumentCaptor.forClass(IvrExecuteRequest.class);
        verify(ivrEngineService).executeFlow(request.capture());
        assertThat(request.getValue().getInputs()).containsExactly("我想咨询李四相关的法律问题");
    }

    @Test
    void sampleInputFallsBackToDefaultScript()
    {
        AiOutboundTask task = task();
        task.setIvrFlowId(5L);
        AiOutboundCallee callee = callee();
        callee.setCalleeParams("not-json");
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));
        stubCallRecord(88L);

        dialer.dialCallee(task, callee);

        ArgumentCaptor<IvrExecuteRequest> request = ArgumentCaptor.forClass(IvrExecuteRequest.class);
        verify(ivrEngineService).executeFlow(request.capture());
        assertThat(request.getValue().getInputs()).containsExactly("我想咨询一下法律问题，比如合同纠纷");
    }

    @Test
    void ivrFailureDegradesToNullFlow()
    {
        AiOutboundTask task = task();
        task.setIvrFlowId(5L);
        AiOutboundCallee callee = callee();
        when(callDispatchService.dialWithQueue(any(DialRequest.class))).thenReturn(DialResult.ok("uuid-1"));
        stubCallRecord(88L);
        when(ivrEngineService.executeFlow(any(IvrExecuteRequest.class)))
                .thenThrow(new IllegalStateException("ivr down"));

        dialer.dialCallee(task, callee);

        verify(finalizer, never()).updateRecordWithFlow(anyLong(), any());
        verify(finalizer).maybeCreateTicket(task, callee, 88L, null);
        verify(finalizer).createOutboundResult(eq(task), eq(callee), eq(88L), eq("1"),
                any(Date.class), any(Date.class), eq(45), isNull());
    }

    private void stubCallRecord(long recordId)
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(recordId);
        when(finalizer.createCallRecord(anyLong(), any(AiOutboundCallee.class))).thenReturn(record);
    }

    private DialRequest captureDialRequest()
    {
        ArgumentCaptor<DialRequest> captor = ArgumentCaptor.forClass(DialRequest.class);
        verify(callDispatchService).dialWithQueue(captor.capture());
        return captor.getValue();
    }

    private static AiOutboundTask task()
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(TASK_ID);
        task.setTaskNo("T-001");
        task.setTaskName("欠薪回访");
        task.setCallerNumber("057100000000");
        task.setPriority(50);
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
        callee.setCallStatus("1");
        callee.setRetryTimes(0);
        return callee;
    }

    private static AiHotspotSuppress suppress(String action)
    {
        AiHotspotSuppress suppress = new AiHotspotSuppress();
        suppress.setRuleName("骚扰规则");
        suppress.setAction(action);
        suppress.setPriorityLevel(20);
        return suppress;
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
