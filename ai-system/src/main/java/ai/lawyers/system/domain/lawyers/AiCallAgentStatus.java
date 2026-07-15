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
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
