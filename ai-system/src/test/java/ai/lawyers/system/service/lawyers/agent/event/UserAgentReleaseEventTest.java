package ai.lawyers.system.service.lawyers.agent.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 用户删除坐席释放事件测试：用户ID载荷透传。
 *
 * @author ai-lawyers
 */
class UserAgentReleaseEventTest
{
    @Test
    void 构造函数保存用户ID()
    {
        UserAgentReleaseEvent event = new UserAgentReleaseEvent(10L);

        assertEquals(10L, event.getUserId());
    }

    @Test
    void 允许空用户ID()
    {
        UserAgentReleaseEvent event = new UserAgentReleaseEvent(null);

        assertNull(event.getUserId());
    }
}
