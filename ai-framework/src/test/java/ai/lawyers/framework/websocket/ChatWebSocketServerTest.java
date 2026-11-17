package ai.lawyers.framework.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.utils.spring.SpringUtils;

/**
 * 图文会话 WebSocket 端点测试：握手鉴权矩阵、房间登记与清理、房间广播、
 * 排空通知以及无连接场景的安全退化。
 *
 * @author ai-lawyers
 */
class ChatWebSocketServerTest
{
    private static final String SESSION_ID = "s1";

    private final ChatWebSocketServer server = new ChatWebSocketServer();

    private ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> rooms;

    private WebSocketAuthGuard authGuard;

    private Field beanFactoryField;

    private Object originalBeanFactory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception
    {
        ReflectionTestUtils.setField(WsClusterRelay.class, "instance", null);
        rooms = (ConcurrentHashMap<String, CopyOnWriteArraySet<Session>>)
                ReflectionTestUtils.getField(ChatWebSocketServer.class, "ROOMS");
        rooms.clear();

        authGuard = mock(WebSocketAuthGuard.class);
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(WebSocketAuthGuard.class)).thenReturn(authGuard);
        beanFactoryField = SpringUtils.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        originalBeanFactory = beanFactoryField.get(null);
        beanFactoryField.set(null, beanFactory);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        rooms.clear();
        ReflectionTestUtils.setField(WsClusterRelay.class, "instance", null);
        beanFactoryField.set(null, originalBeanFactory);
    }

    @Test
    void onOpen缺少会话ID直接以策略违例关闭() throws Exception
    {
        Session session = newSession("c1");

        server.onOpen(session, null);

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
        assertEquals(0, ChatWebSocketServer.getRoomSize(SESSION_ID));
    }

    @Test
    void onOpen鉴权守卫不可用时稍后重试关闭() throws Exception
    {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(WebSocketAuthGuard.class)).thenThrow(new RuntimeException("容器未就绪"));
        beanFactoryField.set(null, beanFactory);
        Session session = newSession("c1");

        server.onOpen(session, SESSION_ID);

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1013));
    }

    @Test
    void onOpen鉴权失败不加入房间() throws Exception
    {
        when(authGuard.authorizeChat("token=abc")).thenReturn(false);
        Session session = newSession("c1");

        server.onOpen(session, SESSION_ID);

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
        assertEquals(0, ChatWebSocketServer.getRoomSize(SESSION_ID));
    }

    @Test
    void onOpen鉴权通过加入房间并可按会话广播() throws Exception
    {
        when(authGuard.authorizeChat("token=abc")).thenReturn(true);
        Session session = newSession("c1");
        RemoteEndpoint.Async async = session.getAsyncRemote();

        server.onOpen(session, SESSION_ID);
        ChatWebSocketServer.broadcast(SESSION_ID, "{\"type\":\"msg\"}");

        assertEquals(1, ChatWebSocketServer.getRoomSize(SESSION_ID));
        verify(async).sendText("{\"type\":\"msg\"}");
    }

    @Test
    void onClose移除连接并清理空房间() throws Exception
    {
        when(authGuard.authorizeChat("token=abc")).thenReturn(true);
        Session session = newSession("c1");
        server.onOpen(session, SESSION_ID);

        server.onClose(session, SESSION_ID);

        assertEquals(0, ChatWebSocketServer.getRoomSize(SESSION_ID));
        assertTrue(rooms.isEmpty());
    }

    @Test
    void 排空通知仅统计已打开连接() throws Exception
    {
        Session open = newSession("c1");
        Session closed = newSession("c2");
        when(closed.isOpen()).thenReturn(false);
        rooms.computeIfAbsent(SESSION_ID, k -> new CopyOnWriteArraySet<>()).add(open);
        rooms.computeIfAbsent(SESSION_ID, k -> new CopyOnWriteArraySet<>()).add(closed);

        int delivered = ChatWebSocketServer.notifyDrainingLocal();

        assertEquals(1, delivered);
        verify(open.getAsyncRemote(), times(1)).sendText(contains("SERVER_DRAINING"));
        verify(closed.getAsyncRemote(), never()).sendText(anyString());
    }

    @Test
    void 空会话与空会话ID广播安全退化() throws Exception
    {
        assertDoesNotThrow(() -> ChatWebSocketServer.broadcast(null, "{}"));
        assertDoesNotThrow(() -> ChatWebSocketServer.broadcast("unknown", "{}"));
        assertEquals(0, ChatWebSocketServer.getRoomSize("unknown"));
        assertEquals(0, ChatWebSocketServer.notifyDrainingLocal());
    }

    @Test
    void onError空会话不抛异常() throws Exception
    {
        assertDoesNotThrow(() -> server.onError(null, new RuntimeException("boom")));
        Session session = newSession("c1");
        assertDoesNotThrow(() -> server.onError(session, new RuntimeException("?token=secret")));
    }

    /** 创建可发送消息的会话 */
    private Session newSession(String connId)
    {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(connId);
        when(session.getQueryString()).thenReturn("token=abc");
        when(session.isOpen()).thenReturn(true);
        when(session.getAsyncRemote()).thenReturn(mock(RemoteEndpoint.Async.class));
        when(session.getUserProperties()).thenReturn(new HashMap<String, Object>());
        return session;
    }
}
