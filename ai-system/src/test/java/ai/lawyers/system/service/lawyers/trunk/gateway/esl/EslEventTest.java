package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ESL 事件测试：呼叫 UUID 多键回退与头部读写。
 *
 * @author ai-lawyers
 */
class EslEventTest
{
    @Test
    void getCallUuid_优先取UniqueID()
    {
        EslEvent event = new EslEvent();
        event.put("Unique-ID", "uuid-a");
        event.put("Channel-Call-UUID", "uuid-b");
        event.put("variable_uuid", "uuid-c");

        assertEquals("uuid-a", event.getCallUuid());
    }

    @Test
    void getCallUuid_逐级回退到ChannelCallUUID与variableUuid()
    {
        EslEvent event = new EslEvent();
        event.put("Channel-Call-UUID", "uuid-b");
        event.put("variable_uuid", "uuid-c");
        assertEquals("uuid-b", event.getCallUuid());

        EslEvent onlyVariable = new EslEvent();
        onlyVariable.put("variable_uuid", "uuid-c");
        assertEquals("uuid-c", onlyVariable.getCallUuid());
    }

    @Test
    void getCallUuid_空值视为未命中()
    {
        EslEvent event = new EslEvent();
        event.put("Unique-ID", "");
        event.put("Channel-Call-UUID", "uuid-b");

        assertEquals("uuid-b", event.getCallUuid());
    }

    @Test
    void getCallUuid_全部缺失返回空()
    {
        assertNull(new EslEvent().getCallUuid());
    }

    @Test
    void 头部与正文_读写透传()
    {
        EslEvent event = new EslEvent();
        event.setEventName("CHANNEL_ANSWER");
        event.put("Caller-Caller-ID-Number", "13800000000");
        event.setBody("body-text");

        assertEquals("CHANNEL_ANSWER", event.getEventName());
        assertEquals("13800000000", event.get("Caller-Caller-ID-Number"));
        assertEquals(1, event.all().size());
        assertEquals("body-text", event.getBody());
    }

    @Test
    void toString_包含事件名与UUID()
    {
        EslEvent event = new EslEvent();
        event.setEventName("CHANNEL_HANGUP");
        event.put("Unique-ID", "uuid-x");

        String text = event.toString();

        assertTrue(text.contains("CHANNEL_HANGUP"));
        assertTrue(text.contains("uuid-x"));
    }
}
