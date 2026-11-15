package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * hangup 挂断节点单测：步骤动作/描述、终止标记与流程终止。
 *
 * @author ai-lawyers
 */
class HangupNodeExecutorTest
{
    private HangupNodeExecutor executor;

    @BeforeEach
    void setUp()
    {
        executor = new HangupNodeExecutor();
    }

    @Test
    void supportedTypesOnlyHangup()
    {
        assertThat(HangupNodeExecutor.NODE_TYPE).isEqualTo("hangup");
        assertThat(executor.supportedTypes()).containsExactly("hangup");
    }

    @Test
    void executeMarksHangupStepAndTermination()
    {
        Map<String, Object> vars = new HashMap<>();
        IvrNodeContext ctx = context(node("挂断节点"), vars);

        AiIvrNode next = executor.execute(ctx);

        assertThat(ctx.getStep().getAction()).isEqualTo("HANGUP");
        assertThat(ctx.getStep().getDetail()).isEqualTo("挂断（挂断节点）");
        assertThat(vars).containsEntry("__terminated", "hangup");
        assertThat(ctx.getResult().getSteps()).containsExactly(ctx.getStep());
        assertThat(next).isNull();
    }

    @Test
    void detailOmitsBracketsWhenNodeNameMissing()
    {
        IvrNodeContext ctx = context(node(null), new HashMap<>());

        executor.execute(ctx);

        assertThat(ctx.getStep().getDetail()).isEqualTo("挂断");
    }

    @Test
    void detailOmitsBracketsWhenNodeNameBlank()
    {
        IvrNodeContext ctx = context(node("  "), new HashMap<>());

        executor.execute(ctx);

        assertThat(ctx.getStep().getDetail()).isEqualTo("挂断");
    }

    @Test
    void terminationFlagOverwritesExistingValue()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("__terminated", "previous");
        IvrNodeContext ctx = context(node("结束"), vars);

        executor.execute(ctx);

        assertThat(vars).containsEntry("__terminated", "hangup");
    }

    private IvrNodeContext context(AiIvrNode node, Map<String, Object> vars)
    {
        IvrNodeContext ctx = new IvrNodeContext(new HashMap<>(), Collections.emptyList(), vars,
                new ArrayDeque<>(), new IvrExecuteRequest(), "IVR-T", new AiIvrFlow(),
                new IvrExecuteResult(), r -> null);
        ctx.setCurrent(node);
        ctx.setStep(new IvrNodeStep());
        return ctx;
    }

    private AiIvrNode node(String nodeName)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setFlowId(10L);
        node.setNodeType(HangupNodeExecutor.NODE_TYPE);
        node.setNodeName(nodeName);
        return node;
    }
}
