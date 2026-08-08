package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * AI律师辅助会话（独立链路）
 *
 * 与人工接听链路（ai_call_record / AiCallAgentStatus）物理隔离：
 *  - 仅通过 recordId 关联人工通话记录，不修改、不调用人工状态机；
 *  - 拥有独立的会话状态机（见 AiAssistSessionStatusEnum）；
 *  - AI 作为人工坐席的实时辅助，不接管通话控制。
 */
public class AiAiCallSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** AI辅助会话ID */
    private Long sessionId;

    /** 关联人工通话记录ID（ai_call_record.record_id） */
    private Long recordId;

    /** 人工坐席ID */
    private Long agentId;

    /** 主叫号码 */
    private String callerPhone;

    /** 客户姓名 */
    private String callerName;

    /** AI辅助状态 0=初始化 1=分析中 2=推荐中 3=已小结 4=已结束 9=异常 */
    private String sessionStatus;

    /** 识别的咨询意图分类 */
    private String intentCategory;

    /** AI推荐的法条(JSON) */
    private String recommendLaws;

    /** AI推荐的话术建议(JSON) */
    private String recommendScripts;

    /** AI自动生成的通话小结 */
    private String callSummary;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setAgentId(Long agentId)
    {
        this.agentId = agentId;
    }

    public Long getAgentId()
    {
        return agentId;
    }

    public void setCallerPhone(String callerPhone)
    {
        this.callerPhone = callerPhone;
    }

    public String getCallerPhone()
    {
        return callerPhone;
    }

    public void setCallerName(String callerName)
    {
        this.callerName = callerName;
    }

    public String getCallerName()
    {
        return callerName;
    }

    public void setSessionStatus(String sessionStatus)
    {
        this.sessionStatus = sessionStatus;
    }

    public String getSessionStatus()
    {
        return sessionStatus;
    }

    public void setIntentCategory(String intentCategory)
    {
        this.intentCategory = intentCategory;
    }

    public String getIntentCategory()
    {
        return intentCategory;
    }

    public void setRecommendLaws(String recommendLaws)
    {
        this.recommendLaws = recommendLaws;
    }

    public String getRecommendLaws()
    {
        return recommendLaws;
    }

    public void setRecommendScripts(String recommendScripts)
    {
        this.recommendScripts = recommendScripts;
    }

    public String getRecommendScripts()
    {
        return recommendScripts;
    }

    public void setCallSummary(String callSummary)
    {
        this.callSummary = callSummary;
    }

    public String getCallSummary()
    {
        return callSummary;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("sessionId", getSessionId())
            .append("recordId", getRecordId())
            .append("agentId", getAgentId())
            .append("callerPhone", getCallerPhone())
            .append("callerName", getCallerName())
            .append("sessionStatus", getSessionStatus())
            .append("intentCategory", getIntentCategory())
            .append("recommendLaws", getRecommendLaws())
            .append("recommendScripts", getRecommendScripts())
            .append("callSummary", getCallSummary())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
