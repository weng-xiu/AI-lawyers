package ai.lawyers.system.domain.lawyers.skill;

import java.util.List;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 技能组对象 ai_skill_group
 *
 * @author ai-lawyers
 */
public class AiSkillGroup extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 技能组ID */
    private Long groupId;

    /** 技能组名称 */
    @Excel(name = "技能组名称")
    private String groupName;

    /** 技能组编码 */
    @Excel(name = "技能组编码")
    private String groupCode;

    /** 关联咨询分类ID */
    private Long categoryId;

    /** 分配策略（round_robin/least_recent/least_calls/all_ring） */
    @Excel(name = "分配策略")
    private String strategy;

    /** 最大排队等待秒 */
    private Integer maxWait;

    /** 话后整理秒 */
    private Integer wrapUpTime;

    /** 服务水平阈值秒 */
    private Integer serviceLevelThreshold;

    /** 溢出技能组ID */
    private Long overflowGroupId;

    /** 状态（0停用 1启用） */
    @Excel(name = "状态", readConverterExp = "0=停用,1=启用")
    private String status;

    /** 分类名称（关联查询） */
    @Excel(name = "关联分类")
    private String categoryName;

    /** 成员数（关联查询） */
    private Integer memberCount;

    /** 成员列表（查询详情时返回） */
    private List<AiSkillGroupMember> members;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public Integer getMaxWait() { return maxWait; }
    public void setMaxWait(Integer maxWait) { this.maxWait = maxWait; }

    public Integer getWrapUpTime() { return wrapUpTime; }
    public void setWrapUpTime(Integer wrapUpTime) { this.wrapUpTime = wrapUpTime; }

    public Integer getServiceLevelThreshold() { return serviceLevelThreshold; }
    public void setServiceLevelThreshold(Integer serviceLevelThreshold) { this.serviceLevelThreshold = serviceLevelThreshold; }

    public Long getOverflowGroupId() { return overflowGroupId; }
    public void setOverflowGroupId(Long overflowGroupId) { this.overflowGroupId = overflowGroupId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }

    public List<AiSkillGroupMember> getMembers() { return members; }
    public void setMembers(List<AiSkillGroupMember> members) { this.members = members; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("groupId", getGroupId())
            .append("groupName", getGroupName())
            .append("groupCode", getGroupCode())
            .append("categoryId", getCategoryId())
            .append("strategy", getStrategy())
            .append("status", getStatus())
            .toString();
    }
}
