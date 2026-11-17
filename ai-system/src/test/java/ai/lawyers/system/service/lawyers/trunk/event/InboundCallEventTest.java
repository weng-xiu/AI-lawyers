package ai.lawyers.system.service.lawyers.trunk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 入站来话事件测试：主机、会话与主被叫透传。
 *
 * @author ai-lawyers
 */
class InboundCallEventTest
{
    @Test
    void getter_透传入站呼叫信息()
    {
        InboundCallEvent event = new InboundCallEvent("esl:10.0.0.8", "sess-9", "13800000000", "4001234567");

        assertEquals("esl:10.0.0.8", event.getHost());
        assertEquals("sess-9", event.getSessionId());
        assertEquals("13800000000", event.getCaller());
        assertEquals("4001234567", event.getCallee());
    }

    @Test
    void 构造_允许主叫被叫为空()
    {
        InboundCallEvent event = new InboundCallEvent("ami:pbx", "sess-10", null, null);

        assertNull(event.getCaller());
        assertNull(event.getCallee());
        assertEquals("sess-10", event.getSessionId());
    }
}
