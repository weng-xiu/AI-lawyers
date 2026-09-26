package ai.lawyers.framework.websocket;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.framework.websocket.voice.VoiceSessionManager;

/**
 * WebSocket 跨实例消息中转（N4）。
 *
 * <p>多实例部署时每个实例只持有连到本机的 WebSocket Session，本机静态注册表无法覆盖
 * 其他实例上的坐席。本组件通过 Redis Pub/Sub 做两件事：</p>
 * <ol>
 *   <li><b>跨实例 fan-out</b>：任一点产生的推送发布到统一频道 {@value #CHANNEL}，
 *       所有实例订阅；收到后仅投递给本机持有的连接，来源实例跳过自己发出的消息
 *       （避免与本地直发重复）。</li>
 *   <li><b>在线注册表（每实例 key，P3-C2 加固）</b>：坐席在本机建立连接后写
 *       {@code ai-law:ws:presence:call:{userId}:{instanceId}}（带 TTL），
 *       心跳仅续期本实例 key、本实例最后一条连接关闭时直接删除本实例 key；
 *       同一坐席同时连接多个实例时各实例 key 并存，任一实例断连不影响其他实例的
 *       在线标记（修复早期单值 key 被覆盖、CAS 删除导致假离线的缺陷）；
 *       全局在线判定 = 该坐席前缀下是否存在任一实例 key。</li>
 *   <li><b>语音会话注册表</b>：{@code /ws/voice} 连接登记
 *       {@code ai-law:ws:presence:voice:{sessionId}:{role}:{instanceId}}（值=connId），
 *       供跨实例查询"语音会话锚定在哪台实例"；本批不做二进制帧跨实例转发
 *       （部署用 nginx sticky 或随 C6 扩展帧中继）。</li>
 * </ol>
 *
 * <p><b>回退开关</b>：{@code websocket.cluster.enabled=false}（默认）时本 Bean 不装配，
 * {@link #getInstance()} 恒为 null，全部静态入口静默退化为原单机直发行为。
 * Redis 异常同样只记录告警、不阻断本地推送。</p>
 *
 * @author ai-lawyers
 */
@Component
@ConditionalOnProperty(prefix = "websocket.cluster", name = "enabled", havingValue = "true")
public class WsClusterRelay implements MessageListener
{
    private static final Logger log = LoggerFactory.getLogger(WsClusterRelay.class);

    /** 跨实例 fan-out 统一频道 */
    public static final String CHANNEL = "ai-law:ws:fanout";

    /** 呼叫坐席在线注册表 key 前缀（完整 key = 前缀 + userId + ':' + instanceId） */
    private static final String PRESENCE_PREFIX = "ai-law:ws:presence:call:";

    /** 语音会话注册表 key 前缀（完整 key = 前缀 + sessionId + ':' + role + ':' + instanceId） */
    private static final String VOICE_PRESENCE_PREFIX = "ai-law:ws:presence:voice:";

    /** 在线键 TTL（秒），必须大于心跳间隔 */
    private static final long PRESENCE_TTL_SECONDS = 60;

    /** 心跳续期间隔（秒） */
    private static final long HEARTBEAT_SECONDS = 20;

    /** SCAN hint（presence key 总量≈在线坐席×实例数，规模小） */
    private static final long SCAN_HINT_COUNT = 200L;

    /** 消息类别：呼叫定向推送给某 userId */
    private static final String CAT_CALL_USER = "CU";
    /** 消息类别：呼叫全员广播 */
    private static final String CAT_CALL_BROADCAST = "CB";
    /** 消息类别：图文会话房间广播 */
    private static final String CAT_CHAT = "CH";

    /** 单机回退时为 null，静态入口据此静默跳过 Redis 路径 */
    private static volatile WsClusterRelay instance;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 本实例ID：主机名+随机串，用于消息去重与注册表 CAS */
    private final String instanceId = buildInstanceId();

    /** 在线键续期心跳（daemon，不阻止 JVM 退出） */
    private ScheduledExecutorService heartbeat;

    /** @return 当前实例的集群中转组件；未开启集群模式时返回 null（调用方静默降级单机直发） */
    public static WsClusterRelay getInstance()
    {
        return instance;
    }

    /**
     * 启动心跳并注册为当前实例中转组件。
     */
    @javax.annotation.PostConstruct
    public void start()
    {
        instance = this;
        heartbeat = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "ws-cluster-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(this::refreshLocalPresence,
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        log.info("[WsCluster] WebSocket 跨实例转发已启用，instanceId={}, channel={}", instanceId, CHANNEL);
    }

    /**
     * 停机时注销本实例全部在线标记，避免残留 TTL 窗口。
     */
    @javax.annotation.PreDestroy
    public void stop()
    {
        if (heartbeat != null)
        {
            heartbeat.shutdownNow();
        }
        try
        {
            for (Long userId : CallWebSocketServer.localUserIds())
            {
                unregisterCallUser(userId);
            }
            for (VoiceSessionManager.VoiceRef ref : VoiceSessionManager.localVoiceRefs())
            {
                unregisterVoice(ref.sessionId, ref.role);
            }
        }
        catch (Exception ignored)
        {
        }
        instance = null;
    }

    // ===================== 发布侧（本机产生推送时调用） =====================

    /** 发布"定向推送给某坐席"的跨实例消息 */
    public void publishCallUser(Long userId, String json)
    {
        publish(CAT_CALL_USER, String.valueOf(userId), json);
    }

    /** 发布"呼叫全员广播"的跨实例消息 */
    public void publishCallBroadcast(String json)
    {
        publish(CAT_CALL_BROADCAST, null, json);
    }

    /** 发布"图文房间广播"的跨实例消息 */
    public void publishChat(String sessionId, String message)
    {
        publish(CAT_CHAT, sessionId, message);
    }

    private void publish(String category, String target, String payload)
    {
        try
        {
            Envelope env = new Envelope();
            env.category = category;
            env.target = target;
            env.payload = payload;
            env.origin = instanceId;
            stringRedisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(env));
        }
        catch (Exception e)
        {
            // Redis 故障只影响跨实例送达，本地发送已在调用处完成，不阻断业务
            log.warn("[WsCluster] 跨实例消息发布失败 category={}, target={}: {}",
                    category, target, e.getMessage());
        }
    }

    // ===================== 订阅侧（收到其他实例的消息） =====================

    @Override
    public void onMessage(Message message, byte[] pattern)
    {
        try
        {
            JsonNode node = objectMapper.readTree(message.getBody());
            String origin = node.path("origin").asText("");
            // 跳过自己发布的消息：本地发送已经在发布前完成
            if (instanceId.equals(origin))
            {
                return;
            }
            String category = node.path("category").asText();
            String target = node.path("target").asText(null);
            String payload = node.path("payload").asText("");
            if (CAT_CALL_USER.equals(category) && target != null)
            {
                CallWebSocketServer.sendToUserLocal(Long.valueOf(target), payload);
            }
            else if (CAT_CALL_BROADCAST.equals(category))
            {
                CallWebSocketServer.broadcastLocal(payload);
            }
            else if (CAT_CHAT.equals(category) && target != null)
            {
                ChatWebSocketServer.broadcastLocal(target, payload);
            }
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 跨实例消息处理失败: {}", e.getMessage());
        }
    }

    // ===================== 呼叫坐席在线注册表 =====================

    /** 坐席在本机建立连接时登记本实例 key（多实例并存不互相覆盖，TTL 由心跳续期） */
    public void registerCallUser(Long userId)
    {
        if (userId == null) return;
        try
        {
            stringRedisTemplate.opsForValue().set(callPresenceKey(userId), instanceId,
                    PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 在线登记失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 本实例该坐席最后一条连接关闭时删除本实例 key（不触碰其他实例 key，无需 CAS） */
    public void unregisterCallUser(Long userId)
    {
        if (userId == null) return;
        try
        {
            stringRedisTemplate.delete(callPresenceKey(userId));
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 在线注销失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 全局在线判定：该坐席在任一实例上存在 presence key。
     * Redis 异常时保守返回 false（调用方可按需先查本地）。
     */
    public boolean isCallUserOnline(Long userId)
    {
        if (userId == null) return false;
        try
        {
            return !scanKeys(PRESENCE_PREFIX + userId + ":*").isEmpty();
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 全局在线判定失败 userId={}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 全集群去重在线坐席数（同一坐席多实例/多连接只计一次）。Redis 异常返回 0。
     */
    public int globalOnlineCount()
    {
        try
        {
            Set<String> userIds = new HashSet<>();
            for (String key : scanKeys(PRESENCE_PREFIX + "*"))
            {
                String tail = key.substring(PRESENCE_PREFIX.length());
                int sep = tail.lastIndexOf(':');
                if (sep > 0)
                {
                    userIds.add(tail.substring(0, sep));
                }
            }
            return userIds.size();
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 全局在线数统计失败: {}", e.getMessage());
            return 0;
        }
    }

    // ===================== 语音会话注册表 =====================

    /** /ws/voice 建连时登记（key 含实例，值为 connId，TTL 由心跳续期） */
    public void registerVoice(String sessionId, String role, String connId)
    {
        if (sessionId == null || role == null) return;
        try
        {
            stringRedisTemplate.opsForValue().set(voicePresenceKey(sessionId, role),
                    connId == null ? "" : connId, PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 语音在线登记失败 sessionId={}, role={}: {}", sessionId, role, e.getMessage());
        }
    }

    /** /ws/voice 断连时删除本实例 key */
    public void unregisterVoice(String sessionId, String role)
    {
        if (sessionId == null || role == null) return;
        try
        {
            stringRedisTemplate.delete(voicePresenceKey(sessionId, role));
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 语音在线注销失败 sessionId={}, role={}: {}", sessionId, role, e.getMessage());
        }
    }

    /**
     * 查询持有指定语音会话（sessionId+role）的全部实例ID；空集合表示无实例锚定。
     */
    public Set<String> voiceInstances(String sessionId, String role)
    {
        Set<String> instances = new HashSet<>();
        if (sessionId == null || role == null) return instances;
        try
        {
            String pattern = VOICE_PRESENCE_PREFIX + sessionId + ":" + role + ":*";
            for (String key : scanKeys(pattern))
            {
                instances.add(key.substring(key.lastIndexOf(':') + 1));
            }
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 语音实例查询失败 sessionId={}, role={}: {}", sessionId, role, e.getMessage());
        }
        return instances;
    }

    // ===================== 心跳与内部工具 =====================

    /** 为本机全部呼叫/语音 presence key 续期（只续本实例 key） */
    private void refreshLocalPresence()
    {
        try
        {
            for (Long userId : CallWebSocketServer.localUserIds())
            {
                stringRedisTemplate.expire(callPresenceKey(userId), PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
            }
            for (VoiceSessionManager.VoiceRef ref : VoiceSessionManager.localVoiceRefs())
            {
                stringRedisTemplate.expire(voicePresenceKey(ref.sessionId, ref.role),
                        PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
            }
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 在线心跳续期失败: {}", e.getMessage());
        }
    }

    /** SCAN（cursor + hint，不用 KEYS）匹配 key 快照 */
    private List<String> scanKeys(String pattern)
    {
        return stringRedisTemplate.execute((RedisCallback<List<String>>) connection ->
        {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_HINT_COUNT).build();
            try (Cursor<byte[]> cursor = connection.scan(options))
            {
                while (cursor.hasNext())
                {
                    keys.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    private String callPresenceKey(Long userId)
    {
        return PRESENCE_PREFIX + userId + ":" + instanceId;
    }

    private String voicePresenceKey(String sessionId, String role)
    {
        return VOICE_PRESENCE_PREFIX + sessionId + ":" + role + ":" + instanceId;
    }

    /** @return 本实例ID（运维排障用） */
    public String getInstanceId()
    {
        return instanceId;
    }

    private static String buildInstanceId()
    {
        String host;
        try
        {
            host = InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e)
        {
            host = "unknown-host";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Pub/Sub 消息信封（Jackson 序列化） */
    public static class Envelope
    {
        /** 消息类别 CU/CB/CH */
        public String category;
        /** 目标：userId（CU）或 sessionId（CH），CB 为 null */
        public String target;
        /** 已序列化的 WebSocket 文本载荷 */
        public String payload;
        /** 发布实例ID，订阅侧用于跳过自发消息 */
        public String origin;
    }
}
