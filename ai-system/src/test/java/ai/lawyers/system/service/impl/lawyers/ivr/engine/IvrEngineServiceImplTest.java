package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrExecutionLog;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.node.HangupNodeExecutor;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.node.IvrNodeContext;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.node.IvrNodeExecutor;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.node.StartNodeExecutor;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrEdgeService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrExecutionLogService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;

/**
 * IVR 流程执行引擎门面单测：流程装载失败分支、会话与执行日志、节点分发异常、
 * 通关回写通话记录与执行日志结束回写。
 *
 * @author ai-lawyers
 */
class IvrEngineServiceImplTest
{
    private IvrEngineServiceImpl engine;
    private IAiIvrFlowService flowService;
    private IAiIvrNodeService nodeService;
    private IAiIvrEdgeService edgeService;
    private IAiIvrExecutionLogService executionLogService;
    private IAiCallRecordService callRecordService;

    @BeforeEach
    void setUp()
    {
        engine = new IvrEngineServiceImpl();
        flowService = mock(IAiIvrFlowService.class);
        nodeService = mock(IAiIvrNodeService.class);
        edgeService = mock(IAiIvrEdgeService.class);
        executionLogService = mock(IAiIvrExecutionLogService.class);
        callRecordService = mock(IAiCallRecordService.class);

        ReflectionTestUtils.setField(engine, "flowService", flowService);
        ReflectionTestUtils.setField(engine, "nodeService", nodeService);
        ReflectionTestUtils.setField(engine, "edgeService", edgeService);
        ReflectionTestUtils.setField(engine, "executionLogService", executionLogService);
        ReflectionTestUtils.setField(engine, "callRecordService", callRecordService);
        ReflectionTestUtils.setField(engine, "flowTimeoutMs", 180000L);
        ReflectionTestUtils.setField(engine, "logFlushIntervalMs", 60000L);
        ReflectionTestUtils.setField(engine, "nodeExecutors",
                Arrays.asList(new StartNodeExecutor(), new HangupNodeExecutor()));

        // 插入执行日志时回填 execId，模拟自增主键
        doAnswer(inv -> {
            AiIvrExecutionLog log = inv.getArgument(0);
            log.setExecId(77L);
            return 1;
        }).when(executionLogService).insertAiIvrExecutionLog(any());
        when(executionLogService.updateAiIvrExecutionLog(any())).thenReturn(1);
    }

    @Test
    void executeFlow_flowNotFoundWithId()
    {
        when(flowService.selectAiIvrFlowByFlowId(5L)).thenReturn(null);

        IvrExecuteResult result = engine.executeFlow(request(5L, null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("FLOW_NOT_FOUND");
        assertThat(result.getMessage()).contains("flowId=5");
        assertThat(result.getStatus()).isEqualTo("2");
        verify(executionLogService, never()).insertAiIvrExecutionLog(any());
    }

    @Test
    void executeFlow_defaultFlowNotFound()
    {
        when(flowService.selectDefaultFlow()).thenReturn(null);

        IvrExecuteResult result = engine.executeFlow(request(null, null));

        assertThat(result.getCode()).isEqualTo("FLOW_NOT_FOUND");
        assertThat(result.getMessage()).contains("默认流程不存在");
    }

    @Test
    void executeFlow_flowWithoutNodes()
    {
        AiIvrFlow flow = flow(1000L, "空流程");
        registerFlow(flow, Collections.emptyList(), Collections.emptyList());

        IvrExecuteResult result = engine.executeFlow(request(1000L, null));

        assertThat(result.getCode()).isEqualTo("FLOW_EMPTY");
        assertThat(result.getMessage()).contains("空流程");
    }

    @Test
    void executeFlow_missingStartNode()
    {
        AiIvrFlow flow = flow(1000L, "无开始节点");
        registerFlow(flow, Collections.singletonList(node(101L, 1000L, "say", "播报", null)),
                Collections.emptyList());

        IvrExecuteResult result = engine.executeFlow(request(1000L, null));

        assertThat(result.getCode()).isEqualTo("NO_START_NODE");
    }

    @Test
    void executeFlow_successBuildsResultAndLog()
    {
        AiIvrFlow flow = flow(1000L, "正常流程");
        registerFlow(flow,
                Arrays.asList(node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "hangup", "挂断", null)),
                Collections.singletonList(edge(1L, 1000L, 101L, 102L)));

        IvrExecuteRequest request = request(1000L, "IVR-TEST");
        request.setRecordId(88L);
        IvrExecuteResult result = engine.executeFlow(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo("OK");
        assertThat(result.getStatus()).isEqualTo("1");
        assertThat(result.getExecId()).isEqualTo(77L);
        assertThat(result.getFlowId()).isEqualTo(1000L);
        assertThat(result.getFlowName()).isEqualTo("正常流程");
        assertThat(result.getRecordId()).isEqualTo(88L);
        assertThat(result.getSessionId()).isEqualTo("IVR-TEST");
        assertThat(result.getSteps()).extracting(s -> s.getAction())
                .containsExactly("START", "HANGUP");

        ArgumentCaptor<AiIvrExecutionLog> inserted = ArgumentCaptor.forClass(AiIvrExecutionLog.class);
        verify(executionLogService).insertAiIvrExecutionLog(inserted.capture());
        assertThat(inserted.getValue().getRecordId()).isEqualTo(88L);
        assertThat(inserted.getValue().getFlowId()).isEqualTo(1000L);
        assertThat(inserted.getValue().getFlowName()).isEqualTo("正常流程");
        assertThat(inserted.getValue().getSessionId()).isEqualTo("IVR-TEST");
        assertThat(inserted.getValue().getStatus()).isEqualTo("0");
        assertThat(inserted.getValue().getStartTime()).isNotNull();

        // 结束回写：成功状态、无错误信息
        ArgumentCaptor<AiIvrExecutionLog> updates = ArgumentCaptor.forClass(AiIvrExecutionLog.class);
        verify(executionLogService, org.mockito.Mockito.atLeastOnce())
                .updateAiIvrExecutionLog(updates.capture());
        AiIvrExecutionLog finish = updates.getAllValues().stream()
                .filter(u -> "1".equals(u.getStatus()))
                .findFirst().orElse(null);
        assertThat(finish).isNotNull();
        assertThat(finish.getEndTime()).isNotNull();
        assertThat(finish.getErrorMsg()).isNull();
        assertThat(finish.getExecuteResult()).contains("HANGUP");
        assertThat(finish.getVariables()).contains("callerNumber");
    }

    @Test
    void executeFlow_generatesSessionIdWhenAbsent()
    {
        AiIvrFlow flow = flow(1000L, "默认会话");
        registerFlow(flow,
                Arrays.asList(node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "hangup", "挂断", null)),
                Collections.singletonList(edge(1L, 1000L, 101L, 102L)));

        IvrExecuteResult result = engine.executeFlow(request(1000L, null));

        assertThat(result.getSessionId()).startsWith("IVR-").hasSize(4 + 32);
    }

    @Test
    void executeFlow_mergesRequestVariables()
    {
        AiIvrFlow flow = flow(1000L, "变量流程");
        registerFlow(flow,
                Arrays.asList(node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "hangup", "挂断", null)),
                Collections.singletonList(edge(1L, 1000L, 101L, 102L)));

        IvrExecuteRequest request = request(1000L, "IVR-TEST");
        request.setCallerNumber("13800001234");
        request.setCalleeNumber("075512345678");
        request.getVariables().put("scene", "test");
        IvrExecuteResult result = engine.executeFlow(request);

        Map<String, Object> vars = result.getVariables();
        assertThat(vars).containsEntry("scene", "test")
                .containsEntry("callerNumber", "13800001234")
                .containsEntry("calleeNumber", "075512345678");
    }

    @Test
    void executeFlow_unknownNodeType()
    {
        AiIvrFlow flow = flow(1000L, "未知节点");
        registerFlow(flow,
                Arrays.asList(node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "unknownX", "神秘节点", null)),
                Collections.singletonList(edge(1L, 1000L, 101L, 102L)));

        IvrExecuteResult result = engine.executeFlow(request(1000L, "IVR-TEST"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("UNSUPPORTED_NODE");
        assertThat(result.getStatus()).isEqualTo("2");
        assertThat(result.getMessage()).contains("unknownx");
        assertThat(result.getCurrentNodeId()).isEqualTo(102L);
        assertThat(result.getCurrentNodeType()).isEqualTo("unknownx");
        assertThat(result.getCurrentNodeName()).isEqualTo("神秘节点");
        assertThat(result.getSteps()).extracting(s -> s.getAction())
                .containsExactly("START", "ERROR");

        ArgumentCaptor<AiIvrExecutionLog> updates = ArgumentCaptor.forClass(AiIvrExecutionLog.class);
        verify(executionLogService, org.mockito.Mockito.atLeastOnce())
                .updateAiIvrExecutionLog(updates.capture());
        assertThat(updates.getAllValues()).anyMatch(u -> "2".equals(u.getStatus())
                && u.getErrorMsg() != null && u.getErrorMsg().contains("未知节点类型"));
    }

    @Test
    void executeFlow_finishLogFailureDoesNotBreakFlow()
    {
        AiIvrFlow flow = flow(1000L, "日志异常");
        registerFlow(flow,
                Arrays.asList(node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "hangup", "挂断", null)),
                Collections.singletonList(edge(1L, 1000L, 101L, 102L)));
        when(executionLogService.updateAiIvrExecutionLog(any()))
                .thenThrow(new RuntimeException("日志表不可用"));

        IvrExecuteResult result = engine.executeFlow(request(1000L, "IVR-TEST"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo("OK");
    }

    @Test
    void resolveFlowForCaller_usesDefaultFlow()
    {
        AiIvrFlow flow = flow(1000L, "默认流程");
        when(flowService.selectDefaultFlow()).thenReturn(flow);

        assertThat(engine.resolveFlowForCaller("13800001234")).isSameAs(flow);
    }

    @Test
    void updateRecord_skipsWhenNoRecordId()
    {
        runTerminalFlow(new ResultNodeExecutor(), null);

        verify(callRecordService, never()).selectAiCallRecordByRecordId(any());
        verify(callRecordService, never()).updateAiCallRecord(any());
    }

    @Test
    void updateRecord_skipsWhenRecordMissing()
    {
        when(callRecordService.selectAiCallRecordByRecordId(88L)).thenReturn(null);

        runTerminalFlow(new ResultNodeExecutor(), 88L);

        verify(callRecordService, never()).updateAiCallRecord(any());
    }

    @Test
    void updateRecord_writesCategoryTransferAndRemark()
    {
        when(callRecordService.selectAiCallRecordByRecordId(88L)).thenReturn(record("旧备注", null));

        runTerminalFlow(new ResultNodeExecutor(), 88L);

        ArgumentCaptor<AiCallRecord> captor = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callRecordService).updateAiCallRecord(captor.capture());
        AiCallRecord update = captor.getValue();
        assertThat(update.getRecordId()).isEqualTo(88L);
        assertThat(update.getCategoryId()).isEqualTo(55L);
        assertThat(update.getStatus()).isEqualTo("2");
        assertThat(update.getRemark()).startsWith("旧备注；【IVR】流程[通关流程]")
                .contains("意图=劳动纠纷(I1)")
                .contains("转接=8001")
                .contains("节点数=1");
    }

    @Test
    void updateRecord_keepsExistingCategory()
    {
        when(callRecordService.selectAiCallRecordByRecordId(88L)).thenReturn(record("", 66L));

        runTerminalFlow(new ResultNodeExecutor(), 88L);

        ArgumentCaptor<AiCallRecord> captor = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callRecordService).updateAiCallRecord(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isNull();
        assertThat(captor.getValue().getRemark()).startsWith("【IVR】流程[通关流程]");
    }

    @Test
    void updateRecord_truncatesRemarkTo500()
    {
        StringBuilder longRemark = new StringBuilder();
        for (int i = 0; i < 600; i++)
        {
            longRemark.append('x');
        }
        when(callRecordService.selectAiCallRecordByRecordId(88L)).thenReturn(record(longRemark.toString(), null));

        runTerminalFlow(new ResultNodeExecutor(), 88L);

        ArgumentCaptor<AiCallRecord> captor = ArgumentCaptor.forClass(AiCallRecord.class);
        verify(callRecordService).updateAiCallRecord(captor.capture());
        assertThat(captor.getValue().getRemark()).hasSize(500);
    }

    @Test
    void updateRecord_exceptionSwallowed()
    {
        when(callRecordService.selectAiCallRecordByRecordId(88L))
                .thenThrow(new RuntimeException("话单库不可用"));

        IvrExecuteResult result = runTerminalFlow(new ResultNodeExecutor(), 88L);

        assertThat(result.isSuccess()).isTrue();
        verify(callRecordService, never()).updateAiCallRecord(any());
    }

    @Test
    void executorRegistry_duplicateTypeThrows()
    {
        ReflectionTestUtils.setField(engine, "nodeExecutors",
                Arrays.asList(executor("say"), executor("say")));

        assertThrows(IllegalStateException.class, engine::initExecutorRegistry);
    }

    /** 用自定义终止执行器跑一条 start → 自定义节点 的流程 */
    private IvrExecuteResult runTerminalFlow(ResultNodeExecutor custom, Long recordId)
    {
        ReflectionTestUtils.setField(engine, "nodeExecutors",
                Arrays.asList(new StartNodeExecutor(), custom));
        AiIvrFlow flow = flow(1000L, "通关流程");
        registerFlow(flow,
                Arrays.asList(node(101L, 1000L, "start", "开始", null),
                        node(102L, 1000L, "agent", "自定义", null)),
                Collections.singletonList(edge(1L, 1000L, 101L, 102L)));
        IvrExecuteRequest request = request(1000L, "IVR-TEST");
        request.setRecordId(recordId);
        return engine.executeFlow(request);
    }

    private IvrExecuteResult runTerminalFlow(ResultNodeExecutor custom)
    {
        return runTerminalFlow(custom, null);
    }

    private IvrExecuteRequest request(Long flowId, String sessionId)
    {
        IvrExecuteRequest request = new IvrExecuteRequest();
        request.setFlowId(flowId);
        request.setSessionId(sessionId);
        request.setCallerNumber("13800001234");
        request.setCalleeNumber("075512345678");
        return request;
    }

    private AiCallRecord record(String remark, Long categoryId)
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(88L);
        record.setRemark(remark);
        record.setCategoryId(categoryId);
        return record;
    }

    private void registerFlow(AiIvrFlow flow, List<AiIvrNode> nodes, List<AiIvrEdge> edges)
    {
        when(flowService.selectAiIvrFlowByFlowId(flow.getFlowId())).thenReturn(flow);
        when(nodeService.selectAiIvrNodeByFlowId(flow.getFlowId())).thenReturn(nodes);
        when(edgeService.selectAiIvrEdgeByFlowId(flow.getFlowId())).thenReturn(edges);
    }

    private AiIvrFlow flow(long flowId, String name)
    {
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(flowId);
        flow.setFlowName(name);
        flow.setStatus("1");
        return flow;
    }

    private AiIvrNode node(long nodeId, long flowId, String type, String name, String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(nodeId);
        node.setFlowId(flowId);
        node.setNodeType(type);
        node.setNodeName(name);
        node.setNodeConfig(config);
        return node;
    }

    private AiIvrEdge edge(long edgeId, long flowId, long from, long to)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(edgeId);
        edge.setFlowId(flowId);
        edge.setSourceNodeId(from);
        edge.setTargetNodeId(to);
        edge.setSortOrder((int) edgeId);
        return edge;
    }

    private IvrNodeExecutor executor(String type)
    {
        return new IvrNodeExecutor()
        {
            @Override
            public String[] supportedTypes()
            {
                return new String[] { type };
            }

            @Override
            public AiIvrNode execute(IvrNodeContext ctx)
            {
                return null;
            }
        };
    }

    /** 自定义终止执行器：写入意图/分类/转接目标后终止流程 */
    private static class ResultNodeExecutor implements IvrNodeExecutor
    {
        @Override
        public String[] supportedTypes()
        {
            return new String[] { "agent" };
        }

        @Override
        public AiIvrNode execute(IvrNodeContext ctx)
        {
            IvrExecuteResult result = ctx.getResult();
            result.setCategoryId(55L);
            result.setTransferTarget("8001");
            result.setMatchedIntention("I1");
            result.setMatchedIntentionName("劳动纠纷");
            return null;
        }
    }
}
