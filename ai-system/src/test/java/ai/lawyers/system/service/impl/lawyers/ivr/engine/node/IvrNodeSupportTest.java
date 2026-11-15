package ai.lawyers.system.service.impl.lawyers.ivr.engine.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;

/**
 * IVR 节点共享工具单测：配置解析、出线路由优先级、SpEL 条件求值与通用小函数。
 *
 * @author ai-lawyers
 */
class IvrNodeSupportTest
{
    @Test
    void config_handlesNullBlankAndInvalidJson()
    {
        assertThat(IvrNodeSupport.config(node((String) null)).isObject()).isTrue();
        assertThat(IvrNodeSupport.config(node((String) null)).size()).isZero();
        assertThat(IvrNodeSupport.config(node("   ")).size()).isZero();
        assertThat(IvrNodeSupport.config(node("not-json")).size()).isZero();
    }

    @Test
    void config_parsesJson()
    {
        assertThat(IvrNodeSupport.config(node("{\"name\":\"n\",\"value\":\"v\"}"))
                .path("name").asText()).isEqualTo("n");
    }

    @Test
    void configText_fallsBackToValueKey()
    {
        assertThat(IvrNodeSupport.configText(node("{\"text\":\"直接\"}"), "text")).isEqualTo("直接");
        assertThat(IvrNodeSupport.configText(node("{\"value\":\"兜底\"}"), "text")).isEqualTo("兜底");
        assertThat(IvrNodeSupport.configText(node("{}"), "text")).isNull();
    }

    @Test
    void routeEdge_prefersFirstTrueExpressionBySortOrder()
    {
        List<AiIvrEdge> edges = Arrays.asList(
                edge(1, 11, "score > 90", null, 1),
                edge(2, 12, "score > 50", null, 2),
                edge(3, 13, null, null, 3));
        Map<String, Object> vars = new HashMap<>();
        vars.put("score", 95);

        assertThat(IvrNodeSupport.routeEdge(nodeById(1L), edges, vars, null).getTargetNodeId())
                .isEqualTo(11L);

        vars.put("score", 60);
        assertThat(IvrNodeSupport.routeEdge(nodeById(1L), edges, vars, null).getTargetNodeId())
                .isEqualTo(12L);
    }

    @Test
    void routeEdge_matchesInputLabelBeforeDefault()
    {
        List<AiIvrEdge> edges = Arrays.asList(
                edge(1, 11, null, "1", 1),
                edge(2, 12, null, null, 2));

        assertThat(IvrNodeSupport.routeEdge(nodeById(1L), edges, new HashMap<>(), "1").getTargetNodeId())
                .isEqualTo(11L);
        assertThat(IvrNodeSupport.routeEdge(nodeById(1L), edges, new HashMap<>(), "9").getTargetNodeId())
                .isEqualTo(12L);
    }

    @Test
    void routeEdge_ignoresEdgesOfOtherNodes()
    {
        AiIvrEdge other = edge(1, 99, null, null, 1);
        other.setSourceNodeId(2L);

        assertThat(IvrNodeSupport.routeEdge(nodeById(1L), Collections.singletonList(other),
                new HashMap<>(), null)).isNull();
    }

    @Test
    void routeEdge_noEdgesReturnsNull()
    {
        assertThat(IvrNodeSupport.routeEdge(nodeById(1L), Collections.emptyList(),
                new HashMap<>(), null)).isNull();
    }

    @Test
    void nextNode_resolvesTargetFromNodeMap()
    {
        AiIvrNode target = nodeById(2L);
        Map<Long, AiIvrNode> nodeMap = Collections.singletonMap(2L, target);

        AiIvrNode next = IvrNodeSupport.nextNode(nodeById(1L), nodeMap,
                Collections.singletonList(edge(1, 2, null, null, 1)), new HashMap<>(), null);

        assertThat(next).isSameAs(target);
    }

    @Test
    void evalCondition_booleanAndVariableRules()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("level", "VIP");

        assertThat(IvrNodeSupport.evalCondition("level == 'VIP'", vars)).isTrue();
        assertThat(IvrNodeSupport.evalCondition("level == 'NORMAL'", vars)).isFalse();
        assertThat(IvrNodeSupport.evalCondition("missing == 'x'", vars)).isFalse();
        assertThat(IvrNodeSupport.evalCondition("level >", vars)).isFalse();
        assertThat(IvrNodeSupport.evalCondition("1 + 1", vars)).isFalse();
    }

    @Test
    void pollInput_returnsNullWhenAbsentAndKeepsOrder()
    {
        assertThat(IvrNodeSupport.pollInput(null)).isNull();
        assertThat(IvrNodeSupport.pollInput(new ArrayDeque<>())).isNull();

        Deque<String> inputs = new ArrayDeque<>(Arrays.asList("1", "2"));
        assertThat(IvrNodeSupport.pollInput(inputs)).isEqualTo("1");
        assertThat(IvrNodeSupport.pollInput(inputs)).isEqualTo("2");
        assertThat(inputs).isEmpty();
    }

    @Test
    void cleanJson_extractsObjectBody()
    {
        assertThat(IvrNodeSupport.cleanJson(null)).isEqualTo("{}");
        assertThat(IvrNodeSupport.cleanJson("  ")).isEqualTo("{}");
        assertThat(IvrNodeSupport.cleanJson("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(IvrNodeSupport.cleanJson("前缀 {\"a\":1} 后缀")).isEqualTo("{\"a\":1}");
        assertThat(IvrNodeSupport.cleanJson("无对象")).isEqualTo("无对象");
    }

    @Test
    void parseInt_usesFallbackOnInvalidValue()
    {
        assertThat(IvrNodeSupport.parseInt(null, 7)).isEqualTo(7);
        assertThat(IvrNodeSupport.parseInt("42", 7)).isEqualTo(42);
        assertThat(IvrNodeSupport.parseInt(42, 7)).isEqualTo(42);
        assertThat(IvrNodeSupport.parseInt("abc", 7)).isEqualTo(7);
    }

    @Test
    void labelOf_prefersConditionThenLabelThenDefault()
    {
        assertThat(IvrNodeSupport.labelOf(edge(1, 2, "a > 1", "标签", 1))).isEqualTo("a > 1");
        assertThat(IvrNodeSupport.labelOf(edge(1, 2, null, "标签", 1))).isEqualTo("标签");
        assertThat(IvrNodeSupport.labelOf(edge(1, 2, null, null, 1))).isEqualTo("默认");
    }

    @Test
    void truncate_cutsAtMax()
    {
        assertThat(IvrNodeSupport.truncate(null, 3)).isNull();
        assertThat(IvrNodeSupport.truncate("abc", 3)).isEqualTo("abc");
        assertThat(IvrNodeSupport.truncate("abcdef", 3)).isEqualTo("abc");
    }

    private AiIvrNode node(String config)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setNodeType("say");
        node.setNodeConfig(config);
        return node;
    }

    private AiIvrNode nodeById(Long nodeId)
    {
        AiIvrNode node = node(null);
        node.setNodeId(nodeId);
        return node;
    }

    private AiIvrEdge edge(long edgeId, long targetId, String conditionExpr, String label, int sortOrder)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(edgeId);
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(targetId);
        edge.setConditionExpr(conditionExpr);
        edge.setEdgeLabel(label);
        edge.setSortOrder(sortOrder);
        return edge;
    }
}
