package ai.lawyers.framework.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;

/**
 * 基于 {@link CallWebSocketServer} 的呼叫事件发布实现
 *
 * @author ai-lawyers
 */
@Component
public class WebSocketCallEventPublisher implements CallEventPublisher
{
    private static final Logger log = LoggerFactory.getLogger(WebSocketCallEventPublisher.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void publishToUser(Long userId, String type, Object data)
    {
        if (userId == null) return;
        CallWebSocketServer.sendToUser(userId, toJson(type, data));
    }

    @Override
    public void broadcast(String type, Object data)
    {
        CallWebSocketServer.broadcast(toJson(type, data));
    }

    private String toJson(String type, Object data)
    {
        try
        {
            StringBuilder sb = new StringBuilder("{\"type\":\"").append(type).append("\"");
            if (data != null)
            {
                sb.append(",\"data\":").append(objectMapper.writeValueAsString(data));
            }
            return sb.append('}').toString();
        }
        catch (Exception e)
        {
            log.error("序列化呼叫事件失败: type={}", type, e);
            return "{\"type\":\"" + type + "\",\"data\":null}";
        }
    }
}
