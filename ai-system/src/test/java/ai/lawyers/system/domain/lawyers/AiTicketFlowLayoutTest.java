package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 工单流程画布布局实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiTicketFlowLayoutTest
{
    @Test
    void 属性读写()
    {
        AiTicketFlowLayout layout = new AiTicketFlowLayout();
        layout.setLayoutId(1L);
        layout.setFlowCode("TICKET_MAIN");
        layout.setStatusCode("0");
        layout.setPosX(120);
        layout.setPosY(80);

        assertEquals(1L, layout.getLayoutId());
        assertEquals("TICKET_MAIN", layout.getFlowCode());
        assertEquals("0", layout.getStatusCode());
        assertEquals(120, layout.getPosX());
        assertEquals(80, layout.getPosY());
    }

    @Test
    void 默认值为空()
    {
        AiTicketFlowLayout layout = new AiTicketFlowLayout();

        assertNull(layout.getLayoutId());
        assertNull(layout.getFlowCode());
        assertNull(layout.getStatusCode());
        assertNull(layout.getPosX());
        assertNull(layout.getPosY());
    }
}
