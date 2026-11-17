package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * IVR节点连线实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiIvrEdgeTest
{
    @Test
    void 属性读写()
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(1L);
        edge.setFlowId(2L);
        edge.setSourceNodeId(3L);
        edge.setTargetNodeId(4L);
        edge.setEdgeLabel("按1");
        edge.setConditionExpr("input == '1'");
        edge.setSortOrder(1);

        assertEquals(1L, edge.getEdgeId());
        assertEquals(2L, edge.getFlowId());
        assertEquals(3L, edge.getSourceNodeId());
        assertEquals(4L, edge.getTargetNodeId());
        assertEquals("按1", edge.getEdgeLabel());
        assertEquals("input == '1'", edge.getConditionExpr());
        assertEquals(1, edge.getSortOrder());
    }

    @Test
    void 默认值为空()
    {
        AiIvrEdge edge = new AiIvrEdge();

        assertNull(edge.getEdgeId());
        assertNull(edge.getFlowId());
        assertNull(edge.getSourceNodeId());
        assertNull(edge.getTargetNodeId());
        assertNull(edge.getEdgeLabel());
        assertNull(edge.getSortOrder());
    }

    @Test
    void 字符串输出包含连线标签与条件表达式()
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeLabel("转人工");
        edge.setConditionExpr("input == '9'");

        String text = edge.toString();
        assertTrue(text.contains("转人工"));
        assertTrue(text.contains("input == '9'"));
    }
}
