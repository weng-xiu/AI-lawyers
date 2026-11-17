package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * AMI 事件测试：头部忽略大小写取值与空头部兜底。
 *
 * @author ai-lawyers
 */
class AmiEventTest
{
    @Test
    void get_忽略头部大小写()
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("UniqueID", "1680000000.12");
        AmiEvent event = new AmiEvent("Newchannel", headers);

        assertEquals("1680000000.12", event.get("Uniqueid"));
        assertEquals("1680000000.12", event.get("uniqueid"));
        assertEquals("1680000000.12", event.get("UniqueID"));
    }

    @Test
    void get_键为空或不存在返回空()
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("CallerIDNum", "13800000000");
        AmiEvent event = new AmiEvent("Dial", headers);

        assertNull(event.get(null));
        assertNull(event.get("CalleeIDNum"));
    }

    @Test
    void 构造_头部为空时给出空映射()
    {
        AmiEvent event = new AmiEvent("Hangup", null);

        assertEquals("Hangup", event.getName());
        assertTrue(event.headers().isEmpty());
        assertNull(event.get("Uniqueid"));
    }

    @Test
    void headers_返回原始头部()
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("Event", "BridgeEnter");
        AmiEvent event = new AmiEvent("BridgeEnter", headers);

        assertEquals("BridgeEnter", event.headers().get("Event"));
        assertEquals(1, event.headers().size());
    }

    @Test
    void toString_包含事件名与头部()
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("Uniqueid", "1.1");
        AmiEvent event = new AmiEvent("Hangup", headers);

        String text = event.toString();

        assertTrue(text.contains("Hangup"));
        assertTrue(text.contains("Uniqueid"));
    }
}
