package ai.lawyers.system.service.lawyers.trunk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 呼叫状态机变更事件测试：参数快照与不可变保护。
 *
 * @author ai-lawyers
 */
class CallStateChangeEventTest
{
    @Test
    void getter_透传会话状态与参数()
    {
        Map<String, Object> params = new HashMap<>();
        params.put("duration", 30);
        params.put("cause", "NORMAL_CLEARING");

        CallStateChangeEvent event = new CallStateChangeEvent("sess-1", "HANGUP", params);

        assertEquals("sess-1", event.getSessionId());
        assertEquals("HANGUP", event.getState());
        assertEquals(30, event.getParams().get("duration"));
        assertEquals("NORMAL_CLEARING", event.getParams().get("cause"));
    }

    @Test
    void 构造_params为空时给出空映射()
    {
        CallStateChangeEvent event = new CallStateChangeEvent("sess-2", "RINGING", null);

        assertNotNull(event.getParams());
        assertTrue(event.getParams().isEmpty());
    }

    @Test
    void 构造_对入参做防御性拷贝()
    {
        Map<String, Object> params = new HashMap<>();
        params.put("cause", "NORMAL_CLEARING");

        CallStateChangeEvent event = new CallStateChangeEvent("sess-3", "HANGUP", params);
        params.put("cause", "USER_BUSY");

        assertEquals("NORMAL_CLEARING", event.getParams().get("cause"));
    }

    @Test
    void getParams_返回不可变映射()
    {
        Map<String, Object> params = new HashMap<>();
        params.put("agent", "1001");
        CallStateChangeEvent event = new CallStateChangeEvent("sess-4", "ANSWERED", params);

        assertThrows(UnsupportedOperationException.class, () -> event.getParams().put("k", "v"));
    }
}
