package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * IVR节点实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiIvrNodeTest
{
    @Test
    void 属性读写()
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(1L);
        node.setFlowId(2L);
        node.setNodeType("MENU");
        node.setNodeName("按键导航");
        node.setNodeConfig("{\"prompt\":\"请按1咨询\"}");
        node.setPositionX(100);
        node.setPositionY(200);
        node.setSortOrder(1);

        assertEquals(1L, node.getNodeId());
        assertEquals(2L, node.getFlowId());
        assertEquals("MENU", node.getNodeType());
        assertEquals("按键导航", node.getNodeName());
        assertEquals("{\"prompt\":\"请按1咨询\"}", node.getNodeConfig());
        assertEquals(100, node.getPositionX());
        assertEquals(200, node.getPositionY());
        assertEquals(1, node.getSortOrder());
    }

    @Test
    void 默认值为空()
    {
        AiIvrNode node = new AiIvrNode();

        assertNull(node.getNodeId());
        assertNull(node.getFlowId());
        assertNull(node.getNodeType());
        assertNull(node.getNodeConfig());
        assertNull(node.getPositionX());
        assertNull(node.getSortOrder());
    }

    @Test
    void 字符串输出包含节点类型与名称()
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeType("TRANSFER");
        node.setNodeName("转人工");

        String text = node.toString();
        assertTrue(text.contains("TRANSFER"));
        assertTrue(text.contains("转人工"));
    }
}
