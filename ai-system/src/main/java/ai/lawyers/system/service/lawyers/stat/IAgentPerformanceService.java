package ai.lawyers.system.service.lawyers.stat;

import java.util.Date;
import java.util.List;
import ai.lawyers.system.domain.lawyers.stat.AgentPerformanceVO;

/**
 * B4 坐席效能报表聚合服务
 */
public interface IAgentPerformanceService
{
    /**
     * 按坐席聚合的效能指标列表。
     *
     * @param agentName 坐席名称模糊筛选
     * @param beginTime 统计开始时间
     * @param endTime   统计结束时间
     */
    List<AgentPerformanceVO> selectAgentPerformanceList(String agentName, Date beginTime, Date endTime);
}
