package ai.lawyers.system.domain.lawyers.stat;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import ai.lawyers.common.annotation.Excel;

/**
 * 坐席效能报表行对象（聚合结果，非持久化表）
 */
public class AgentPerformanceVO
{
    private Long agentId;

    @Excel(name = "坐席名称")
    private String agentName;

    @Excel(name = "状态", readConverterExp = "0=离线,1=在线,2=忙碌,3=休息")
    private String status;

    @Excel(name = "通话状态", readConverterExp = "0=空闲,1=通话中,2=保持,3=咨询中,4=三方,5=话后整理")
    private String callStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "签入时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date loginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date logoutTime;

    @Excel(name = "签入时长(分)")
    private Long signedInMinutes;

    @Excel(name = "呼入接听数")
    private Long inboundCalls;

    @Excel(name = "呼出数")
    private Long outboundCalls;

    @Excel(name = "转接数")
    private Long transferredOut;

    @Excel(name = "通话时长(秒)")
    private Long talkDuration;

    @Excel(name = "平均通话时长(秒)")
    private Long avgTalkDuration;

    @Excel(name = "AI协访次数")
    private Long aiAssistCount;

    @Excel(name = "满意度均值")
    private Double avgSatisfaction;

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCallStatus() { return callStatus; }
    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }

    public Date getLoginTime() { return loginTime; }
    public void setLoginTime(Date loginTime) { this.loginTime = loginTime; }

    public Date getLogoutTime() { return logoutTime; }
    public void setLogoutTime(Date logoutTime) { this.logoutTime = logoutTime; }

    public Long getSignedInMinutes() { return signedInMinutes; }
    public void setSignedInMinutes(Long signedInMinutes) { this.signedInMinutes = signedInMinutes; }

    public Long getInboundCalls() { return inboundCalls; }
    public void setInboundCalls(Long inboundCalls) { this.inboundCalls = inboundCalls; }

    public Long getOutboundCalls() { return outboundCalls; }
    public void setOutboundCalls(Long outboundCalls) { this.outboundCalls = outboundCalls; }

    public Long getTransferredOut() { return transferredOut; }
    public void setTransferredOut(Long transferredOut) { this.transferredOut = transferredOut; }

    public Long getTalkDuration() { return talkDuration; }
    public void setTalkDuration(Long talkDuration) { this.talkDuration = talkDuration; }

    public Long getAvgTalkDuration() { return avgTalkDuration; }
    public void setAvgTalkDuration(Long avgTalkDuration) { this.avgTalkDuration = avgTalkDuration; }

    public Long getAiAssistCount() { return aiAssistCount; }
    public void setAiAssistCount(Long aiAssistCount) { this.aiAssistCount = aiAssistCount; }

    public Double getAvgSatisfaction() { return avgSatisfaction; }
    public void setAvgSatisfaction(Double avgSatisfaction) { this.avgSatisfaction = avgSatisfaction; }
}
