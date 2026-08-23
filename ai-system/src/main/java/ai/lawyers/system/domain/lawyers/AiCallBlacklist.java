package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 通话黑白名单对象 ai_call_blacklist
 */
public class AiCallBlacklist extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 电话号码 */
    @Excel(name = "电话号码")
    private String phoneNumber;

    /** 名单类型 1黑名单 2白名单 */
    @Excel(name = "名单类型", readConverterExp = "1=黑名单,2=白名单")
    private Integer listType;

    /** 加入原因 */
    @Excel(name = "加入原因")
    private String reason;

    /** 状态 0停用 1启用 */
    @Excel(name = "状态", readConverterExp = "0=停用,1=启用")
    private Integer status;

    /** 生效开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "生效开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveStart;

    /** 生效结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "生效结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveEnd;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }

    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getPhoneNumber() { return phoneNumber; }

    public void setListType(Integer listType) { this.listType = listType; }
    public Integer getListType() { return listType; }

    public void setReason(String reason) { this.reason = reason; }
    public String getReason() { return reason; }

    public void setStatus(Integer status) { this.status = status; }
    public Integer getStatus() { return status; }

    public void setEffectiveStart(Date effectiveStart) { this.effectiveStart = effectiveStart; }
    public Date getEffectiveStart() { return effectiveStart; }

    public void setEffectiveEnd(Date effectiveEnd) { this.effectiveEnd = effectiveEnd; }
    public Date getEffectiveEnd() { return effectiveEnd; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("phoneNumber", getPhoneNumber())
            .append("listType", getListType())
            .append("reason", getReason())
            .append("status", getStatus())
            .append("effectiveStart", getEffectiveStart())
            .append("effectiveEnd", getEffectiveEnd())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
