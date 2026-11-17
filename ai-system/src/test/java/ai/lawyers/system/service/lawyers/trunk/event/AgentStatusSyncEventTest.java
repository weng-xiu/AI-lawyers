package ai.lawyers.system.service.lawyers.trunk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 队列成员设备态同步事件测试：分机号与目标状态的不可变载荷。
 *
 * @author ai-lawyers
 */
class AgentStatusSyncEventTest
{
    @Test
    void 构造函数保存分机与目标状态()
    {
        AgentStatusSyncEvent event = new AgentStatusSyncEvent("8001", "READY");

        assertEquals("8001", event.getExtension());
        assertEquals("READY", event.getTargetStatus());
    }

    @Test
    void 允许空载荷()
    {
        AgentStatusSyncEvent event = new AgentStatusSyncEvent(null, null);

        assertNull(event.getExtension());
        assertNull(event.getTargetStatus());
    }
}
