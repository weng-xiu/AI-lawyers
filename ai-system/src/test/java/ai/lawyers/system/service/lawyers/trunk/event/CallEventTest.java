package ai.lawyers.system.service.lawyers.trunk.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 统一 PBX 呼叫事件模型测试：payload 取值辅助（String/int/boolean 容错）、
 * 便捷构造默认值、会话 ID 判空。
 *
 * @author ai-lawyers
 */
class CallEventTest
{
    @Test
    void payloadString_空负载与空key返回null()
    {
        CallEvent event = new CallEvent();
        assertThat(event.payloadString("dtmf")).isNull();

        event.setPayload(new HashMap<>());
        assertThat(event.payloadString("dtmf")).isNull();
        assertThat(event.payloadString(null)).isNull();
    }

    @Test
    void payloadString_任意类型转字符串()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("number", 13800001234L);
        payload.put("flag", false);
        CallEvent event = new CallEvent();
        event.setPayload(payload);

        assertThat(event.payloadString("number")).isEqualTo("13800001234");
        assertThat(event.payloadString("flag")).isEqualTo("false");
    }

    @Test
    void payloadInt_数字字符串与非法值容错()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("duration", 42);
        payload.put("digits", "37");
        payload.put("bad", "abc");
        payload.put("nil", null);
        CallEvent event = new CallEvent();
        event.setPayload(payload);

        assertThat(event.payloadInt("duration", -1)).isEqualTo(42);
        assertThat(event.payloadInt("digits", -1)).isEqualTo(37);
        assertThat(event.payloadInt("bad", -1)).isEqualTo(-1);
        assertThat(event.payloadInt("nil", -1)).isEqualTo(-1);
        assertThat(event.payloadInt("missing", -1)).isEqualTo(-1);
        assertThat(event.payloadInt(null, -1)).isEqualTo(-1);
    }

    @Test
    void payloadInt_无负载返回默认值()
    {
        assertThat(new CallEvent().payloadInt("duration", 7)).isEqualTo(7);
    }

    @Test
    void payloadBool_布尔与字符串旗标()
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recorded", Boolean.TRUE);
        payload.put("answered", "TRUE");
        payload.put("bridged", "yes");
        payload.put("nil", null);
        CallEvent event = new CallEvent();
        event.setPayload(payload);

        assertThat(event.payloadBool("recorded")).isTrue();
        assertThat(event.payloadBool("answered")).isTrue();
        assertThat(event.payloadBool("bridged")).isFalse();
        assertThat(event.payloadBool("nil")).isFalse();
        assertThat(event.payloadBool("missing")).isFalse();
        assertThat(event.payloadBool(null)).isFalse();
    }

    @Test
    void payloadBool_无负载返回false()
    {
        assertThat(new CallEvent().payloadBool("recorded")).isFalse();
    }

    @Test
    void of_填充来源事件名与会话并生成默认字段()
    {
        CallEvent event = CallEvent.of("ESL:127.0.0.1", CallEvent.ANSWERED, "uuid-1");

        assertThat(event.getSource()).isEqualTo("ESL:127.0.0.1");
        assertThat(event.getEventName()).isEqualTo(CallEvent.ANSWERED);
        assertThat(event.getSessionId()).isEqualTo("uuid-1");
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getTimestamp()).isPositive();
        assertThat(event.hasSession()).isTrue();
    }

    @Test
    void 事件ID默认为独立UUID()
    {
        CallEvent first = new CallEvent();
        CallEvent second = new CallEvent();

        assertThat(first.getEventId()).isNotBlank();
        assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
    }

    @Test
    void hasSession_空串与null为false()
    {
        CallEvent event = new CallEvent();
        assertThat(event.hasSession()).isFalse();

        event.setSessionId("");
        assertThat(event.hasSession()).isFalse();

        event.setSessionId("uuid-2");
        assertThat(event.hasSession()).isTrue();
    }
}
