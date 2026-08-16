package ai.lawyers.system.domain.lawyers.skill;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 技能组成员对象 ai_skill_group_member
 *
 * @author ai-lawyers
 */
public class AiSkillGroupMember extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 技能组ID */
    private Long groupId;

    /** 坐席ID（ai_call_agent_status.agent_id） */
    @Excel(name = "坐席ID")
    private Long agentId;

    /** 技能等级1-5 */
    @Excel(name = "技能等级")
    private Integer skillLevel;

    /** 组内优先级 */
    private Integer priority;

    /** 个人最大并发 */
    private Integer maxConcurrent;

    /** 状态（0禁用 1启用） */
    @Excel(name = "状态", readConverterExp = "0=禁用,1=启用")
    private String status;

    /** 坐席名称（关联查询） */
    @Excel(name = "坐席名称")
    private String agentName;

    /** 坐席当前状态（关联查询） */
    private String agentStatus;

    /** 坐席通话状态（关联查询） */
    private String callStatus;

    /** 批量操作用：坐席ID数组（非持久化） */
    private transient Long[] agentIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Integer getSkillLevel() { return skillLevel; }
    public void setSkillLevel(Integer skillLevel) { this.skillLevel = skillLevel; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(Integer maxConcurrent) { this.maxConcurrent = maxConcurrent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getAgentStatus() { return agentStatus; }
    public void setAgentStatus(String agentStatus) { this.agentStatus = agentStatus; }

    public String getCallStatus() { return callStatus; }
    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }

    public Long[] getAgentIds() { return agentIds; }
    public void setAgentIds(Long[] agentIds) { this.agentIds = agentIds; }

    @Override
    public Date getCreateTime() { return createTime; }
    @Override
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    @Override
    public Date getUpdateTime() { return updateTime; }
    @Override
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("groupId", getGroupId())
            .append("agentId", getAgentId())
            .append("skillLevel", getSkillLevel())
            .append("status", getStatus())
            .toString();
    }
}
