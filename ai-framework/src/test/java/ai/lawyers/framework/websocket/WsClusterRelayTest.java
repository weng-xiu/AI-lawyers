package ai.lawyers.framework.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.framework.websocket.voice.VoiceSessionManager;

/**
 * {@link WsClusterRelay} 测试：
 * <ul>
 *   <li>N4：发布扇出、自发消息跳过、跨实例消息本机投递；</li>
 *   <li>P3-C2：每实例 presence key 登记/注销、全局在线判定与去重计数、
 *       语音会话注册表与实例查询、心跳续期、多实例同坐席 key 互不影响。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@SuppressWarnings("unchecked")
class WsClusterRelayTest
{
    private StringRedisTemplate redis;
    private WsClusterRelay relay;
    private ValueOperations<String, String> valueOps;
    private ConcurrentHashMap<Long, CopyOnWriteArraySet<Session>> agents;
    private Session session;
    private RemoteEndpoint.Async async;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp()
    {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
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

    // ===================== N4：fan-out =====================

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

    // ===================== P3-C2：呼叫 presence 每实例 key =====================

    @Test
    void registerCallUser_writesPerInstanceKeyWithTtl()
    {
        relay.registerCallUser(77L);

        String key = "ai-law:ws:presence:call:77:" + relay.getInstanceId();
        verify(valueOps).set(key, relay.getInstanceId(), 60L, TimeUnit.SECONDS);
    }

    @Test
    void unregisterCallUser_deletesOwnKeyOnly()
    {
        relay.unregisterCallUser(77L);

        verify(redis).delete("ai-law:ws:presence:call:77:" + relay.getInstanceId());
    }

    @Test
    void isCallUserOnline_trueWhenAnyInstanceKeyExists()
    {
        // 无任何实例 key：离线
        when(redis.execute(any(RedisCallback.class))).thenReturn(Collections.emptyList());
        assertThat(relay.isCallUserOnline(77L)).isFalse();

        // 任一实例存在 key：在线（pattern 精确性由键名解析用例覆盖）
        when(redis.execute(any(RedisCallback.class)))
                .thenReturn(Collections.singletonList("ai-law:ws:presence:call:77:other-instance"));
        assertThat(relay.isCallUserOnline(77L)).isTrue();
    }

    @Test
    void isCallUserOnline_redisFailure_returnsFalse()
    {
        when(redis.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("redis down"));

        assertThat(relay.isCallUserOnline(77L)).isFalse();
    }

    @Test
    void globalOnlineCount_dedupesUsersAcrossInstances()
    {
        List<String> keys = Arrays.asList(
                "ai-law:ws:presence:call:101:hostA-11111111",
                "ai-law:ws:presence:call:102:hostB-22222222",
                "ai-law:ws:presence:call:101:hostC-33333333");
        when(redis.execute(any(RedisCallback.class))).thenReturn(keys);

        // 101 在两台实例在线只计一次
        assertThat(relay.globalOnlineCount()).isEqualTo(2);
    }

    @Test
    void globalOnlineCount_redisFailure_returnsZero()
    {
        when(redis.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("redis down"));

        assertThat(relay.globalOnlineCount()).isZero();
    }

    // ===================== P3-C2：语音会话注册表 =====================

    @Test
    void registerVoice_writesPerInstanceKeyWithConnIdAndTtl()
    {
        relay.registerVoice("sess1", "agent", "conn-9");

        String key = "ai-law:ws:presence:voice:sess1:agent:" + relay.getInstanceId();
        verify(valueOps).set(key, "conn-9", 60L, TimeUnit.SECONDS);
    }

    @Test
    void unregisterVoice_deletesOwnKey()
    {
        relay.unregisterVoice("sess1", "caller");

        verify(redis).delete("ai-law:ws:presence:voice:sess1:caller:" + relay.getInstanceId());
    }

    @Test
    void voiceInstances_parsesInstanceIdsFromKeys()
    {
        List<String> keys = Arrays.asList(
                "ai-law:ws:presence:voice:sess1:agent:hostA-11111111",
                "ai-law:ws:presence:voice:sess1:agent:hostB-22222222");
        when(redis.execute(any(RedisCallback.class))).thenReturn(keys);

        assertThat(relay.voiceInstances("sess1", "agent"))
                .containsExactlyInAnyOrder("hostA-11111111", "hostB-22222222");
    }

    @Test
    void voiceInstances_emptyWhenNoInstanceAnchored()
    {
        when(redis.execute(any(RedisCallback.class))).thenReturn(Collections.emptyList());

        assertThat(relay.voiceInstances("sessX", "caller")).isEmpty();
    }

    // ===================== P3-C2：心跳续期 =====================

    @Test
    void refreshLocalPresence_renewsOwnCallAndVoiceKeys()
    {
        // 准备一个持有 1 条语音会话的本机 manager，并注入 SpringUtils
        VoiceSessionManager vsm = new VoiceSessionManager();
        ReflectionTestUtils.setField(vsm, "enabled", true);
        ReflectionTestUtils.setField(vsm, "maxSessions", 10);
        ReflectionTestUtils.setField(vsm, "sendQueueCapacity", 100);
        Session voiceWs = mockVoiceSession("c1");
        // register 内部会经 relay（本实例）登记语音 presence
        assertThat(vsm.register(voiceWs, "sessX", "agent")).isNotNull();

        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(VoiceSessionManager.class)).thenReturn(vsm);
        Object oldFactory = ReflectionTestUtils.getField(
                ai.lawyers.common.utils.spring.SpringUtils.class, "beanFactory");
        ReflectionTestUtils.setField(ai.lawyers.common.utils.spring.SpringUtils.class,
                "beanFactory", beanFactory);
        try
        {
            ReflectionTestUtils.invokeMethod(relay, "refreshLocalPresence");

            verify(redis).expire(
                    "ai-law:ws:presence:call:77:" + relay.getInstanceId(),
                    60L, TimeUnit.SECONDS);
            verify(redis).expire(
                    "ai-law:ws:presence:voice:sessX:agent:" + relay.getInstanceId(),
                    60L, TimeUnit.SECONDS);
        }
        finally
        {
            ReflectionTestUtils.setField(ai.lawyers.common.utils.spring.SpringUtils.class,
                    "beanFactory", oldFactory);
            vsm.destroy();
        }
    }

    // ===================== P3-C2：多实例同坐席互不影响 =====================

    @Test
    void twoInstances_sameUser_keysIndependent()
    {
        StringRedisTemplate redis2 = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps2 = mock(ValueOperations.class);
        when(redis2.opsForValue()).thenReturn(valueOps2);
        WsClusterRelay relay2 = new WsClusterRelay();
        ReflectionTestUtils.setField(relay2, "stringRedisTemplate", redis2);
        relay2.start();
        try
        {
            // 同一坐席在两实例各登记一次：key 各自独立、互不覆盖
            relay.registerCallUser(77L);
            relay2.registerCallUser(77L);
            verify(valueOps).set(
                    eq("ai-law:ws:presence:call:77:" + relay.getInstanceId()),
                    anyString(), anyLong(), any(TimeUnit.class));
            verify(valueOps2).set(
                    eq("ai-law:ws:presence:call:77:" + relay2.getInstanceId()),
                    anyString(), anyLong(), any(TimeUnit.class));

            // relay2 断连只删自己的 key
            relay2.unregisterCallUser(77L);
            verify(redis2).delete("ai-law:ws:presence:call:77:" + relay2.getInstanceId());
            verify(redis, never()).delete(contains("presence:call:77"));
        }
        finally
        {
            relay2.stop();
            // relay2.stop 把静态 instance 置空，恢复 relay1 供后续 tearDown
            ReflectionTestUtils.setField(WsClusterRelay.class, "instance", relay);
        }
    }

    // ===================== helpers =====================

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

    private Session mockVoiceSession(String connId)
    {
        Session s = mock(Session.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(s.getId()).thenReturn(connId);
        when(s.isOpen()).thenReturn(true);
        when(s.getBasicRemote()).thenReturn(basic);
        return s;
    }
}
