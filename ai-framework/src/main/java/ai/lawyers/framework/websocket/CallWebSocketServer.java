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
        send(session, "{\"type\":\"CONNECTED\",\"data\":{\"userId\":" + userId + "}}");
    }

    @OnClose
    public void onClose(Session session)
    {
        Object userIdObj = session.getUserProperties().get("userId");
        if (!(userIdObj instanceof Long)) return;
        Long userId = (Long) userIdObj;
        CopyOnWriteArraySet<Session> conns = AGENTS.get(userId);
        if (conns != null)
        {
            conns.remove(session);
            if (conns.isEmpty())
            {
                AGENTS.remove(userId);
            }
        }
        log.info("CallWS close: userId={}, connId={}", userId, session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error)
    {
        log.error("CallWS error: connId={}, msg={}",
                session == null ? "" : session.getId(), error.getMessage());
    }

    /**
     * 推送给指定坐席。
     */
    public static void sendToUser(Long userId, String json)
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
     * 广播给所有在线坐席。
     */
    public static void broadcast(String json)
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
     * 当前在线坐席数。
     */
    public static int onlineCount()
    {
        return AGENTS.size();
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
