package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 用户咨询对象 ai_user_consultation
 * 
 * @author AI律师
 * @date 2023-11-19
 */
public class AiUserConsultation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 咨询ID */
    private Long consultationId;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 问题分类 */
    @Excel(name = "问题分类")
    private String category;

    /** 问题内容 */
    @Excel(name = "问题内容")
    private String content;

    /** 附件路径 */
    private String attachments;

    /** AI回答 */
    @Excel(name = "AI回答")
    private String aiAnswer;

    /** 相关法条 */
    @Excel(name = "相关法条")
    private String relatedLaws;

    /** 相关案例 */
    @Excel(name = "相关案例")
    private String relatedCases;

    /** 置信度 */
    @Excel(name = "置信度")
    private Double confidence;

    /** 状态（PROCESSING-处理中，COMPLETED-已完成，FAILED-失败） */
    @Excel(name = "状态", readConverterExp = "PROCESSING=处理中,COMPLETED=已完成,FAILED=失败")
    private String status;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "处理时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date processTime;

    public void setConsultationId(Long consultationId) 
    {
        this.consultationId = consultationId;
    }

    public Long getConsultationId() 
    {
        return consultationId;
    }
    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }
    public void setCategory(String category) 
    {
        this.category = category;
    }

    public String getCategory() 
    {
        return category;
    }
    public void setContent(String content) 
    {
        this.content = content;
    }

    public String getContent() 
    {
        return content;
    }
    public void setAttachments(String attachments) 
    {
        this.attachments = attachments;
    }

    public String getAttachments() 
    {
        return attachments;
    }
    public void setAiAnswer(String aiAnswer) 
    {
        this.aiAnswer = aiAnswer;
    }

    public String getAiAnswer() 
    {
        return aiAnswer;
    }
    public void setRelatedLaws(String relatedLaws) 
    {
        this.relatedLaws = relatedLaws;
    }

    public String getRelatedLaws() 
    {
        return relatedLaws;
    }
    public void setRelatedCases(String relatedCases) 
    {
        this.relatedCases = relatedCases;
    }

    public String getRelatedCases() 
    {
        return relatedCases;
    }
    public void setConfidence(Double confidence) 
    {
        this.confidence = confidence;
    }

    public Double getConfidence() 
    {
        return confidence;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }
    public void setProcessTime(Date processTime) 
    {
        this.processTime = processTime;
    }

    public Date getProcessTime() 
    {
        return processTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("consultationId", getConsultationId())
            .append("userId", getUserId())
            .append("category", getCategory())
            .append("content", getContent())
            .append("attachments", getAttachments())
            .append("aiAnswer", getAiAnswer())
            .append("relatedLaws", getRelatedLaws())
            .append("relatedCases", getRelatedCases())
            .append("confidence", getConfidence())
            .append("status", getStatus())
            .append("processTime", getProcessTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}