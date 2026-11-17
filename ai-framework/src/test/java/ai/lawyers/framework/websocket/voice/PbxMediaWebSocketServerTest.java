package ai.lawyers.framework.websocket.voice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * PBX 媒体接入端点测试：recordId/开关/共享密钥校验、拒绝原因计数、文本与二进制帧转发、
 * 已受理连接关闭指标分类、异常原因标记以及活跃 fork 计数。
 *
 * @author ai-lawyers
 */
class PbxMediaWebSocketServerTest
{
    private static final String RECORD_ID = "1001";

    private static final String CONN_ID = "c1";

    private final PbxMediaWebSocketServer server = new PbxMediaWebSocketServer();

    private PbxMediaForkProperties properties;

    private VoiceSessionManager manager;

    private HotlineMetrics metrics;

    private Field beanFactoryField;

    private Object originalBeanFactory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception
    {
        properties = mock(PbxMediaForkProperties.class);
        manager = mock(VoiceSessionManager.class);
        metrics = mock(HotlineMetrics.class);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getAuthKey()).thenReturn("");

        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(PbxMediaForkProperties.class)).thenReturn(properties);
        when(beanFactory.getBean(VoiceSessionManager.class)).thenReturn(manager);
        when(beanFactory.getBean(HotlineMetrics.class)).thenReturn(metrics);
        beanFactoryField = SpringUtils.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        originalBeanFactory = beanFactoryField.get(null);
        beanFactoryField.set(null, beanFactory);

        ((java.util.Set<PbxMediaForkHandler>) ReflectionTestUtils.getField(
                PbxMediaWebSocketServer.class, "ACTIVE_HANDLERS")).clear();
        ((AtomicInteger) ReflectionTestUtils.getField(PbxMediaWebSocketServer.class, "ACTIVE_COUNT")).set(0);
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void tearDown() throws Exception
    {
        ((java.util.Set<PbxMediaForkHandler>) ReflectionTestUtils.getField(
                PbxMediaWebSocketServer.class, "ACTIVE_HANDLERS")).clear();
        ((AtomicInteger) ReflectionTestUtils.getField(PbxMediaWebSocketServer.class, "ACTIVE_COUNT")).set(0);
        beanFactoryField.set(null, originalBeanFactory);
    }

    @Test
    void onOpen非数字话单ID拒绝并计数() throws Exception
    {
        Session session = newSession();

        server.onOpen(session, "abc");

        verify(metrics).incrementPbxReject("bad_record");
        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
    }

    @Test
    void onOpen容器未就绪时稍后重试关闭() throws Exception
    {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(PbxMediaForkProperties.class)).thenThrow(new RuntimeException("容器未就绪"));
        beanFactoryField.set(null, beanFactory);
        Session session = newSession();

        server.onOpen(session, RECORD_ID);

        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1013));
    }

    @Test
    void onOpen媒体开关关闭时拒绝() throws Exception
    {
        when(properties.isEnabled()).thenReturn(false);
        Session session = newSession();

        server.onOpen(session, RECORD_ID);

        verify(metrics).incrementPbxReject("disabled");
        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1013));
    }

    @Test
    void onOpen共享密钥不匹配拒绝() throws Exception
    {
        when(properties.getAuthKey()).thenReturn("right");
        Session session = newSession("key=wrong");

        server.onOpen(session, RECORD_ID);

        verify(metrics).incrementPbxReject("auth_fail");
        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
    }

    @Test
    void onOpen受理连接并注入处理器()
    {
        Session session = newSession("key=any");

        server.onOpen(session, RECORD_ID);

        assertTrue(session.getUserProperties().get("pbxHandler") instanceof PbxMediaForkHandler);
        assertEquals(Boolean.TRUE, session.getUserProperties().get("pbxAccepted"));
        verify(metrics).incrementPbxConnect();
    }

    @Test
    void 共享密钥校验覆盖空配置与匹配场景()
    {
        assertTrue(PbxMediaWebSocketServer.checkAuthKey(null, ""));
        assertTrue(PbxMediaWebSocketServer.checkAuthKey(null, null));
        assertFalse(PbxMediaWebSocketServer.checkAuthKey(null, "key"));
        assertFalse(PbxMediaWebSocketServer.checkAuthKey("other=1", "key"));
        assertTrue(PbxMediaWebSocketServer.checkAuthKey("key=abc", "abc"));
        assertTrue(PbxMediaWebSocketServer.checkAuthKey("key=a%20b", "a b"));
    }

    @Test
    void 非法文本帧以策略违例关闭并记录原因() throws Exception
    {
        PbxMediaForkHandler handler = mock(PbxMediaForkHandler.class);
        when(handler.handleText("bad")).thenReturn(false);
        Session session = newSession();
        session.getUserProperties().put("pbxHandler", handler);

        server.onTextMessage("bad", session);

        assertEquals("bad_frame", session.getUserProperties().get("pbxCloseReason"));
        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
    }

    @Test
    void 合法文本帧不关闭连接() throws Exception
    {
        PbxMediaForkHandler handler = mock(PbxMediaForkHandler.class);
        when(handler.handleText("ok")).thenReturn(true);
        Session session = newSession();
        session.getUserProperties().put("pbxHandler", handler);

        server.onTextMessage("ok", session);

        verify(session, never()).close(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 二进制帧转为PCM交处理器且空帧忽略()
    {
        PbxMediaForkHandler handler = mock(PbxMediaForkHandler.class);
        Session session = newSession();
        session.getUserProperties().put("pbxHandler", handler);

        server.onBinaryMessage(ByteBuffer.wrap(new byte[] { 9, 8 }), session);
        server.onBinaryMessage(null, session);

        verify(handler).handleBinary(argThat(pcm -> java.util.Arrays.equals(pcm, new byte[] { 9, 8 })));
        verify(handler, org.mockito.Mockito.times(1)).handleBinary(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onClose已受理连接记录生命周期指标()
    {
        PbxMediaForkHandler handler = mock(PbxMediaForkHandler.class);
        when(handler.metrics()).thenReturn(metrics);
        when(handler.isStopReceived()).thenReturn(true);
        Session session = newSession();
        session.getUserProperties().put("pbxHandler", handler);
        session.getUserProperties().put("pbxAccepted", Boolean.TRUE);

        server.onClose(session);

        verify(handler).finish();
        verify(manager).unregister(CONN_ID);
        verify(metrics).recordPbxSession(anyLong());
        verify(metrics).incrementPbxClose("stop");
    }

    @Test
    void onClose未受理连接不记录指标()
    {
        Session session = newSession();

        server.onClose(session);

        verify(manager).unregister(CONN_ID);
        verify(metrics, never()).recordPbxSession(anyLong());
        verify(metrics, never()).incrementPbxClose(anyString());
    }

    @Test
    void onError标记异常原因且不覆盖已有原因()
    {
        Session session = newSession();
        session.getUserProperties().put("pbxAccepted", Boolean.TRUE);

        server.onError(session, new RuntimeException("io"));
        assertEquals("error", session.getUserProperties().get("pbxCloseReason"));

        session.getUserProperties().put("pbxCloseReason", "bad_frame");
        server.onError(session, new RuntimeException("io"));
        assertEquals("bad_frame", session.getUserProperties().get("pbxCloseReason"));
        assertDoesNotThrow(() -> server.onError(null, new RuntimeException("io")));
    }

    @Test
    void 活跃fork计数随开始与关闭收敛()
    {
        PbxMediaForkHandler handler = mock(PbxMediaForkHandler.class);

        PbxMediaWebSocketServer.onForkStarted(handler);
        assertEquals(1, PbxMediaWebSocketServer.activeCount());

        PbxMediaWebSocketServer.onForkClosed(handler);
        assertEquals(0, PbxMediaWebSocketServer.activeCount());

        // 重复关闭不产生负数
        PbxMediaWebSocketServer.onForkClosed(handler);
        assertEquals(0, PbxMediaWebSocketServer.activeCount());
    }

    /** 创建带用户属性表的会话 */
    private Session newSession()
    {
        return newSession(null);
    }

    /** 创建带查询串的会话 */
    private Session newSession(String queryString)
    {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(CONN_ID);
        when(session.getQueryString()).thenReturn(queryString);
        when(session.isOpen()).thenReturn(true);
        when(session.getUserProperties()).thenReturn(new HashMap<String, Object>());
        return session;
    }
}
