package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 来电人档案对象 ai_caller_profile
 */
public class AiCallerProfile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long profileId;

    @Excel(name = "来电号码")
    private String callerNumber;

    @Excel(name = "来电人姓名")
    private String callerName;

    @Excel(name = "性别", readConverterExp = "0=男,1=女,2=未知")
    private String callerGender;

    @Excel(name = "年龄")
    private Integer callerAge;

    @Excel(name = "身份证号")
    private String callerIdCard;

    @Excel(name = "联系地址")
    private String callerAddress;

    @Excel(name = "客户等级")
    private String customerLevel;

    @Excel(name = "标签")
    private String tags;

    @Excel(name = "意图预测")
    private String intentPrediction;

    @Excel(name = "意图置信度")
    private Integer intentConfidence;

    @Excel(name = "咨询偏好")
    private String consultPreference;

    @Excel(name = "高频问题")
    private String highFreqProblem;

    @Excel(name = "高频提及次数")
    private Integer freqMentionCount;

    @Excel(name = "风险等级", readConverterExp = "0=低,1=中,2=高")
    private String riskLevel;

    @Excel(name = "情绪状态")
    private String emotionStatus;

    @Excel(name = "情绪预警")
    private String emotionWarning;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最后来电时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lastCallTime;

    /** 语种偏好 zh-CN 普通话 / yue-CN 粤语（F1） */
    @Excel(name = "语种偏好")
    private String languagePreference;

    /** 关怀模式偏好 0 标准 1 关怀（F2） */
    @Excel(name = "关怀模式", readConverterExp = "0=标准,1=关怀")
    private Integer careMode;

    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public Long getProfileId() { return profileId; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }
    public String getCallerNumber() { return callerNumber; }
    public void setCallerName(String callerName) { this.callerName = callerName; }
    public String getCallerName() { return callerName; }
    public void setCallerGender(String callerGender) { this.callerGender = callerGender; }
    public String getCallerGender() { return callerGender; }
    public void setCallerAge(Integer callerAge) { this.callerAge = callerAge; }
    public Integer getCallerAge() { return callerAge; }
    public void setCallerIdCard(String callerIdCard) { this.callerIdCard = callerIdCard; }
    public String getCallerIdCard() { return callerIdCard; }
    public void setCallerAddress(String callerAddress) { this.callerAddress = callerAddress; }
    public String getCallerAddress() { return callerAddress; }
    public void setCustomerLevel(String customerLevel) { this.customerLevel = customerLevel; }
    public String getCustomerLevel() { return customerLevel; }
    public void setTags(String tags) { this.tags = tags; }
    public String getTags() { return tags; }
    public void setIntentPrediction(String intentPrediction) { this.intentPrediction = intentPrediction; }
    public String getIntentPrediction() { return intentPrediction; }
    public void setIntentConfidence(Integer intentConfidence) { this.intentConfidence = intentConfidence; }
    public Integer getIntentConfidence() { return intentConfidence; }
    public void setConsultPreference(String consultPreference) { this.consultPreference = consultPreference; }
    public String getConsultPreference() { return consultPreference; }
    public void setHighFreqProblem(String highFreqProblem) { this.highFreqProblem = highFreqProblem; }
    public String getHighFreqProblem() { return highFreqProblem; }
    public void setFreqMentionCount(Integer freqMentionCount) { this.freqMentionCount = freqMentionCount; }
    public Integer getFreqMentionCount() { return freqMentionCount; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getRiskLevel() { return riskLevel; }
    public void setEmotionStatus(String emotionStatus) { this.emotionStatus = emotionStatus; }
    public String getEmotionStatus() { return emotionStatus; }
    public void setEmotionWarning(String emotionWarning) { this.emotionWarning = emotionWarning; }
    public String getEmotionWarning() { return emotionWarning; }
    public void setLastCallTime(Date lastCallTime) { this.lastCallTime = lastCallTime; }
    public Date getLastCallTime() { return lastCallTime; }
    public void setLanguagePreference(String languagePreference) { this.languagePreference = languagePreference; }
    public String getLanguagePreference() { return languagePreference; }
    public void setCareMode(Integer careMode) { this.careMode = careMode; }
    public Integer getCareMode() { return careMode; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("profileId", getProfileId())
            .append("callerNumber", getCallerNumber())
            .append("callerName", getCallerName())
            .append("customerLevel", getCustomerLevel())
            .append("intentPrediction", getIntentPrediction())
            .append("riskLevel", getRiskLevel())
            .append("emotionStatus", getEmotionStatus())
            .append("lastCallTime", getLastCallTime())
            .toString();
    }
}
