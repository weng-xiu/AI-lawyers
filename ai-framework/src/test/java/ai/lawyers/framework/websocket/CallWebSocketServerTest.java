package ai.lawyers.framework.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 * 呼叫事件 WebSocket 端点测试：握手鉴权矩阵（缺少 userId、鉴权守卫不可用、越权、放行）、
 * 连接登记与关闭注销、定向推送/广播/排空通知投递以及全局在线判定。
 *
 * <p>类内静态注册表在用例间清理；{@link WsClusterRelay} 未启用时静态入口退化为本机直发。</p>
 *
 * @author ai-lawyers
 */
class CallWebSocketServerTest
{
    private static final Long USER_ID = 100L;

    private final CallWebSocketServer server = new CallWebSocketServer();

    private ConcurrentHashMap<Long, CopyOnWriteArraySet<Session>> agents;

    private WebSocketAuthGuard authGuard;

    private Field beanFactoryField;

    private Object originalBeanFactory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception
    {
        ReflectionTestUtils.setField(WsClusterRelay.class, "instance", null);
        agents = (ConcurrentHashMap<Long, CopyOnWriteArraySet<Session>>)
                ReflectionTestUtils.getField(CallWebSocketServer.class, "AGENTS");
        agents.clear();

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
        agents.clear();
        ReflectionTestUtils.setField(WsClusterRelay.class, "instance", null);
        beanFactoryField.set(null, originalBeanFactory);
    }

    @Test
    void onOpen缺少用户ID直接以策略违例关闭() throws Exception
    {
        Session session = newSession("c1");

        server.onOpen(session, null);

        verify(session).close(org.mockito.ArgumentMatchers.argThat(reason -> closeCode(reason) == 1008));
        assertEquals(0, CallWebSocketServer.onlineCount());
    }

    @Test
    void onOpen鉴权守卫不可用时稍后重试关闭() throws Exception
    {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(WebSocketAuthGuard.class)).thenThrow(new RuntimeException("容器未就绪"));
        beanFactoryField.set(null, beanFactory);
        Session session = newSession("c1");

        server.onOpen(session, USER_ID);

        verify(session).close(org.mockito.ArgumentMatchers.argThat(reason -> closeCode(reason) == 1013));
        assertEquals(0, CallWebSocketServer.onlineCount());
    }

    @Test
    void onOpen鉴权失败拒绝连接且不推送消息() throws Exception
    {
        when(authGuard.authorizeCall(USER_ID, "token=abc")).thenReturn(false);
        Session session = newSession("c1");
        RemoteEndpoint.Async async = asyncOf(session);

        server.onOpen(session, USER_ID);

        verify(session).close(org.mockito.ArgumentMatchers.argThat(reason -> closeCode(reason) == 1008));
        verify(async, never()).sendText(anyString());
        assertEquals(0, CallWebSocketServer.onlineCount());
    }

    @Test
    void onOpen鉴权通过登记连接并下发连接成功帧() throws Exception
    {
        when(authGuard.authorizeCall(USER_ID, "token=abc")).thenReturn(true);
        Session session = newSession("c1");
        RemoteEndpoint.Async async = asyncOf(session);

        server.onOpen(session, USER_ID);

        verify(async).sendText("{\"type\":\"CONNECTED\",\"data\":{\"userId\":100}}");
        assertEquals(USER_ID, session.getUserProperties().get("userId"));
        assertEquals(1, CallWebSocketServer.onlineCount());
        assertTrue(CallWebSocketServer.isUserOnlineGlobally(USER_ID));
    }

    @Test
    void onClose移除连接并注销用户() throws Exception
    {
        when(authGuard.authorizeCall(USER_ID, "token=abc")).thenReturn(true);
        Session session = newSession("c1");
        server.onOpen(session, USER_ID);

        server.onClose(session);

        assertEquals(0, CallWebSocketServer.onlineCount());
        assertFalse(CallWebSocketServer.isUserOnlineGlobally(USER_ID));
        assertFalse(agents.containsKey(USER_ID));
    }

    @Test
    void onClose无用户属性的连接静默忽略() throws Exception
    {
        Session session = newSession("c1");
        agents.computeIfAbsent(USER_ID, k -> new CopyOnWriteArraySet<>()).add(session);

        assertDoesNotThrow(() -> server.onClose(session));

        assertEquals(1, CallWebSocketServer.onlineCount());
    }

    @Test
    void 定向推送广播与排空通知覆盖本机全部连接() throws Exception
    {
        when(authGuard.authorizeCall(USER_ID, "token=abc")).thenReturn(true);
        Session first = newSession("c1");
        Session second = newSession("c2");
        RemoteEndpoint.Async firstAsync = asyncOf(first);
        RemoteEndpoint.Async secondAsync = asyncOf(second);
        server.onOpen(first, USER_ID);
        server.onOpen(second, USER_ID);

        CallWebSocketServer.sendToUser(USER_ID, "{\"type\":\"INBOUND_RING\"}");
        CallWebSocketServer.broadcast("{\"type\":\"AGENT_STATUS\"}");
        int delivered = CallWebSocketServer.notifyDrainingLocal();

        verify(firstAsync).sendText("{\"type\":\"INBOUND_RING\"}");
        verify(secondAsync).sendText("{\"type\":\"INBOUND_RING\"}");
        verify(firstAsync).sendText("{\"type\":\"AGENT_STATUS\"}");
        verify(secondAsync).sendText("{\"type\":\"AGENT_STATUS\"}");
        assertEquals(2, delivered);
        verify(firstAsync, org.mockito.Mockito.times(1))
                .sendText(org.mockito.ArgumentMatchers.contains("SERVER_DRAINING"));
        assertEquals(1, CallWebSocketServer.globalOnlineCount());
    }

    @Test
    void 无连接时推送与在线判定均安全退化() throws Exception
    {
        assertDoesNotThrow(() -> CallWebSocketServer.sendToUser(USER_ID, "{}"));
        assertDoesNotThrow(() -> CallWebSocketServer.broadcast("{}"));
        assertEquals(0, CallWebSocketServer.notifyDrainingLocal());
        assertFalse(CallWebSocketServer.isUserOnlineGlobally(null));
        assertFalse(CallWebSocketServer.isUserOnlineGlobally(USER_ID));
        assertEquals(0, CallWebSocketServer.onlineCount());
    }

    @Test
    void onError空会话不抛异常() throws Exception
    {
        assertDoesNotThrow(() -> server.onError(null, new RuntimeException("boom")));
        Session session = newSession("c1");
        assertDoesNotThrow(() -> server.onError(session, new RuntimeException("?token=secret")));
    }

    /** 创建已连接且可发送的会话 */
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

    /** 取会话的异步发送端点（与 newSession 中 stub 为同一实例） */
    private RemoteEndpoint.Async asyncOf(Session session)
    {
        return session.getAsyncRemote();
    }

    /** 读取关闭原因码 */
    private int closeCode(CloseReason reason)
    {
        return reason.getCloseCode().getCode();
    }
}
