package ai.lawyers.system.service.lawyers.trunk.event;

/**
 * P10/D1：入站来话事件（进程内 Spring 事件）。
 *
 * <p>由 {@link CallEventProcessor} 发布，{@link CallEventListeners} 消费后转交
 * {@code InboundCallHandler} 执行弹屏/排队/IVR，避免处理器反向依赖入站处理器。</p>
 *
 * @author ai-lawyers
 */
public class InboundCallEvent
{
    private final String host;

    private final String sessionId;

    private final String caller;

    private final String callee;

    public InboundCallEvent(String host, String sessionId, String caller, String callee)
    {
        this.host = host;
        this.sessionId = sessionId;
        this.caller = caller;
        this.callee = callee;
    }

    public String getHost()
    {
        return host;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public String getCaller()
    {
        return caller;
    }

    public String getCallee()
    {
        return callee;
    }
}
