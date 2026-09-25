package ai.lawyers.framework.websocket.voice;

import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * /ws/voice 本机连接注册表（P3-A1）。
 *
 * <p>key=WebSocket connId（{@code Session.getId()}）；注册即创建
 * {@link VoiceSession}（含保序发送线程），注销幂等释放全部引擎句柄。
 * 跨实例消息路由（语音会话锚定）随多实例部署批次扩展，A1 仅维护本机表。</p>
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
            session.shutdown();
            log.info("VoiceWS disconnect connId={}, remaining={}", connId, sessions.size());
        }
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
