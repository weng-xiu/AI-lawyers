package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 高频置底命中处置日志对象 ai_hotspot_suppress_log
 *
 * @author ai-lawyers
 */
public class AiHotspotSuppressLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;

    /** 命中规则ID */
    private Long suppressId;

    /** 规则名称（冗余） */
    @Excel(name = "规则名称")
    private String ruleName;

    /** 呼叫方向 INBOUND入站 OUTBOUND外呼 */
    @Excel(name = "方向", readConverterExp = "INBOUND=入站,OUTBOUND=外呼")
    private String direction;

    /** 匹配类型 */
    private String matchType;

    /** 命中的匹配值 */
    private String matchValue;

    /** 主叫号码 */
    @Excel(name = "主叫号码")
    private String callerNumber;

    /** 被叫号码 */
    private String calleeNumber;

    /** 实际处置动作 */
    @Excel(name = "处置动作", readConverterExp = "PRIORITY=置底降权,REJECT=直接拦截")
    private String action;

    /** 处置后优先级 */
    private Integer priority;

    /** 通道UUID/话单关联键 */
    private String channelUuid;

    /** 命中时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "命中时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date hitTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public Long getSuppressId() { return suppressId; }
    public void setSuppressId(Long suppressId) { this.suppressId = suppressId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }

    public String getMatchValue() { return matchValue; }
    public void setMatchValue(String matchValue) { this.matchValue = matchValue; }

    public String getCallerNumber() { return callerNumber; }
    public void setCallerNumber(String callerNumber) { this.callerNumber = callerNumber; }

    public String getCalleeNumber() { return calleeNumber; }
    public void setCalleeNumber(String calleeNumber) { this.calleeNumber = calleeNumber; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public String getChannelUuid() { return channelUuid; }
    public void setChannelUuid(String channelUuid) { this.channelUuid = channelUuid; }

    public Date getHitTime() { return hitTime; }
    public void setHitTime(Date hitTime) { this.hitTime = hitTime; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("logId", getLogId())
            .append("suppressId", getSuppressId())
            .append("ruleName", getRuleName())
            .append("direction", getDirection())
            .append("callerNumber", getCallerNumber())
            .append("calleeNumber", getCalleeNumber())
            .append("action", getAction())
            .append("priority", getPriority())
            .append("channelUuid", getChannelUuid())
            .append("hitTime", getHitTime())
            .toString();
    }
}
