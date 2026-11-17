package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * IVR流程实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiIvrFlowTest
{
    @Test
    void 属性读写()
    {
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(1L);
        flow.setFlowName("法律咨询主流程");
        flow.setFlowCode("LEGAL_MAIN");
        flow.setDescription("来电进入法律咨询的主流程");
        flow.setFlowData("{\"nodes\":[]}");
        flow.setStatus("1");
        flow.setVersion(2);
        flow.setCategory("咨询");
        flow.setIsDefault("1");

        assertEquals(1L, flow.getFlowId());
        assertEquals("法律咨询主流程", flow.getFlowName());
        assertEquals("LEGAL_MAIN", flow.getFlowCode());
        assertEquals("来电进入法律咨询的主流程", flow.getDescription());
        assertEquals("{\"nodes\":[]}", flow.getFlowData());
        assertEquals("1", flow.getStatus());
        assertEquals(2, flow.getVersion());
        assertEquals("咨询", flow.getCategory());
        assertEquals("1", flow.getIsDefault());
    }

    @Test
    void 默认值为空()
    {
        AiIvrFlow flow = new AiIvrFlow();

        assertNull(flow.getFlowId());
        assertNull(flow.getFlowName());
        assertNull(flow.getFlowCode());
        assertNull(flow.getStatus());
        assertNull(flow.getVersion());
        assertNull(flow.getIsDefault());
    }

    @Test
    void 字符串输出包含流程名称与编码()
    {
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowName("投诉建议流程");
        flow.setFlowCode("COMPLAINT");

        String text = flow.toString();
        assertTrue(text.contains("投诉建议流程"));
        assertTrue(text.contains("COMPLAINT"));
    }
}
