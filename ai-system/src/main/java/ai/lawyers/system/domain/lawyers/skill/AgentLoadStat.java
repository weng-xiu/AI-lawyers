package ai.lawyers.system.domain.lawyers.skill;

import java.util.Date;

/**
 * 坐席负载统计（ACD least_recent / least_calls 策略批量聚合用，消除 N+1 查询）
 *
 * @author ai-lawyers
 */
public class AgentLoadStat
{
    /** 坐席ID */
    private Long agentId;

    /** 最近一次通话开始时间（null 表示今日/历史无通话，排最前） */
    private Date lastCallStartTime;

    /** 当日已完成通话数 */
    private Integer todayCompleted;

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public Date getLastCallStartTime() { return lastCallStartTime; }
    public void setLastCallStartTime(Date lastCallStartTime) { this.lastCallStartTime = lastCallStartTime; }

    public Integer getTodayCompleted() { return todayCompleted; }
    public void setTodayCompleted(Integer todayCompleted) { this.todayCompleted = todayCompleted; }
}
