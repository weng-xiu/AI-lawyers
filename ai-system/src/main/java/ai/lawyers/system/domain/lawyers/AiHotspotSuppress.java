package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 高频置底规则对象 ai_hotspot_suppress
 *
 * <p>对骚扰/高频来电与外呼号码做降权置底或直接拦截：
 * 号码规则按主叫精确匹配，关键词规则按文本匹配；入站命中 PRIORITY 时排队沉底（仍可接听），
 * 命中 REJECT 时直接挂断。</p>
 *
 * @author ai-lawyers
 */
public class AiHotspotSuppress extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private Long suppressId;

    /** 规则名称 */
    @Excel(name = "规则名称")
    private String ruleName;

    /** 匹配类型 PHONE号码 KEYWORD关键词 */
    @Excel(name = "匹配类型", readConverterExp = "PHONE=号码匹配,KEYWORD=关键词匹配")
    private String matchType;

    /** 匹配值（号码精确匹配/关键词） */
    @Excel(name = "匹配值")
    private String matchValue;

    /** 处置动作 PRIORITY置底降权 REJECT直接拦截 */
    @Excel(name = "处置动作", readConverterExp = "PRIORITY=置底降权,REJECT=直接拦截")
    private String action;

    /** 置底优先级（负数，入站队列数值越大越优先） */
    @Excel(name = "置底优先级")
    private Integer priorityLevel;

    /** 时间窗内触发次数阈值（0=命中即生效） */
    private Integer triggerCount;

    /** 频次统计时间窗（秒，0=不限制） */
    private Integer windowSeconds;

    /** 累计命中次数 */
    @Excel(name = "命中次数")
    private Integer hitCount;

    /** 状态 0启用 1停用 */
    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    /** 生效开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "生效开始", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveStart;

    /** 生效结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "生效结束", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveEnd;

    public Long getSuppressId() { return suppressId; }
    public void setSuppressId(Long suppressId) { this.suppressId = suppressId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }

    public String getMatchValue() { return matchValue; }
    public void setMatchValue(String matchValue) { this.matchValue = matchValue; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(Integer priorityLevel) { this.priorityLevel = priorityLevel; }

    public Integer getTriggerCount() { return triggerCount; }
    public void setTriggerCount(Integer triggerCount) { this.triggerCount = triggerCount; }

    public Integer getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(Integer windowSeconds) { this.windowSeconds = windowSeconds; }

    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getEffectiveStart() { return effectiveStart; }
    public void setEffectiveStart(Date effectiveStart) { this.effectiveStart = effectiveStart; }

    public Date getEffectiveEnd() { return effectiveEnd; }
    public void setEffectiveEnd(Date effectiveEnd) { this.effectiveEnd = effectiveEnd; }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("suppressId", getSuppressId())
            .append("ruleName", getRuleName())
            .append("matchType", getMatchType())
            .append("matchValue", getMatchValue())
            .append("action", getAction())
            .append("priorityLevel", getPriorityLevel())
            .append("triggerCount", getTriggerCount())
            .append("windowSeconds", getWindowSeconds())
            .append("hitCount", getHitCount())
            .append("status", getStatus())
            .append("effectiveStart", getEffectiveStart())
            .append("effectiveEnd", getEffectiveEnd())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
