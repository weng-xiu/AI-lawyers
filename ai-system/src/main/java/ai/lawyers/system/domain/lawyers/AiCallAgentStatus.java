package ai.lawyers.system.domain.lawyers;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import ai.lawyers.common.annotation.Excel;
import ai.lawyers.common.annotation.Excel.ColumnType;
import ai.lawyers.common.core.domain.BaseEntity;

public class AiCallAgentStatus extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long agentId;

    @Excel(name = "用户ID", cellType = ColumnType.NUMERIC)
    private Long userId;

    @Excel(name = "坐席名称")
    private String agentName;

    @Excel(name = "状态", readConverterExp = "0=离线,1=在线,2=忙碌,3=休息")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "登录时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date loginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "注销时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date logoutTime;

    @Excel(name = "最后登录IP")
    private String lastLoginIp;

    @Excel(name = "应答模式", readConverterExp = "0=自动应答,1=手动应答")
    private String callMode;

    @Excel(name = "当前通话ID", cellType = ColumnType.NUMERIC)
    private Long currentCallId;

    @Excel(name = "当前通话号码")
    private String currentCallPhone;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "当前通话开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date callStartTime;

    @Excel(name = "通话状态", readConverterExp = "0=空闲,1=通话中,2=保持,3=咨询中,4=三方,5=话后整理")
    private String callStatus;

    @Excel(name = "SIP分机号")
    private String sipExtension;

    public void setAgentId(Long agentId)
    {
        this.agentId = agentId;
    }

    public Long getAgentId() 
    {
        return agentId;
    }
    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }
    public void setAgentName(String agentName) 
    {
        this.agentName = agentName;
    }

    public String getAgentName() 
    {
        return agentName;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }
    public void setLoginTime(Date loginTime) 
    {
        this.loginTime = loginTime;
    }

    public Date getLoginTime() 
    {
        return loginTime;
    }
    public void setLogoutTime(Date logoutTime) 
    {
        this.logoutTime = logoutTime;
    }

    public Date getLogoutTime() 
    {
        return logoutTime;
    }
    public void setLastLoginIp(String lastLoginIp) 
    {
        this.lastLoginIp = lastLoginIp;
    }

    public String getLastLoginIp()
    {
        return lastLoginIp;
    }
    public void setCallMode(String callMode)
    {
        this.callMode = callMode;
    }

    public String getCallMode()
    {
        return callMode;
    }
    public void setCurrentCallId(Long currentCallId)
    {
        this.currentCallId = currentCallId;
    }

    public Long getCurrentCallId()
    {
        return currentCallId;
    }
    public void setCurrentCallPhone(String currentCallPhone)
    {
        this.currentCallPhone = currentCallPhone;
    }

    public String getCurrentCallPhone()
    {
        return currentCallPhone;
    }
    public void setCallStartTime(Date callStartTime)
    {
        this.callStartTime = callStartTime;
    }

    public Date getCallStartTime()
    {
        return callStartTime;
    }
    public void setCallStatus(String callStatus)
    {
        this.callStatus = callStatus;
    }

    public String getCallStatus()
    {
        return callStatus;
    }
    public void setSipExtension(String sipExtension)
    {
        this.sipExtension = sipExtension;
    }

    public String getSipExtension()
    {
        return sipExtension;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("agentId", getAgentId())
            .append("userId", getUserId())
            .append("agentName", getAgentName())
            .append("status", getStatus())
            .append("loginTime", getLoginTime())
            .append("logoutTime", getLogoutTime())
            .append("lastLoginIp", getLastLoginIp())
            .append("callMode", getCallMode())
            .append("currentCallId", getCurrentCallId())
            .append("currentCallPhone", getCurrentCallPhone())
            .append("callStartTime", getCallStartTime())
            .append("callStatus", getCallStatus())
            .append("sipExtension", getSipExtension())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
