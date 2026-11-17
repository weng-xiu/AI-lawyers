package ai.lawyers.framework.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * 呼叫事件发布测试：按用户推送、广播、空用户忽略与序列化失败兜底。
 *
 * @author ai-lawyers
 */
class WebSocketCallEventPublisherTest
{
    private final WebSocketCallEventPublisher publisher = new WebSocketCallEventPublisher();

    private MockedStatic<CallWebSocketServer> callServer;

    @BeforeEach
    void setUp()
    {
        callServer = mockStatic(CallWebSocketServer.class);
    }

    @AfterEach
    void tearDown()
    {
        callServer.close();
    }

    @Test
    void publishToUser_按用户推送事件JSON()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callId", "c-1");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        publisher.publishToUser(2L, "call.ringing", data);

        callServer.verify(() -> CallWebSocketServer.sendToUser(eq(2L), captor.capture()));
        String json = captor.getValue();
        assertTrue(json.contains("\"type\":\"call.ringing\""), json);
        assertTrue(json.contains("\"callId\":\"c-1\""), json);
    }

    @Test
    void publishToUser_用户ID为空时忽略()
    {
        publisher.publishToUser(null, "call.end", null);

        callServer.verify(() -> CallWebSocketServer.sendToUser(any(), anyString()), never());
    }

    @Test
    void broadcast_推送给全部连接()
    {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        publisher.broadcast("agent.status", "online");

        callServer.verify(() -> CallWebSocketServer.broadcast(captor.capture()));
        assertEquals("{\"type\":\"agent.status\",\"data\":\"online\"}", captor.getValue());
    }

    @Test
    void 序列化异常时回退为null数据()
    {
        Object broken = new Object()
        {
            public String getValue()
            {
                throw new IllegalStateException("boom");
            }
        };
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        publisher.broadcast("broken", broken);

        callServer.verify(() -> CallWebSocketServer.broadcast(captor.capture()));
        assertEquals("{\"type\":\"broken\",\"data\":null}", captor.getValue());
    }
}
