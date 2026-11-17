package ai.lawyers.system.service.lawyers.agent.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 用户坐席配置同步事件测试：完整绑定载荷的透传。
 *
 * @author ai-lawyers
 */
class UserAgentSyncEventTest
{
    @Test
    void 构造函数保存全部绑定字段()
    {
        UserAgentSyncEvent event = new UserAgentSyncEvent(10L, 20L, "张律师",
                "8001", "SIP", "admin");

        assertEquals(10L, event.getUserId());
        assertEquals(20L, event.getAgentId());
        assertEquals("张律师", event.getAgentName());
        assertEquals("8001", event.getSipExtension());
        assertEquals("SIP", event.getCallMode());
        assertEquals("admin", event.getOperator());
    }
}
