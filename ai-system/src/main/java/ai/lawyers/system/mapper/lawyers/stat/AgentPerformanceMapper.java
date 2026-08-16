package ai.lawyers.system.mapper.lawyers.stat;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.stat.AgentPerformanceVO;

/**
 * B4 坐席效能报表聚合 Mapper
 */
public interface AgentPerformanceMapper
{
    /**
     * 按坐席聚合的效能指标（签入时长、呼入/呼出、转接、通话时长、AI协访、满意度）。
     *
     * @param agentName 坐席名称模糊筛选
     * @param beginTime 统计开始时间
     * @param endTime   统计结束时间
     */
    List<AgentPerformanceVO> selectAgentPerformance(@Param("agentName") String agentName,
                                                    @Param("beginTime") Date beginTime,
                                                    @Param("endTime") Date endTime);
}
