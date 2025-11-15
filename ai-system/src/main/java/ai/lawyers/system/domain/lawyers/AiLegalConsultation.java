package ai.lawyers.system.domain.lawyers;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 法律咨询记录表 ai_legal_consultation
 * 
 * @author AI Lawyers
 */
public class AiLegalConsultation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 咨询ID */
    @Excel(name = "咨询ID", cellType = ColumnType.NUMERIC)
    private Long consultationId;

    /** 用户ID */
    @Excel(name = "用户ID", cellType = ColumnType.NUMERIC)
    private Long userId;

    /** 咨询问题 */
    @Excel(name = "咨询问题")
    private String question;

    /** 问题分类ID */
    @Excel(name = "问题分类ID", cellType = ColumnType.NUMERIC)
    private Long categoryId;

    /** AI回答 */
    @Excel(name = "AI回答")
    private String answer;

    /** 回答置信度 */
    @Excel(name = "回答置信度")
    private Double confidence;

    /** 状态（0处理中 1已完成 2失败） */
    @Excel(name = "状态", readConverterExp = "0=处理中,1=已完成,2=失败")
    private String status;

    /** 附件路径 */
    @Excel(name = "附件路径")
    private String attachmentPath;

    /** 用户评分(1-5) */
    @Excel(name = "用户评分", cellType = ColumnType.NUMERIC)
    private Integer rating;

    /** 用户反馈 */
    @Excel(name = "用户反馈")
    private String feedback;

    public Long getConsultationId()
    {
        return consultationId;
    }

    public void setConsultationId(Long consultationId)
    {
        this.consultationId = consultationId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    @NotBlank(message = "咨询问题不能为空")
    @Size(min = 0, max = 2000, message = "咨询问题不能超过2000个字符")
    public String getQuestion()
    {
        return question;
    }

    public void setQuestion(String question)
    {
        this.question = question;
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public String getAnswer()
    {
        return answer;
    }

    public void setAnswer(String answer)
    {
        this.answer = answer;
    }

    public Double getConfidence()
    {
        return confidence;
    }

    public void setConfidence(Double confidence)
    {
        this.confidence = confidence;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getAttachmentPath()
    {
        return attachmentPath;
    }

    public void setAttachmentPath(String attachmentPath)
    {
        this.attachmentPath = attachmentPath;
    }

    public Integer getRating()
    {
        return rating;
    }

    public void setRating(Integer rating)
    {
        this.rating = rating;
    }

    public String getFeedback()
    {
        return feedback;
    }

    public void setFeedback(String feedback)
    {
        this.feedback = feedback;
    }
    
    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("consultationId", getConsultationId())
            .append("userId", getUserId())
            .append("question", getQuestion())
            .append("categoryId", getCategoryId())
            .append("answer", getAnswer())
            .append("confidence", getConfidence())
            .append("status", getStatus())
            .append("attachmentPath", getAttachmentPath())
            .append("rating", getRating())
            .append("feedback", getFeedback())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}