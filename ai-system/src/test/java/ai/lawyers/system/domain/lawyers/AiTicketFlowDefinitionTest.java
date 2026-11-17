package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 工单流程状态机定义实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiTicketFlowDefinitionTest
{
    @Test
    void 属性读写()
    {
        BigDecimal slaHours = new BigDecimal("24");
        AiTicketFlowDefinition definition = new AiTicketFlowDefinition();
        definition.setFlowId(1L);
        definition.setFlowCode("TICKET_MAIN");
        definition.setStatusFrom("0");
        definition.setActionCode("ACCEPT");
        definition.setActionName("受理");
        definition.setTargetStatus("1");
        definition.setRoleKey("agent");
        definition.setSortNo(1);
        definition.setStatus("0");
        definition.setSlaHours(slaHours);
        definition.setFormSchema("{\"required\":[\"orgId\",\"remark\"]}");

        assertEquals(1L, definition.getFlowId());
        assertEquals("TICKET_MAIN", definition.getFlowCode());
        assertEquals("0", definition.getStatusFrom());
        assertEquals("ACCEPT", definition.getActionCode());
        assertEquals("受理", definition.getActionName());
        assertEquals("1", definition.getTargetStatus());
        assertEquals("agent", definition.getRoleKey());
        assertEquals(1, definition.getSortNo());
        assertEquals("0", definition.getStatus());
        assertEquals(slaHours, definition.getSlaHours());
        assertEquals("{\"required\":[\"orgId\",\"remark\"]}", definition.getFormSchema());
    }

    @Test
    void 默认值为空()
    {
        AiTicketFlowDefinition definition = new AiTicketFlowDefinition();

        assertNull(definition.getFlowId());
        assertNull(definition.getFlowCode());
        assertNull(definition.getActionCode());
        assertNull(definition.getTargetStatus());
        assertNull(definition.getSlaHours());
        assertNull(definition.getFormSchema());
    }
}
