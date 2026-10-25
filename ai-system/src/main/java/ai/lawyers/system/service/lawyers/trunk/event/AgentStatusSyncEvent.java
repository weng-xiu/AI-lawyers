package ai.lawyers.system.service.lawyers.trunk.event;

/**
 * P10/D1：队列成员设备态 → 坐席状态同步事件（进程内 Spring 事件）。
 *
 * <p>由 {@link CallEventProcessor} 发布，{@link CallEventListeners} 消费后调用
 * {@code IAiCallAgentStatusService}，避免处理器与坐席状态服务互相依赖。</p>
 *
 * @author ai-lawyers
 */
public class AgentStatusSyncEvent
{
    private final String extension;

    private final String targetStatus;

    public AgentStatusSyncEvent(String extension, String targetStatus)
    {
        this.extension = extension;
        this.targetStatus = targetStatus;
    }

    public String getExtension()
    {
        return extension;
    }

    public String getTargetStatus()
    {
        return targetStatus;
    }
}
