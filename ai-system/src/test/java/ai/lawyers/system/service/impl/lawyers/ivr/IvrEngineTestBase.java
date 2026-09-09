package ai.lawyers.system.service.impl.lawyers.ivr;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.IvrEngineServiceImpl;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrEdgeService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrExecutionLogService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W3 IVR 引擎测试装配基类：注入 mock 的流程/节点/连线/执行日志服务，
 * 流程执行不触达 DB（执行日志 execId 为 null 不影响主链路，话单回写因 recordId 为空短路）。
 *
 * @author ai-lawyers
 */
abstract class IvrEngineTestBase
{
    protected IAiIvrFlowService flowService;
    protected IAiIvrNodeService nodeService;
    protected IAiIvrEdgeService edgeService;
    protected IAiIvrExecutionLogService executionLogService;
    protected IvrEngineServiceImpl engine;

    @BeforeEach
    void setUpEngine()
    {
        flowService = mock(IAiIvrFlowService.class);
        nodeService = mock(IAiIvrNodeService.class);
        edgeService = mock(IAiIvrEdgeService.class);
        executionLogService = mock(IAiIvrExecutionLogService.class);

        engine = new IvrEngineServiceImpl();
        setField("flowService", flowService);
        setField("nodeService", nodeService);
        setField("edgeService", edgeService);
        setField("executionLogService", executionLogService);
        setField("flowTimeoutMs", 180000L);
    }

    /** 注册一个流程及其节点/连线（子流程按 flowId 各自注册） */
    protected void registerFlow(AiIvrFlow flow, List<AiIvrNode> nodes, List<AiIvrEdge> edges)
    {
        when(flowService.selectAiIvrFlowByFlowId(flow.getFlowId())).thenReturn(flow);
        when(nodeService.selectAiIvrNodeByFlowId(flow.getFlowId())).thenReturn(nodes);
        when(edgeService.selectAiIvrEdgeByFlowId(flow.getFlowId())).thenReturn(edges);
    }

    protected AiIvrFlow flow(long flowId, String name)
    {
        AiIvrFlow f = new AiIvrFlow();
        f.setFlowId(flowId);
        f.setFlowName(name);
        f.setStatus("1");
        return f;
    }

    protected AiIvrNode node(long nodeId, long flowId, String type, String name, String configJson)
    {
        AiIvrNode n = new AiIvrNode();
        n.setNodeId(nodeId);
        n.setFlowId(flowId);
        n.setNodeType(type);
        n.setNodeName(name);
        n.setNodeConfig(configJson);
        return n;
    }

    /** 连线：label/conditionExpr 可为 null（null 即默认连线） */
    protected AiIvrEdge edge(long edgeId, long flowId, long from, long to, String label, String conditionExpr)
    {
        AiIvrEdge e = new AiIvrEdge();
        e.setEdgeId(edgeId);
        e.setFlowId(flowId);
        e.setSourceNodeId(from);
        e.setTargetNodeId(to);
        e.setEdgeLabel(label);
        e.setConditionExpr(conditionExpr);
        e.setSortOrder((int) edgeId);
        return e;
    }

    protected List<AiIvrNode> nodes(AiIvrNode... array)
    {
        return new ArrayList<>(java.util.Arrays.asList(array));
    }

    protected List<AiIvrEdge> edges(AiIvrEdge... array)
    {
        return new ArrayList<>(java.util.Arrays.asList(array));
    }

    protected IvrExecuteResult run(AiIvrFlow flow, String... inputs)
    {
        IvrExecuteRequest request = new IvrExecuteRequest();
        request.setFlowId(flow.getFlowId());
        request.setSessionId("IVR-TEST");
        request.setCallerNumber("13800001234");
        request.setCalleeNumber("075512345678");
        for (String input : inputs)
        {
            request.getInputs().add(input);
        }
        return engine.executeFlow(request);
    }

    protected void setField(String name, Object value)
    {
        try
        {
            Field f = IvrEngineServiceImpl.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(engine, value);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("注入字段失败: " + name, e);
        }
    }
}
