package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * IVR流程执行步骤测试：构造方法、属性读写与默认值。
 *
 * @author ai-lawyers
 */
class IvrNodeStepTest
{
    @Test
    void 属性读写()
    {
        IvrNodeStep step = new IvrNodeStep();
        step.setNodeId(1L);
        step.setNodeType("MENU");
        step.setNodeName("按键导航");
        step.setAction("PRESS_KEY");
        step.setDetail("用户按下1键");

        assertEquals(1L, step.getNodeId());
        assertEquals("MENU", step.getNodeType());
        assertEquals("按键导航", step.getNodeName());
        assertEquals("PRESS_KEY", step.getAction());
        assertEquals("用户按下1键", step.getDetail());
    }

    @Test
    void 全参构造方法赋值()
    {
        IvrNodeStep step = new IvrNodeStep(2L, "INTENTION", "意图识别", "MATCH", "命中劳动争议");

        assertEquals(2L, step.getNodeId());
        assertEquals("INTENTION", step.getNodeType());
        assertEquals("意图识别", step.getNodeName());
        assertEquals("MATCH", step.getAction());
        assertEquals("命中劳动争议", step.getDetail());
    }

    @Test
    void 默认值为空()
    {
        IvrNodeStep step = new IvrNodeStep();

        assertNull(step.getNodeId());
        assertNull(step.getNodeType());
        assertNull(step.getNodeName());
        assertNull(step.getAction());
        assertNull(step.getDetail());
    }
}
