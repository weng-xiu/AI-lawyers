package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link VoiceSessionManager} 注册/容量/释放测试（P3-A1）。
 *
 * @author ai-lawyers
 */
class VoiceSessionManagerTest
{
    private VoiceSessionManager manager;

    @BeforeEach
    void setUp()
    {
        manager = new VoiceSessionManager();
        ReflectionTestUtils.setField(manager, "enabled", true);
        ReflectionTestUtils.setField(manager, "maxSessions", 2);
        ReflectionTestUtils.setField(manager, "sendQueueCapacity", 100);
    }

    @Test
    void register_upToCapacity_thenReject_andUnregisterReleases() throws Exception
    {
        Session s1 = mockSession("k1");
        Session s2 = mockSession("k2");
        Session s3 = mockSession("k3");

        VoiceSession v1 = manager.register(s1, "sess", "agent");
        VoiceSession v2 = manager.register(s2, "sess", "caller");
        assertThat(v1).isNotNull();
        assertThat(v2).isNotNull();
        assertThat(manager.count()).isEqualTo(2);

        // 超容量拒连（端点据此回 1013）
        assertThat(manager.register(s3, "sess", "agent")).isNull();

        assertThat(manager.get("k1")).isSameAs(v1);

        manager.unregister("k1");
        manager.unregister("k1"); // 幂等
        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.get("k1")).isNull();

        // 释放后容量空出可再注册
        assertThat(manager.register(s3, "sess2", "agent")).isNotNull();
        v2.shutdown();
        manager.destroy();
        assertThat(manager.count()).isZero();
    }

    @Test
    void disabled_registerRejected()
    {
        manager.setEnabled(false);
        Session s = mockSession("k9");
        assertThat(manager.register(s, "sess", "agent")).isNull();
        assertThat(manager.count()).isZero();
    }

    private Session mockSession(String connId)
    {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.getId()).thenReturn(connId);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(basic);
        return session;
    }
}
