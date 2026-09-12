package ai.lawyers.framework.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * N4：{@link WsClusterRelay} 发布扇出、自发消息跳过与跨实例消息本机投递测试。
 *
 * @author ai-lawyers
 */
@SuppressWarnings("unchecked")
class WsClusterRelayTest
{
    private StringRedisTemplate redis;
    private WsClusterRelay relay;
    private ConcurrentHashMap<Long, CopyOnWriteArraySet<Session>> agents;
    private Session session;
    private RemoteEndpoint.Async async;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp()
    {
        redis = mock(StringRedisTemplate.class);
        relay = new WsClusterRelay();
        ReflectionTestUtils.setField(relay, "stringRedisTemplate", redis);
        relay.start();

        agents = (ConcurrentHashMap<Long, CopyOnWriteArraySet<Session>>)
                ReflectionTestUtils.getField(CallWebSocketServer.class, "AGENTS");
        agents.clear();
        session = mock(Session.class);
        async = mock(RemoteEndpoint.Async.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getAsyncRemote()).thenReturn(async);
        CopyOnWriteArraySet<Session> set = new CopyOnWriteArraySet<>();
        set.add(session);
        agents.put(77L, set);
    }

    @AfterEach
    void tearDown()
    {
        agents.clear();
        relay.stop();
    }

    @Test
    void publishCallUser_sendsEnvelopeToFanoutChannel() throws Exception
    {
        relay.publishCallUser(77L, "{\"type\":\"X\"}");

        Mockito.verify(redis).convertAndSend(eq(WsClusterRelay.CHANNEL), Mockito.argThat(json ->
        {
            try
            {
                JsonNode node = mapper.readTree((String) json);
                return "CU".equals(node.path("category").asText())
                        && "77".equals(node.path("target").asText())
                        && node.path("origin").asText().equals(relay.getInstanceId());
            }
            catch (Exception e)
            {
                return false;
            }
        }));
        assertThat(WsClusterRelay.getInstance()).isSameAs(relay);
    }

    @Test
    void onMessage_fromOtherInstance_deliversToLocalSession() throws Exception
    {
        Message msg = message("CU", "77", "{\"type\":\"X\"}", "other-instance");

        relay.onMessage(msg, null);

        verify(async).sendText("{\"type\":\"X\"}");
    }

    @Test
    void onMessage_selfOrigin_skippedToAvoidDuplicate() throws Exception
    {
        Message msg = message("CU", "77", "{\"type\":\"X\"}", relay.getInstanceId());

        relay.onMessage(msg, null);

        // 自己发布的消息已在发布前本地直发，订阅回调必须跳过，避免重复推送
        verify(async, never()).sendText(anyString());
    }

    @Test
    void onMessage_unknownUserOnThisNode_silentlyIgnored() throws Exception
    {
        Message msg = message("CU", "888", "{\"type\":\"X\"}", "other-instance");

        relay.onMessage(msg, null);

        verify(async, never()).sendText(anyString());
    }

    @Test
    void onMessage_badJson_doesNotThrow()
    {
        Message msg = mock(Message.class);
        when(msg.getBody()).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

        relay.onMessage(msg, null);
    }

    private Message message(String category, String target, String payload, String origin)
            throws Exception
    {
        WsClusterRelay.Envelope env = new WsClusterRelay.Envelope();
        env.category = category;
        env.target = target;
        env.payload = payload;
        env.origin = origin;
        Message msg = mock(Message.class);
        when(msg.getBody()).thenReturn(mapper.writeValueAsString(env).getBytes(StandardCharsets.UTF_8));
        return msg;
    }
}
