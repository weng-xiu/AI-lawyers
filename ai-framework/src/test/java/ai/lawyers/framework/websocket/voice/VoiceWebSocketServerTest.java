package ai.lawyers.framework.websocket.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;

import jakarta.websocket.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.framework.websocket.WebSocketAuthGuard;

/**
 * 流式语音 WebSocket 端点测试：路径参数校验、握手鉴权与容量拒绝、
 * 坐席身份注入、文本/二进制帧转发以及关闭时的会话释放。
 *
 * @author ai-lawyers
 */
class VoiceWebSocketServerTest
{
    private static final String SESSION_ID = "s1";

    private static final String CONN_ID = "c1";

    private final VoiceWebSocketServer server = new VoiceWebSocketServer();

    private WebSocketAuthGuard authGuard;

    private VoiceSessionManager manager;

    private VoiceSession voiceSession;

    private Field beanFactoryField;

    private Object originalBeanFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        authGuard = mock(WebSocketAuthGuard.class);
        manager = mock(VoiceSessionManager.class);
        voiceSession = mock(VoiceSession.class);

        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(WebSocketAuthGuard.class)).thenReturn(authGuard);
        when(beanFactory.getBean(VoiceSessionManager.class)).thenReturn(manager);
        beanFactoryField = SpringUtils.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        originalBeanFactory = beanFactoryField.get(null);
        beanFactoryField.set(null, beanFactory);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        beanFactoryField.set(null, originalBeanFactory);
    }

    @Test
    void onOpen缺少会话ID以策略违例关闭() throws Exception
    {
        Session session = newSession();

        server.onOpen(session, " ", "agent");

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
    }

    @Test
    void onOpen角色非法以策略违例关闭() throws Exception
    {
        Session session = newSession();

        server.onOpen(session, SESSION_ID, "robot");

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
    }

    @Test
    void onOpen容器未就绪时稍后重试关闭() throws Exception
    {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(WebSocketAuthGuard.class)).thenThrow(new RuntimeException("容器未就绪"));
        beanFactoryField.set(null, beanFactory);
        Session session = newSession();

        server.onOpen(session, SESSION_ID, "agent");

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1013));
    }

    @Test
    void onOpen鉴权失败以策略违例关闭() throws Exception
    {
        when(authGuard.authorizeVoice("token=abc")).thenReturn(false);
        Session session = newSession();

        server.onOpen(session, SESSION_ID, "agent");

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
    }

    @Test
    void onOpen容量或开关拒绝时稍后重试关闭() throws Exception
    {
        when(authGuard.authorizeVoice("token=abc")).thenReturn(true);
        when(manager.register(org.mockito.ArgumentMatchers.any(Session.class), eq(SESSION_ID), eq("agent"),
                org.mockito.ArgumentMatchers.nullable(Long.class))).thenReturn(null);
        Session session = newSession();

        server.onOpen(session, SESSION_ID, "agent");

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1013));
    }

    @Test
    void onOpen注册成功注入会话并携带坐席ID() throws Exception
    {
        when(authGuard.authorizeVoice("token=abc")).thenReturn(true);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        when(authGuard.resolveVoiceLoginUser("token=abc")).thenReturn(loginUser);
        when(manager.register(org.mockito.ArgumentMatchers.any(Session.class), eq(SESSION_ID), eq("agent"),
                eq(7L))).thenReturn(voiceSession);
        Session session = newSession();

        server.onOpen(session, SESSION_ID, "agent");

        assertSame(voiceSession, session.getUserProperties().get("voiceSession"));
        verify(manager).register(session, SESSION_ID, "agent", 7L);
    }

    @Test
    void 文本帧转交给语音会话()
    {
        Session session = newSession();
        session.getUserProperties().put("voiceSession", voiceSession);

        server.onTextMessage("{\"type\":\"start\"}", session);

        verify(voiceSession).handleText("{\"type\":\"start\"}");
    }

    @Test
    void 二进制帧解码为PCM转交语音会话()
    {
        Session session = newSession();
        session.getUserProperties().put("voiceSession", voiceSession);
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] { 1, 2, 3 });

        server.onBinaryMessage(buffer, session);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(voiceSession).handleBinary(captor.capture());
        assertArrayEquals(new byte[] { 1, 2, 3 }, captor.getValue());
    }

    @Test
    void 无会话时文本与二进制帧被忽略()
    {
        Session session = newSession();

        assertDoesNotThrow(() -> server.onTextMessage("x", session));
        assertDoesNotThrow(() -> server.onBinaryMessage(ByteBuffer.wrap(new byte[] { 1 }), session));
        assertDoesNotThrow(() -> server.onBinaryMessage(null, session));
    }

    @Test
    void onClose注销会话()
    {
        Session session = newSession();

        server.onClose(session);

        verify(manager).unregister(CONN_ID);
    }

    @Test
    void onClose容器销毁时兜底关闭语音会话() throws Exception
    {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(VoiceSessionManager.class)).thenThrow(new RuntimeException("bean 已销毁"));
        beanFactoryField.set(null, beanFactory);
        Session session = newSession();
        session.getUserProperties().put("voiceSession", voiceSession);

        server.onClose(session);

        verify(voiceSession).shutdown();
    }

    @Test
    void onError空会话不抛异常()
    {
        assertDoesNotThrow(() -> server.onError(null, new RuntimeException("boom")));
    }

    /** 创建带用户属性表的会话 */
    private Session newSession()
    {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(CONN_ID);
        when(session.getQueryString()).thenReturn("token=abc");
        when(session.isOpen()).thenReturn(true);
        when(session.getUserProperties()).thenReturn(new HashMap<String, Object>());
        return session;
    }
}
