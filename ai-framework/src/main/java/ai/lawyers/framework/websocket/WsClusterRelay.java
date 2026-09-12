package ai.lawyers.framework.websocket;

import java.net.InetAddress;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WebSocket 跨实例消息中转（N4）。
 *
 * <p>多实例部署时每个实例只持有连到本机的 WebSocket Session，本机静态注册表无法覆盖
 * 其他实例上的坐席。本组件通过 Redis Pub/Sub 做两件事：</p>
 * <ol>
 *   <li><b>跨实例 fan-out</b>：任一点产生的推送发布到统一频道 {@value #CHANNEL}，
 *       所有实例订阅；收到后仅投递给本机持有的连接，来源实例跳过自己发出的消息
 *       （避免与本地直发重复）。</li>
 *   <li><b>在线注册表</b>：坐席签入后在 Redis 写 {@code ai-law:ws:presence:call:{userId}}
 *       （值为实例ID，带 TTL），心跳定时续期，最后一条本地连接关闭时 CAS 删除，
 *       供运维从 Redis 侧观察全局在线情况。</li>
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

    /** 呼叫坐席在线注册表 key 前缀（key = 前缀 + userId） */
    private static final String PRESENCE_PREFIX = "ai-law:ws:presence:call:";

    /** 在线键 TTL（秒），必须大于心跳间隔 */
    private static final long PRESENCE_TTL_SECONDS = 60;

    /** 心跳续期间隔（秒） */
    private static final long HEARTBEAT_SECONDS = 20;

    /** 消息类别：呼叫定向推送给某 userId */
    private static final String CAT_CALL_USER = "CU";
    /** 消息类别：呼叫全员广播 */
    private static final String CAT_CALL_BROADCAST = "CB";
    /** 消息类别：图文会话房间广播 */
    private static final String CAT_CHAT = "CH";

    /** 仅当键值等于本实例ID时删除（CAS），避免删掉其他实例的在线标记 */
    private static final DefaultRedisScript<Long> RELEASE_PRESENCE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

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

    // ===================== 在线注册表 =====================

    /** 坐席在本机建立连接时登记（覆盖写+TTL，后续由心跳续期） */
    public void registerCallUser(Long userId)
    {
        if (userId == null) return;
        try
        {
            stringRedisTemplate.opsForValue().set(presenceKey(userId), instanceId,
                    PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 在线登记失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 本实例该坐席最后一条连接关闭时，仅当标记仍属于本实例才删除 */
    public void unregisterCallUser(Long userId)
    {
        if (userId == null) return;
        try
        {
            stringRedisTemplate.execute(RELEASE_PRESENCE_SCRIPT,
                    java.util.Collections.singletonList(presenceKey(userId)), instanceId);
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 在线注销失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 为当前本机所有在线坐席续期（在线标记只要求"任一实例在线"，覆盖写本实例ID即可） */
    private void refreshLocalPresence()
    {
        try
        {
            for (Long userId : CallWebSocketServer.localUserIds())
            {
                stringRedisTemplate.expire(presenceKey(userId), PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
            }
        }
        catch (Exception e)
        {
            log.warn("[WsCluster] 在线心跳续期失败: {}", e.getMessage());
        }
    }

    private static String presenceKey(Long userId)
    {
        return PRESENCE_PREFIX + userId;
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
