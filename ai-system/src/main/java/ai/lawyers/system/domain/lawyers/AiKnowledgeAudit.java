package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 知识库审核发布流水对象 ai_knowledge_audit（F7）
 *
 * <p>记录知识条目"草稿→待审核→已发布→已下线"全过程动作留痕：
 * SUBMIT 提交 / APPROVE 通过 / REJECT 驳回 / PUBLISH 发布 / OFFLINE 下线。</p>
 *
 * @author ai-lawyers
 */
public class AiKnowledgeAudit extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long auditId;

    private Long knowledgeId;

    /** 知识标题（联表冗余展示） */
    @Excel(name = "知识标题")
    private String title;

    private Integer publishVersion;

    /** 动作 SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE */
    @Excel(name = "动作")
    private String action;

    private String fromStatus;

    private String toStatus;

    @Excel(name = "审核意见")
    private String auditOpinion;

    @Excel(name = "审核人")
    private String auditor;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "审核时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date auditTime;

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }

    public Long getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(Long knowledgeId) { this.knowledgeId = knowledgeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getPublishVersion() { return publishVersion; }
    public void setPublishVersion(Integer publishVersion) { this.publishVersion = publishVersion; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getAuditOpinion() { return auditOpinion; }
    public void setAuditOpinion(String auditOpinion) { this.auditOpinion = auditOpinion; }

    public String getAuditor() { return auditor; }
    public void setAuditor(String auditor) { this.auditor = auditor; }

    public Date getAuditTime() { return auditTime; }
    public void setAuditTime(Date auditTime) { this.auditTime = auditTime; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("auditId", getAuditId())
                .append("knowledgeId", getKnowledgeId())
                .append("title", getTitle())
                .append("action", getAction())
                .append("fromStatus", getFromStatus())
                .append("toStatus", getToStatus())
                .append("auditor", getAuditor())
                .append("auditTime", getAuditTime())
                .toString();
    }
}
