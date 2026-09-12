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
 * 图文对话WebSocket服务端
 * 路径：/ws/chat/{sessionId}
 * 同一会话下的所有连接组成一个房间，broadcast 推送给该房间内全部连接。
 *
 * @author ai-lawyers
 */
@Component
@ServerEndpoint("/ws/chat/{sessionId}")
public class ChatWebSocketServer
{
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketServer.class);

    /** 会话房间：sessionId -> 连接集合 */
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> ROOMS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") String sessionId)
    {
        if (sessionId == null) {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "missing sessionId");
            return;
        }
        // N5：握手期 JWT 鉴权（query 传 token；websocket.auth.enabled=false 时应急放行）
        WebSocketAuthGuard authGuard;
        try
        {
            authGuard = SpringUtils.getBean(WebSocketAuthGuard.class);
        }
        catch (Exception e)
        {
            log.warn("ChatWS 鉴权守卫不可用，拒绝连接 connId={}: {}", session.getId(), e.getMessage());
            closeQuietly(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "auth unavailable");
            return;
        }
        if (!authGuard.authorizeChat(session.getQueryString()))
        {
            log.warn("ChatWS 拒绝未授权连接 sessionId={}, connId={}", sessionId, session.getId());
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized");
            return;
        }
        ROOMS.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("ChatWS connect: sessionId={}, connId={}, roomSize={}", sessionId, session.getId(), ROOMS.get(sessionId).size());
    }

    @OnClose
    public void onClose(Session session, @PathParam("sessionId") String sessionId)
    {
        if (sessionId == null) {
            return;
        }
        CopyOnWriteArraySet<Session> room = ROOMS.get(sessionId);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) {
                ROOMS.remove(sessionId);
            }
        }
        log.info("ChatWS close: sessionId={}, connId={}", sessionId, session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error)
    {
        log.error("ChatWS error: connId={}, msg={}", session == null ? "" : session.getId(),
                WebSocketAuthGuard.maskQueryToken(error.getMessage()));
    }

    /**
     * 向指定会话房间广播消息（供 REST 控制器调用）。
     * N4：先发本机房间连接，再经 Redis 频道扇出给其他实例。
     */
    public static void broadcast(String sessionId, String message)
    {
        if (sessionId == null) {
            return;
        }
        broadcastLocal(sessionId, message);
        WsClusterRelay relay = WsClusterRelay.getInstance();
        if (relay != null) {
            relay.publishChat(sessionId, message);
        }
    }

    /**
     * 仅向本机持有的房间连接广播（供集群订阅回调使用，避免回环）。
     */
    static void broadcastLocal(String sessionId, String message)
    {
        if (sessionId == null) {
            return;
        }
        CopyOnWriteArraySet<Session> room = ROOMS.get(sessionId);
        if (room == null || room.isEmpty()) {
            return;
        }
        for (Session s : room) {
            try {
                if (s.isOpen()) {
                    s.getAsyncRemote().sendText(message);
                }
            } catch (Exception e) {
                log.error("ChatWS broadcast fail: sessionId={}, connId={}", sessionId, s.getId(), e);
            }
        }
    }

    public static int getRoomSize(String sessionId)
    {
        CopyOnWriteArraySet<Session> room = ROOMS.get(sessionId);
        return room == null ? 0 : room.size();
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
