package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 用户评价对象 ai_user_evaluation
 * 
 * @author AI律师
 * @date 2023-11-19
 */
public class AiUserEvaluation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 评价ID */
    private Long evaluationId;

    /** 咨询ID */
    @Excel(name = "咨询ID")
    private Long consultationId;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 总体评价（1-5分） */
    @Excel(name = "总体评价")
    private Integer overallRating;

    /** 专业度评价（1-5分） */
    @Excel(name = "专业度评价")
    private Integer professionalismRating;

    /** 响应速度评价（1-5分） */
    @Excel(name = "响应速度评价")
    private Integer responsivenessRating;

    /** 解答质量评价（1-5分） */
    @Excel(name = "解答质量评价")
    private Integer qualityRating;

    /** 文字反馈 */
    @Excel(name = "文字反馈")
    private String feedback;

    public void setEvaluationId(Long evaluationId) 
    {
        this.evaluationId = evaluationId;
    }

    public Long getEvaluationId() 
    {
        return evaluationId;
    }
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
    public void setOverallRating(Integer overallRating) 
    {
        this.overallRating = overallRating;
    }

    public Integer getOverallRating() 
    {
        return overallRating;
    }
    public void setProfessionalismRating(Integer professionalismRating) 
    {
        this.professionalismRating = professionalismRating;
    }

    public Integer getProfessionalismRating() 
    {
        return professionalismRating;
    }
    public void setResponsivenessRating(Integer responsivenessRating) 
    {
        this.responsivenessRating = responsivenessRating;
    }

    public Integer getResponsivenessRating() 
    {
        return responsivenessRating;
    }
    public void setQualityRating(Integer qualityRating) 
    {
        this.qualityRating = qualityRating;
    }

    public Integer getQualityRating() 
    {
        return qualityRating;
    }
    public void setFeedback(String feedback) 
    {
        this.feedback = feedback;
    }

    public String getFeedback() 
    {
        return feedback;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("evaluationId", getEvaluationId())
            .append("consultationId", getConsultationId())
            .append("userId", getUserId())
            .append("overallRating", getOverallRating())
            .append("professionalismRating", getProfessionalismRating())
            .append("responsivenessRating", getResponsivenessRating())
            .append("qualityRating", getQualityRating())
            .append("feedback", getFeedback())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}