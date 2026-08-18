package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import java.util.HashMap;
import java.util.Map;

/**
 * FreeSWITCH ESL 事件（已解析的键值对）
 *
 * @author ai-lawyers
 */
public class EslEvent
{
    private String eventName;
    private final Map<String, String> headers = new HashMap<>();
    private String body;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String get(String key) { return headers.get(key); }
    public void put(String key, String value) { headers.put(key, value); }
    public Map<String, String> all() { return headers; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getCallUuid()
    {
        String uuid = get("Unique-ID");
        if (uuid == null || uuid.isEmpty())
        {
            uuid = get("Channel-Call-UUID");
        }
        if (uuid == null || uuid.isEmpty())
        {
            uuid = get("variable_uuid");
        }
        return uuid;
    }

    @Override
    public String toString()
    {
        return "EslEvent{name=" + eventName + ", uuid=" + getCallUuid() + ", keys=" + headers.size() + "}";
    }
}
