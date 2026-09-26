package ai.lawyers.framework.websocket;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.spring.SpringUtils;

/**
 * 呼叫事件 WebSocket 服务端
 *
 * <p>路径：{@code /ws/call/{userId}}，坐席工作台在签入后建立此连接，
 * 后端通过该通道实时推送：来电弹屏（INBOUND_RING）、坐席状态变更、
 * 通话接通/挂断、排队通知、系统广播等，替代前端 5 秒轮询。</p>
 *
 * <p>消息为 JSON 文本，格式：{@code {"type":"...","data":{...}}}。</p>
 *
 * @author ai-lawyers
 */
@Component
@ServerEndpoint("/ws/call/{userId}")
public class CallWebSocketServer
{
    private static final Logger log = LoggerFactory.getLogger(CallWebSocketServer.class);

    /** userId -> 该用户的全部连接（支持多端同时在线） */
    private static final ConcurrentHashMap<Long, CopyOnWriteArraySet<Session>> AGENTS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId)
    {
        if (userId == null)
        {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "missing userId");
            return;
        }
        // N5：握手期 JWT 鉴权 + userId 归属校验（query 传 token；websocket.auth.enabled=false 时应急放行）
        WebSocketAuthGuard authGuard;
        try
        {
            authGuard = SpringUtils.getBean(WebSocketAuthGuard.class);
        }
        catch (Exception e)
        {
            // Spring 容器未就绪（极端启动时序）时保守拒绝，避免无鉴权裸奔
            log.warn("CallWS 鉴权守卫不可用，拒绝连接 connId={}: {}", session.getId(), e.getMessage());
            closeQuietly(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "auth unavailable");
            return;
        }
        if (!authGuard.authorizeCall(userId, session.getQueryString()))
        {
            log.warn("CallWS 拒绝未授权连接 userId={}, connId={}", userId, session.getId());
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized");
            return;
        }
        AGENTS.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        session.getUserProperties().put("userId", userId);
        log.info("CallWS connect: userId={}, connId={}, online={}",
                userId, session.getId(), AGENTS.get(userId).size());
        // N4：登记到集群在线注册表（集群关闭时为空操作）
        WsClusterRelay relay = WsClusterRelay.getInstance();
        if (relay != null)
        {
            relay.registerCallUser(userId);
        }
        send(session, "{\"type\":\"CONNECTED\",\"data\":{\"userId\":" + userId + "}}");
    }

    @OnClose
    public void onClose(Session session)
    {
        Object userIdObj = session.getUserProperties().get("userId");
        if (!(userIdObj instanceof Long)) return;
        Long userId = (Long) userIdObj;
        CopyOnWriteArraySet<Session> conns = AGENTS.get(userId);
        boolean localEmpty = true;
        if (conns != null)
        {
            conns.remove(session);
            if (conns.isEmpty())
            {
                AGENTS.remove(userId);
            }
            else
            {
                localEmpty = false;
            }
        }
        // N4：本实例该坐席最后一条连接关闭时注销全局在线标记
        if (localEmpty)
        {
            WsClusterRelay relay = WsClusterRelay.getInstance();
            if (relay != null)
            {
                relay.unregisterCallUser(userId);
            }
        }
        log.info("CallWS close: userId={}, connId={}", userId, session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error)
    {
        log.error("CallWS error: connId={}, msg={}",
                session == null ? "" : session.getId(),
                WebSocketAuthGuard.maskQueryToken(error.getMessage()));
    }

    /**
     * 推送给指定坐席。N4：先发本机连接，再经 Redis 频道扇出给其他实例。
     */
    public static void sendToUser(Long userId, String json)
    {
        if (userId == null) return;
        sendToUserLocal(userId, json);
        WsClusterRelay relay = WsClusterRelay.getInstance();
        if (relay != null)
        {
            relay.publishCallUser(userId, json);
        }
    }

    /**
     * 广播给所有在线坐席。N4：本机广播 + Redis 频道跨实例广播。
     */
    public static void broadcast(String json)
    {
        broadcastLocal(json);
        WsClusterRelay relay = WsClusterRelay.getInstance();
        if (relay != null)
        {
            relay.publishCallBroadcast(json);
        }
    }

    /**
     * 仅投递给本机持有的指定坐席连接（供集群订阅回调使用，避免再次发布造成回环）。
     */
    static void sendToUserLocal(Long userId, String json)
    {
        if (userId == null) return;
        CopyOnWriteArraySet<Session> conns = AGENTS.get(userId);
        if (conns == null || conns.isEmpty()) return;
        for (Session s : conns)
        {
            send(s, json);
        }
    }

    /**
     * 仅向本机全部坐席连接广播（供集群订阅回调使用）。
     */
    static void broadcastLocal(String json)
    {
        for (CopyOnWriteArraySet<Session> conns : AGENTS.values())
        {
            for (Session s : conns)
            {
                send(s, json);
            }
        }
    }

    /**
     * 当前本机在线坐席数。
     */
    public static int onlineCount()
    {
        return AGENTS.size();
    }

    /**
     * P3-C2：全集群在线判定——本机持有连接直接 true，否则查 Redis presence。
     * 未开启集群时等价于本机判定。
     */
    public static boolean isUserOnlineGlobally(Long userId)
    {
        if (userId == null) return false;
        if (AGENTS.containsKey(userId)) return true;
        WsClusterRelay relay = WsClusterRelay.getInstance();
        return relay != null && relay.isCallUserOnline(userId);
    }

    /**
     * P3-C2：全集群去重在线坐席数。未开启集群时返回本机在线数。
     */
    public static int globalOnlineCount()
    {
        WsClusterRelay relay = WsClusterRelay.getInstance();
        return relay == null ? AGENTS.size() : relay.globalOnlineCount();
    }

    /**
     * N4：本实例当前持有连接的坐席ID快照，供集群在线注册表心跳续期使用。
     */
    static java.util.Set<Long> localUserIds()
    {
        return new java.util.HashSet<>(AGENTS.keySet());
    }

    private static void send(Session session, String json)
    {
        try
        {
            if (session != null && session.isOpen())
            {
                session.getAsyncRemote().sendText(json);
            }
        }
        catch (Exception e)
        {
            log.error("CallWS send fail: connId={}", session == null ? "" : session.getId(), e);
        }
    }

    /** N5：鉴权失败以策略违例关闭握手连接，异常不外抛 */
    private static void closeQuietly(Session session, CloseReason.CloseCode code, String reason)
    {
        try
        {
            session.close(new CloseReason(code, reason));
        }
        catch (IOException | IllegalStateException e)
        {
            // 连接可能已关闭，忽略
        }
    }
}
