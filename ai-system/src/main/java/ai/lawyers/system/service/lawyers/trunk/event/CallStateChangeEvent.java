package ai.lawyers.system.service.lawyers.trunk.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * P10/D1：呼叫状态机变更事件（进程内 Spring 事件）。
 *
 * <p>RINGING / ANSWERED / HANGUP 由 {@link CallEventProcessor} 发布，
 * {@link CallEventListeners} 转交 {@code ICallDispatchService#onCallEvent} 驱动状态机，
 * 从而把「调度服务 → 事件总线 → 处理器 → 调度服务」的环改为单向事件流。</p>
 *
 * @author ai-lawyers
 */
public class CallStateChangeEvent
{
    private final String sessionId;

    private final String state;

    private final Map<String, Object> params;

    public CallStateChangeEvent(String sessionId, String state, Map<String, Object> params)
    {
        this.sessionId = sessionId;
        this.state = state;
        this.params = params == null ? new HashMap<>() : Collections.unmodifiableMap(new HashMap<>(params));
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public String getState()
    {
        return state;
    }

    public Map<String, Object> getParams()
    {
        return params;
    }
}
