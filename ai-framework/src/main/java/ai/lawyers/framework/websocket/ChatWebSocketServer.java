package ai.lawyers.framework.websocket;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

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
        log.error("ChatWS error: connId={}, msg={}", session == null ? "" : session.getId(), error.getMessage());
    }

    /**
     * 向指定会话房间广播消息（供 REST 控制器调用）
     */
    public static void broadcast(String sessionId, String message)
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
}
