package ai.lawyers.system.domain.lawyers.skill;

import java.io.Serializable;

/**
 * 坐席分配结果
 *
 * @author ai-lawyers
 */
public class DispatchResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否成功分配到坐席 */
    private boolean success;

    /** 分配到的坐席ID */
    private Long agentId;

    /** 坐席名称 */
    private String agentName;

    /** 坐席分机号（如有） */
    private String agentExtension;

    /** 命中的技能组ID */
    private Long groupId;

    /** 命中的技能组名称 */
    private String groupName;

    /** 实际使用策略 */
    private String strategy;

    /** 排队ID（未分配直接排队时有值） */
    private Long queueId;

    /** 排队位置（从1开始） */
    private Integer queuePosition;

    /** 当前队列等待人数 */
    private Integer queueSize;

    /** 结果说明 */
    private String message;

    public static DispatchResult assigned(Long agentId, String agentName, Long groupId, String groupName, String strategy)
    {
        DispatchResult r = new DispatchResult();
        r.success = true;
        r.agentId = agentId;
        r.agentName = agentName;
        r.groupId = groupId;
        r.groupName = groupName;
        r.strategy = strategy;
        r.message = "已分配坐席：" + agentName;
        return r;
    }

    public static DispatchResult queued(Long queueId, int position, int queueSize, Long groupId, String groupName)
    {
        DispatchResult r = new DispatchResult();
        r.success = false;
        r.queueId = queueId;
        r.queuePosition = position;
        r.queueSize = queueSize;
        r.groupId = groupId;
        r.groupName = groupName;
        r.message = "无空闲坐席，已进入排队，当前位置第" + position + "位";
        return r;
    }

    public static DispatchResult failed(String message)
    {
        DispatchResult r = new DispatchResult();
        r.success = false;
        r.message = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentExtension() { return agentExtension; }
    public void setAgentExtension(String agentExtension) { this.agentExtension = agentExtension; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public Long getQueueId() { return queueId; }
    public void setQueueId(Long queueId) { this.queueId = queueId; }
    public Integer getQueuePosition() { return queuePosition; }
    public void setQueuePosition(Integer queuePosition) { this.queuePosition = queuePosition; }
    public Integer getQueueSize() { return queueSize; }
    public void setQueueSize(Integer queueSize) { this.queueSize = queueSize; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
