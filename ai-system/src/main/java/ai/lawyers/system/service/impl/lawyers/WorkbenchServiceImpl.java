package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiNotice;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.mapper.lawyers.AiCallLedgerMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiNoticeMapper;
import ai.lawyers.system.mapper.lawyers.AiTodoMapper;
import ai.lawyers.system.service.lawyers.IWorkbenchService;

@Service
public class WorkbenchServiceImpl implements IWorkbenchService
{
    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Autowired
    private AiCallLedgerMapper aiCallLedgerMapper;

    @Autowired
    private AiCallAgentStatusMapper aiCallAgentStatusMapper;

    @Autowired
    private AiTodoMapper aiTodoMapper;

    @Autowired
    private AiNoticeMapper aiNoticeMapper;

    @Override
    public Map<String, Object> getWorkbenchStats(Long userId)
    {
        Map<String, Object> stats = new HashMap<>();
        // 今日通话数 + 今日服务时长(秒)
        Map<String, Object> callStats = aiCallRecordMapper.selectTodayCallStats();
        long todayCalls = 0L;
        long todayServiceDuration = 0L;
        if (callStats != null) {
            todayCalls = toLong(callStats.get("todayCalls"));
            todayServiceDuration = toLong(callStats.get("todayServiceDuration"));
        }
        stats.put("todayCalls", todayCalls);
        // 服务时长（分钟）
        stats.put("serviceDuration", todayServiceDuration / 60);

        // 今日满意度
        Map<String, Object> satStats = aiCallLedgerMapper.selectTodaySatisfactionStats();
        double avgSatisfaction = 0.0;
        long todayLedgerCount = 0L;
        if (satStats != null) {
            avgSatisfaction = toDouble(satStats.get("avgSatisfaction"));
            todayLedgerCount = toLong(satStats.get("todayLedgerCount"));
        }
        stats.put("satisfaction", Math.round(avgSatisfaction * 10) / 10.0);
        stats.put("todayLedgerCount", todayLedgerCount);

        // 在线时长（分钟）：当前坐席 loginTime 到 now
        long onlineDuration = 0L;
        String agentStatus = "0";
        if (userId != null) {
            AiCallAgentStatus agent = aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(userId);
            if (agent != null) {
                if (agent.getStatus() != null) {
                    agentStatus = agent.getStatus();
                }
                if (agent.getLoginTime() != null) {
                    long diffMs = new Date().getTime() - agent.getLoginTime().getTime();
                    if (diffMs > 0) {
                        onlineDuration = diffMs / (1000 * 60);
                    }
                }
            }
        }
        stats.put("onlineDuration", onlineDuration);
        stats.put("agentStatus", agentStatus);

        return stats;
    }

    @Override
    public List<AiTodo> getWorkbenchTodos(Long userId)
    {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }
        return aiTodoMapper.selectAiTodoListByUserId(userId);
    }

    @Override
    public List<AiCallRecord> getWorkbenchRecentCalls(Integer limit)
    {
        if (limit == null || limit <= 0) {
            limit = 5;
        }
        return aiCallRecordMapper.selectRecentCalls(limit);
    }

    @Override
    public List<AiNotice> getWorkbenchNotices(Integer limit)
    {
        if (limit == null || limit <= 0) {
            limit = 5;
        }
        return aiNoticeMapper.selectPublishedNotices(limit);
    }

    private long toLong(Object obj)
    {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return 0L; }
    }

    private double toDouble(Object obj)
    {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0.0; }
    }
}
