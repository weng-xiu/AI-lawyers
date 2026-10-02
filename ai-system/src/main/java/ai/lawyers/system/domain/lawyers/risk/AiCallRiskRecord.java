package ai.lawyers.system.domain.lawyers.risk;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 呼叫行为风控评估记录对象 ai_call_risk_record（P2-15）
 *
 * <p>定时任务按号码聚合窗口内呼叫行为（来电次数/超短通话/夜间来电/未接）
 * 加权评分，超阈值自动生成高频置底规则并留痕，供班组长复核。</p>
 *
 * <p>隐私口径：只存号码盲索引与脱敏号码，不落明文/密文。</p>
 *
 * @author ai-lawyers
 */
public class AiCallRiskRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long riskId;

    /** 主叫号码盲索引（HMAC-SM3，确定性，可关联可分组） */
    private String callerNumberIndex;

    /** 脱敏号码（138****5678，展示用） */
    private String maskedNumber;

    /** 评估窗口（小时） */
    private Integer windowHours;

    /** 窗口内来电次数 */
    private Integer callCount;

    /** 超短通话次数 */
    private Integer shortCount;

    /** 夜间来电次数 */
    private Integer nightCount;

    /** 未接来电次数 */
    private Integer missedCount;

    /** 风险评分 0~100 */
    private Integer riskScore;

    /** 评分构成说明（频次x+超短y+夜间z+未接w） */
    private String scoreDetail;

    /** 风险等级 LOW/MEDIUM/HIGH */
    private String riskLevel;

    /** 复核状态 0待复核 1已确认 2已忽略 */
    private String reviewStatus;

    /** 自动生成的置底规则ID */
    private Long suppressId;

    /** 复核人 */
    private String reviewBy;

    /** 复核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    /** 复核说明 */
    private String reviewRemark;

    /** 查询辅助：号码（模糊匹配脱敏号码，仅入参） */
    private String maskedNumberLike;

    public Long getRiskId()
    {
        return riskId;
    }

    public void setRiskId(Long riskId)
    {
        this.riskId = riskId;
    }

    public String getCallerNumberIndex()
    {
        return callerNumberIndex;
    }

    public void setCallerNumberIndex(String callerNumberIndex)
    {
        this.callerNumberIndex = callerNumberIndex;
    }

    public String getMaskedNumber()
    {
        return maskedNumber;
    }

    public void setMaskedNumber(String maskedNumber)
    {
        this.maskedNumber = maskedNumber;
    }

    public Integer getWindowHours()
    {
        return windowHours;
    }

    public void setWindowHours(Integer windowHours)
    {
        this.windowHours = windowHours;
    }

    public Integer getCallCount()
    {
        return callCount;
    }

    public void setCallCount(Integer callCount)
    {
        this.callCount = callCount;
    }

    public Integer getShortCount()
    {
        return shortCount;
    }

    public void setShortCount(Integer shortCount)
    {
        this.shortCount = shortCount;
    }

    public Integer getNightCount()
    {
        return nightCount;
    }

    public void setNightCount(Integer nightCount)
    {
        this.nightCount = nightCount;
    }

    public Integer getMissedCount()
    {
        return missedCount;
    }

    public void setMissedCount(Integer missedCount)
    {
        this.missedCount = missedCount;
    }

    public Integer getRiskScore()
    {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore)
    {
        this.riskScore = riskScore;
    }

    public String getScoreDetail()
    {
        return scoreDetail;
    }

    public void setScoreDetail(String scoreDetail)
    {
        this.scoreDetail = scoreDetail;
    }

    public String getRiskLevel()
    {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel)
    {
        this.riskLevel = riskLevel;
    }

    public String getReviewStatus()
    {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus)
    {
        this.reviewStatus = reviewStatus;
    }

    public Long getSuppressId()
    {
        return suppressId;
    }

    public void setSuppressId(Long suppressId)
    {
        this.suppressId = suppressId;
    }

    public String getReviewBy()
    {
        return reviewBy;
    }

    public void setReviewBy(String reviewBy)
    {
        this.reviewBy = reviewBy;
    }

    public Date getReviewTime()
    {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime)
    {
        this.reviewTime = reviewTime;
    }

    public String getReviewRemark()
    {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark)
    {
        this.reviewRemark = reviewRemark;
    }

    public String getMaskedNumberLike()
    {
        return maskedNumberLike;
    }

    public void setMaskedNumberLike(String maskedNumberLike)
    {
        this.maskedNumberLike = maskedNumberLike;
    }
}
