package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * IVR 节点执行上下文：一次流程执行的共享状态载体。
 *
 * <p>引擎主循环每步设置 {@link #setCurrent} / {@link #setStep} 后分发给执行器；
 * 执行器就地修改 variables / result / inputs（与原 switch 分支语义一致）。</p>
 *
 * @author ai-lawyers
 */
public class IvrNodeContext
{
    /** 当前节点（引擎每步重置） */
    private AiIvrNode current;
    /** 当前步骤（引擎每步新建并重置） */
    private IvrNodeStep step;

    private final Map<Long, AiIvrNode> nodeMap;
    private final List<AiIvrEdge> edges;
    private final Map<String, Object> variables;
    private final Deque<String> inputs;
    private final IvrExecuteRequest request;
    private final String sessionId;
    private final AiIvrFlow flow;
    private final IvrExecuteResult result;

    /** 子流程执行入口：引擎以 this::executeFlow 提供，避免执行器反向依赖引擎 Bean 成环 */
    private final Function<IvrExecuteRequest, IvrExecuteResult> flowRunner;

    public IvrNodeContext(Map<Long, AiIvrNode> nodeMap, List<AiIvrEdge> edges,
                          Map<String, Object> variables, Deque<String> inputs,
                          IvrExecuteRequest request, String sessionId, AiIvrFlow flow,
                          IvrExecuteResult result,
                          Function<IvrExecuteRequest, IvrExecuteResult> flowRunner)
    {
        this.nodeMap = nodeMap;
        this.edges = edges;
        this.variables = variables;
        this.inputs = inputs;
        this.request = request;
        this.sessionId = sessionId;
        this.flow = flow;
        this.result = result;
        this.flowRunner = flowRunner;
    }

    public AiIvrNode getCurrent()
    {
        return current;
    }

    public void setCurrent(AiIvrNode current)
    {
        this.current = current;
    }

    public IvrNodeStep getStep()
    {
        return step;
    }

    public void setStep(IvrNodeStep step)
    {
        this.step = step;
    }

    public Map<Long, AiIvrNode> getNodeMap()
    {
        return nodeMap;
    }

    public List<AiIvrEdge> getEdges()
    {
        return edges;
    }

    public Map<String, Object> getVariables()
    {
        return variables;
    }

    public Deque<String> getInputs()
    {
        return inputs;
    }

    public IvrExecuteRequest getRequest()
    {
        return request;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public AiIvrFlow getFlow()
    {
        return flow;
    }

    public IvrExecuteResult getResult()
    {
        return result;
    }

    public Function<IvrExecuteRequest, IvrExecuteResult> getFlowRunner()
    {
        return flowRunner;
    }
}
