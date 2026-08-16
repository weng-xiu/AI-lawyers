package ai.lawyers.system.service.impl.lawyers.stat;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.stat.DashboardMapper;
import ai.lawyers.system.service.lawyers.stat.IDashboardService;

/**
 * B4 运营大屏聚合服务实现
 *
 * 基于现有业务表实时聚合，聚合结果按模块键组织，前端一次拉取后渲染。
 */
@Service
public class DashboardServiceImpl implements IDashboardService
{
    @Autowired
    private DashboardMapper dashboardMapper;

    @Override
    public Map<String, Object> getDashboardData(Date beginTime, Date endTime)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callSummary", nullToEmpty(dashboardMapper.selectCallSummary(beginTime, endTime)));
        data.put("callTrend", nullToEmptyList(dashboardMapper.selectCallTrend(beginTime, endTime)));
        data.put("categoryPie", nullToEmptyList(dashboardMapper.selectCategoryPie(beginTime, endTime)));
        data.put("aiRatio", nullToEmpty(dashboardMapper.selectAiRatio(beginTime, endTime)));
        data.put("agentLoad", nullToEmptyList(dashboardMapper.selectAgentLoad()));
        data.put("queueNow", nullToEmptyList(dashboardMapper.selectQueueNow()));
        data.put("outboundProgress", nullToEmptyList(dashboardMapper.selectOutboundProgress()));
        data.put("agentStatusSummary", nullToEmpty(dashboardMapper.selectAgentStatusSummary()));
        data.put("satisfactionSummary", nullToEmpty(dashboardMapper.selectSatisfactionSummary(beginTime, endTime)));
        return data;
    }

    private Map<String, Object> nullToEmpty(Map<String, Object> map)
    {
        return map == null ? new LinkedHashMap<>() : map;
    }

    private List<Map<String, Object>> nullToEmptyList(List<Map<String, Object>> list)
    {
        return list == null ? java.util.Collections.emptyList() : list;
    }
}
