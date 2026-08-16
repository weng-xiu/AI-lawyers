package ai.lawyers.system.service.impl.lawyers.stat;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.stat.AgentPerformanceVO;
import ai.lawyers.system.mapper.lawyers.stat.AgentPerformanceMapper;
import ai.lawyers.system.service.lawyers.stat.IAgentPerformanceService;

/**
 * B4 坐席效能报表聚合服务实现
 */
@Service
public class AgentPerformanceServiceImpl implements IAgentPerformanceService
{
    @Autowired
    private AgentPerformanceMapper agentPerformanceMapper;

    @Override
    public List<AgentPerformanceVO> selectAgentPerformanceList(String agentName, Date beginTime, Date endTime)
    {
        return agentPerformanceMapper.selectAgentPerformance(agentName, beginTime, endTime);
    }
}
