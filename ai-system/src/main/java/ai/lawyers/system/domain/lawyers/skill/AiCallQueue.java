package ai.lawyers.system.domain.lawyers.skill;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 排队/分配流水对象 ai_call_queue
 *
 * @author ai-lawyers
 */
public class AiCallQueue extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 排队ID */
    private Long queueId;

    /** IVR会话ID */
    private String sessionId;

    /** 通话记录ID */
    private Long recordId;

    /** 主叫号码 */
    @Excel(name = "主叫号码")
    private String callerNumber;

    /** 目标技能组ID */
    private Long groupId;

    /** 入队时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date enqueueTime;

    /** 出队时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dequeueTime;

    /** 分配到的坐席ID */
    private Long agentId;

    /** 等待时长（秒） */
    @Excel(name = "等待时长(秒)")
    private Integer waitDuration;

    /** 排队状态（0排队中 1已分配 2超时溢出 3已放弃 4无可用坐席） */
    @Excel(name = "状态", readConverterExp = "0=排队中,1=已分配,2=超时溢出,3=已放弃,4=无可用")
    private String queueStatus;

    /** 实际命中策略 */
    private String strategyUsed;

    /** 排队优先级 */
    private Integer priority;

    /** 技能组名称（关联查询） */
    @Excel(name = "技能组")
    private String groupName;

    /** 坐席名称（关联查询） */
    @Excel(name = "分配坐席")
    private String agentName;

    public Long getQueueId() { return queueId; }
    public void setQueueId(Long queueId) { this.queueId = queueId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public String getCallerNumber() { return callerNumber; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Date getEnqueueTime() { return enqueueTime; }
    public void setEnqueueTime(Date enqueueTime) { this.enqueueTime = enqueueTime; }

    public Date getDequeueTime() { return dequeueTime; }
    public void setDequeueTime(Date dequeueTime) { this.dequeueTime = dequeueTime; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Integer getWaitDuration() { return waitDuration; }
    public void setWaitDuration(Integer waitDuration) { this.waitDuration = waitDuration; }

    public String getQueueStatus() { return queueStatus; }
    public void setQueueStatus(String queueStatus) { this.queueStatus = queueStatus; }

    public String getStrategyUsed() { return strategyUsed; }
    public void setStrategyUsed(String strategyUsed) { this.strategyUsed = strategyUsed; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("queueId", getQueueId())
            .append("sessionId", getSessionId())
            .append("callerNumber", getCallerNumber())
            .append("groupId", getGroupId())
            .append("queueStatus", getQueueStatus())
            .append("agentId", getAgentId())
            .append("waitDuration", getWaitDuration())
            .toString();
    }
}
