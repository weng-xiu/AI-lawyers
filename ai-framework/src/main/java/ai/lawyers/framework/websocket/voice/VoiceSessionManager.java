package ai.lawyers.framework.websocket.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.framework.websocket.WsClusterRelay;

/**
 * /ws/voice 本机连接注册表（P3-A1）。
 *
 * <p>key=WebSocket connId（{@code Session.getId()}）；注册即创建
 * {@link VoiceSession}（含保序发送线程），注销幂等释放全部引擎句柄。
 * P3-C2：注册/注销同步外置到 Redis 语音会话注册表（经 {@link WsClusterRelay}，
 * 集群未开启时空操作），供跨实例锚定查询；二进制帧跨实例转发仍随 C6。</p>
 *
 * <p>配置：{@code websocket.voice.enabled}（默认 true，关闭时拒连）、
 * {@code websocket.voice.max-sessions}（默认 200，超限拒连保护线程/内存）、
 * {@code websocket.voice.send-queue-capacity}（默认 1000）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class VoiceSessionManager
{
    private static final Logger log = LoggerFactory.getLogger(VoiceSessionManager.class);

    private final ConcurrentHashMap<String, VoiceSession> sessions = new ConcurrentHashMap<>();

    @Value("${websocket.voice.enabled:true}")
    private boolean enabled;

    @Value("${websocket.voice.max-sessions:200}")
    private int maxSessions;

    @Value("${websocket.voice.send-queue-capacity:1000}")
    private int sendQueueCapacity;

    @PostConstruct
    public void init()
    {
        log.info("流式语音端点 /ws/voice 初始化 enabled={}, maxSessions={}, queueCap={}（A1：仅 Mock 引擎，真实 Provider 随 A2/A3）",
                enabled, maxSessions, sendQueueCapacity);
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * 注册并初始化语音会话。
     *
     * @return 会话实例；功能关闭或超容量时返回 null（端点以 1013/1008 关闭）
     */
    public VoiceSession register(Session wsSession, String sessionId, String role)
    {
        if (!enabled)
        {
            log.warn("VoiceWS 功能关闭，拒绝连接 sessionId={}, connId={}", sessionId, wsSession.getId());
            return null;
        }
        if (sessions.size() >= maxSessions)
        {
            log.warn("VoiceWS 连接数达上限 {}，拒绝 connId={}", maxSessions, wsSession.getId());
            return null;
        }
        VoiceSession session = new VoiceSession(wsSession, sessionId, role, sendQueueCapacity);
        VoiceSession old = sessions.putIfAbsent(wsSession.getId(), session);
        if (old != null)
        {
            // 同一 connId 理论不会重复注册；防御性关闭新会话
            session.shutdown();
            return old;
        }
        session.sendConnected();
        WsClusterRelay relay = WsClusterRelay.getInstance();
        if (relay != null)
        {
            relay.registerVoice(sessionId, role, wsSession.getId());
        }
        log.info("VoiceWS connect sessionId={}, role={}, connId={}, online={}",
                sessionId, role, wsSession.getId(), sessions.size());
        return session;
    }

    /** 注销并释放；幂等（重复调用/不存在均无副作用） */
    public void unregister(String connId)
    {
        VoiceSession session = sessions.remove(connId);
        if (session != null)
        {
            WsClusterRelay relay = WsClusterRelay.getInstance();
            if (relay != null)
            {
                relay.unregisterVoice(session.getSessionId(), session.getRole());
            }
            session.shutdown();
            log.info("VoiceWS disconnect connId={}, remaining={}", connId, sessions.size());
        }
    }

    /**
     * P3-C2：本机当前持有的语音会话引用快照（sessionId+role），供 presence 心跳续期。
     */
    public static List<VoiceRef> localVoiceRefs()
    {
        return getCurrentManager().collectVoiceRefs();
    }

    /** 实例侧收集本机语音会话引用 */
    List<VoiceRef> collectVoiceRefs()
    {
        List<VoiceRef> refs = new ArrayList<>();
        for (VoiceSession s : sessions.values())
        {
            refs.add(new VoiceRef(s.getSessionId(), s.getRole()));
        }
        return refs;
    }

    /**
     * 语音会话引用（心跳快照用）。
     */
    public static final class VoiceRef
    {
        /** 图文/语音会话ID */
        public final String sessionId;
        /** agent/caller */
        public final String role;

        VoiceRef(String sessionId, String role)
        {
            this.sessionId = sessionId;
            this.role = role;
        }
    }

    /**
     * localVoiceRefs 是静态方法（供 WsClusterRelay 静态调用），通过当前装配实例取本机表；
     * 未开启集群时该方法不会被调用。
     */
    private static VoiceSessionManager getCurrentManager()
    {
        return ai.lawyers.common.utils.spring.SpringUtils.getBean(VoiceSessionManager.class);
    }

    public VoiceSession get(String connId)
    {
        return sessions.get(connId);
    }

    /** 当前本机 /ws/voice 连接数 */
    public int count()
    {
        return sessions.size();
    }

    @PreDestroy
    public void destroy()
    {
        for (VoiceSession s : sessions.values())
        {
            try
            {
                s.shutdown();
            }
            catch (Exception e)
            {
                log.warn("VoiceWS 容器停机关闭会话异常: {}", e.getMessage());
            }
        }
        sessions.clear();
    }

    /* 测试用注入点 */

    void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    void setMaxSessions(int maxSessions)
    {
        this.maxSessions = maxSessions;
    }

    void setSendQueueCapacity(int sendQueueCapacity)
    {
        this.sendQueueCapacity = sendQueueCapacity;
    }
}
