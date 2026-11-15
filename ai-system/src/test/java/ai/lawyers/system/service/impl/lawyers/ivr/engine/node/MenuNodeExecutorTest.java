package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;

/**
 * T1-4：{@link MenuNodeExecutor} 按键/语音菜单节点执行器测试。
 *
 * <p>覆盖默认与自定义结束键剥离、maxDigits 多位按键截断（默认单位）、
 * 空输入与空白规整、dtmf/lastInput 变量写入以及按按键标签路由与默认连线回退。</p>
 *
 * @author ai-lawyers
 */
class MenuNodeExecutorTest
{
    private final MenuNodeExecutor executor = new MenuNodeExecutor();

    @Test
    void supportsMenuAndDtmfNodes()
    {
        assertThat(executor.supportedTypes())
                .containsExactly(MenuNodeExecutor.NODE_MENU, MenuNodeExecutor.NODE_DTMF);
    }

    @Test
    void stripsDefaultHashEndKey()
    {
        IvrNodeContext ctx = context(node("{}"), "1#");

        executor.execute(ctx);

        assertThat(ctx.getVariables())
                .containsEntry("dtmf", "1")
                .containsEntry("lastInput", "1#");
        assertThat(ctx.getStep().getAction()).isEqualTo("INPUT");
        assertThat(ctx.getStep().getDetail()).isEqualTo("按键/语音输入：1");
    }

    @Test
    void emptyInputBecomesBlankDtmf()
    {
        IvrNodeContext ctx = context(node("{}"), null);

        executor.execute(ctx);

        assertThat(ctx.getVariables())
                .containsEntry("dtmf", "")
                .containsEntry("lastInput", "");
    }

    @Test
    void trimsWhitespaceInput()
    {
        IvrNodeContext ctx = context(node("{}"), "  2 ");

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("dtmf", "2");
    }

    @Test
    void stripsCustomEndKey()
    {
        IvrNodeContext ctx = context(node("{\"endKey\":\"*\"}"), "3*");

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("dtmf", "3");
    }

    @Test
    void stripsLegacyEndAlias()
    {
        IvrNodeContext ctx = context(node("{\"end\":\"#\"}"), "4#");

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("dtmf", "4");
    }

    @Test
    void truncatesToSingleDigitByDefault()
    {
        IvrNodeContext ctx = context(node("{}"), "12");

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("dtmf", "1");
    }

    @Test
    void supportsMultiDigitMenu()
    {
        IvrNodeContext ctx = context(node("{\"maxDigits\":3}"), "12345#");

        executor.execute(ctx);

        assertThat(ctx.getVariables()).containsEntry("dtmf", "123");
        assertThat(ctx.getVariables()).containsEntry("lastInput", "12345#");
    }

    @Test
    void routesToMatchingEdgeLabel()
    {
        IvrNodeContext ctx = context(node("{}"), "1");

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(2L));
    }

    @Test
    void fallsBackToDefaultEdgeWhenNoLabelMatches()
    {
        IvrNodeContext ctx = context(node("{}"), "9");

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(3L));
    }

    @Test
    void deletedInputStillRoutesDefaultEdge()
    {
        IvrNodeContext ctx = context(node("{}"), "");

        AiIvrNode next = executor.execute(ctx);

        assertThat(next).isSameAs(ctx.getNodeMap().get(3L));
    }

    private IvrNodeContext context(AiIvrNode current, String input)
    {
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        nodeMap.put(1L, current);
        nodeMap.put(2L, node("say", "{}"));
        nodeMap.put(3L, node("say", "{}"));
        List<AiIvrEdge> edges = new ArrayList<>();
        edges.add(edge(1L, 2L, "1"));
        edges.add(edge(1L, 3L, null));
        ArrayDeque<String> inputs = new ArrayDeque<>();
        if (input != null)
        {
            inputs.add(input);
        }
        IvrNodeStep step = new IvrNodeStep(1L, current.getNodeType(), "菜单", null, null);
        IvrNodeContext ctx = new IvrNodeContext(nodeMap, edges, new HashMap<>(), inputs,
                new IvrExecuteRequest(), "sess-1", null, new IvrExecuteResult(), req -> null);
        ctx.setCurrent(current);
        ctx.setStep(step);
        return ctx;
    }

    private static AiIvrNode node(String config)
    {
        return node("menu", config);
    }

    private static AiIvrNode node(String type, String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setNodeType(type);
        node.setNodeConfig(config);
        return node;
    }

    private static AiIvrEdge edge(Long source, Long target, String label)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        edge.setEdgeLabel(label);
        edge.setSortOrder(0);
        return edge;
    }
}
