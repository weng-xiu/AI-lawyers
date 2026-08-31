package ai.lawyers.system.domain.lawyers.quality;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 智能质检记录对象 ai_quality_inspection（T4-1）
 *
 * <p>来源：通话录音挂断后经 ASR 转写（复用 ai_call_record.transcript），调大模型按
 * 服务规范/答复准确/情绪态度/违禁话术维度评分；命中违禁/激烈情绪联动生成风险预警；
 * 质检员人工复核闭环（通过/驳回整改）。</p>
 *
 * @author ai-lawyers
 */
public class AiQualityInspection extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 质检ID */
    private Long inspectionId;

    /** 来源 1通话录音 2图文 3视频 */
    @Excel(name = "来源", readConverterExp = "1=通话录音,2=图文,3=视频")
    private String sourceType;

    /** 通话记录ID */
    private Long recordId;

    /** 会话ID */
    private String sessionId;

    /** 被检坐席ID */
    @Excel(name = "坐席ID")
    private Long agentId;

    /** 主叫号码 */
    @Excel(name = "主叫号码")
    private String callerNumber;

    /** 转写文本 */
    private String transcript;

    /** AI质检总分 */
    @Excel(name = "AI总分")
    private BigDecimal totalScore;

    /** 各维度评分JSON */
    private String dimensionJson;

    /** 命中违禁/敏感词明细JSON */
    private String violationJson;

    /** AI质检状态 0待检 1检中 2完成 3失败 */
    @Excel(name = "AI状态", readConverterExp = "0=待检,1=检中,2=完成,3=失败")
    private String aiStatus;

    /** AI质检总体评语/失败原因 */
    private String aiRemark;

    /** 人工复核状态 0未复核 1通过 2驳回整改 */
    @Excel(name = "复核状态", readConverterExp = "0=未复核,1=通过,2=驳回整改")
    private String reviewStatus;

    /** 复核人用户ID */
    private Long reviewerId;

    /** 复核人姓名 */
    @Excel(name = "复核人")
    private String reviewerName;

    /** 复核评语 */
    private String reviewRemark;

    /** 复核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "复核时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    /** 联动生成的风险预警ID */
    private Long riskWarningId;

    /** 联表回显：坐席名称 */
    @Excel(name = "坐席名称")
    private String agentName;

    public Long getInspectionId() { return inspectionId; }
    public void setInspectionId(Long inspectionId) { this.inspectionId = inspectionId; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getCallerNumber() { return callerNumber; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }

    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }

    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }

    public String getDimensionJson() { return dimensionJson; }
    public void setDimensionJson(String dimensionJson) { this.dimensionJson = dimensionJson; }

    public String getViolationJson() { return violationJson; }
    public void setViolationJson(String violationJson) { this.violationJson = violationJson; }

    public String getAiStatus() { return aiStatus; }
    public void setAiStatus(String aiStatus) { this.aiStatus = aiStatus; }

    public String getAiRemark() { return aiRemark; }
    public void setAiRemark(String aiRemark) { this.aiRemark = aiRemark; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }

    public Date getReviewTime() { return reviewTime; }
    public void setReviewTime(Date reviewTime) { this.reviewTime = reviewTime; }

    public Long getRiskWarningId() { return riskWarningId; }
    public void setRiskWarningId(Long riskWarningId) { this.riskWarningId = riskWarningId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
}
