package ai.lawyers.system.service.lawyers.stat.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.stat.SlaBoardMapper;
import ai.lawyers.system.service.lawyers.stat.ISlaBoardService;

/**
 * F9 工单 SLA 可视化看板聚合服务实现
 *
 * <p>全部基于现有工单/转办/策略表实时聚合，不依赖三期 {@code ai_stat_minute} 物化表；
 * 三期 P3-F3 落地后仅需将 Mapper 数据源切到物化表，接口与前端契约不变。</p>
 *
 * @author ai-lawyers
 */
@Service
public class SlaBoardServiceImpl implements ISlaBoardService
{
    /** 默认临近超时预警窗口（分钟） */
    private static final int DEFAULT_WARN_MINUTES = 60;

    /** 超时 TOP 与临近超时列表条数 */
    private static final int TOP_LIMIT = 10;

    @Autowired
    private SlaBoardMapper slaBoardMapper;

    @Override
    public Map<String, Object> getSlaBoard(Date beginTime, Date endTime, Integer warnMinutes)
    {
        int warn = (warnMinutes == null || warnMinutes <= 0) ? DEFAULT_WARN_MINUTES : warnMinutes;

        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> dueSoon = nullToEmptyList(
                slaBoardMapper.selectDueSoon(warn, TOP_LIMIT));
        data.put("overview", buildOverview(beginTime, endTime,
                slaBoardMapper.countDueSoon(warn)));
        data.put("achieveByBizType", nullToEmptyList(
                slaBoardMapper.selectAchieveByBizType(beginTime, endTime)));
        data.put("achieveByAssignee", nullToEmptyList(
                slaBoardMapper.selectAchieveByAssignee(beginTime, endTime)));
        data.put("overtimeTop", nullToEmptyList(
                slaBoardMapper.selectOvertimeTop(beginTime, endTime, TOP_LIMIT)));
        data.put("dueSoon", dueSoon);
        data.put("transferStat", nullToEmptyList(
                slaBoardMapper.selectTransferStat(beginTime, endTime)));
        data.put("warnMinutes", warn);
        return data;
    }

    /**
     * 总体概况：补齐准时率/办结率等派生指标，保证前端字段恒有值。
     *
     * @param dueSoonCount 临近超时（剩余时限 ≤ 预警窗口的进行中工单）数量
     */
    private Map<String, Object> buildOverview(Date beginTime, Date endTime, long dueSoonCount)
    {
        Map<String, Object> overview = slaBoardMapper.selectOverview(beginTime, endTime);
        if (overview == null)
        {
            overview = new LinkedHashMap<>();
        }
        long total = toLong(overview.get("totalCount"));
        long closed = toLong(overview.get("closedCount"));
        long onTime = toLong(overview.get("closedOnTimeCount"));
        long closedOvertime = toLong(overview.get("closedOvertimeCount"));
        long openOvertime = toLong(overview.get("openOvertimeCount"));
        overview.put("achievementRate", total > 0
                ? Math.round(onTime * 10000d / total) / 100d : 0d);
        overview.put("closeRate", total > 0
                ? Math.round(closed * 10000d / total) / 100d : 0d);
        overview.put("overtimeRate", closed > 0
                ? Math.round(closedOvertime * 10000d / closed) / 100d : 0d);
        overview.put("totalOvertimeCount", closedOvertime + openOvertime);
        // 保证关键计数字段恒在，避免历史库驱动不返回全 0 聚合行时前端 undefined
        putIfAbsent(overview, "totalCount", 0L);
        putIfAbsent(overview, "closedCount", 0L);
        putIfAbsent(overview, "closedOnTimeCount", 0L);
        putIfAbsent(overview, "closedOvertimeCount", 0L);
        putIfAbsent(overview, "openCount", 0L);
        putIfAbsent(overview, "openOvertimeCount", 0L);
        overview.put("dueSoonCount", dueSoonCount);
        return overview;
    }

    private static long toLong(Object v)
    {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static void putIfAbsent(Map<String, Object> map, String key, Object value)
    {
        if (!map.containsKey(key) || map.get(key) == null)
        {
            map.put(key, value);
        }
    }

    private List<Map<String, Object>> nullToEmptyList(List<Map<String, Object>> list)
    {
        return list == null ? java.util.Collections.emptyList() : list;
    }
}
