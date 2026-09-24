package ai.lawyers.system.service.impl.lawyers.stat;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.quality.AiAgentStatusLogMapper;
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
    /** 默认服务水平阈值（秒）：X 秒内接听占比；技能组配置缺失时使用 */
    private static final int DEFAULT_SLA_THRESHOLD_SECONDS = 20;

    @Autowired
    private DashboardMapper dashboardMapper;

    @Autowired
    private AiAgentStatusLogMapper agentStatusLogMapper;

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
        data.put("slaSummary", buildSlaSummary(beginTime, endTime));
        data.put("agentStatusDuration", buildAgentStatusDuration(beginTime, endTime));
        data.put("bizMetrics", buildBizMetrics(beginTime, endTime));
        return data;
    }

    /**
     * F10 公共法律服务业务类指标：语种分布 / 关怀模式使用率 / 条线转办统计 /
     * 渠道活跃与绑定 / 公众端满意度。全部实时聚合现有业务表；
     * P3-F3 ai_stat_minute 物化表落地后仅需切换 Mapper 数据源，接口与前端契约不变。
     * Copilot 建议采纳率依赖 F4 埋点（坐席辅助深化未启动），本批先透出占位 null。
     */
    private Map<String, Object> buildBizMetrics(Date beginTime, Date endTime)
    {
        Map<String, Object> biz = new LinkedHashMap<>();

        // 语种分布（来电人档案 language_preference，无档案记 zh-CN）
        Map<String, String> langNames = new LinkedHashMap<>();
        langNames.put("zh-CN", "普通话");
        langNames.put("yue-CN", "粤语");
        List<Map<String, Object>> langDist = new java.util.ArrayList<>();
        for (Map<String, Object> row : nullToEmptyList(dashboardMapper.selectLanguageDist(beginTime, endTime)))
        {
            Map<String, Object> item = new LinkedHashMap<>();
            String lang = str(row.get("lang"));
            item.put("lang", lang);
            item.put("langName", langNames.getOrDefault(lang, lang));
            item.put("value", toLong(row.get("value")));
            langDist.add(item);
        }
        biz.put("languageDist", langDist);

        // 关怀模式使用率（按通话量与按独立号码两个口径）
        Map<String, Object> care = nullToEmpty(dashboardMapper.selectCareUsage(beginTime, endTime));
        long totalCalls = toLong(care.get("totalCalls"));
        long careCalls = toLong(care.get("careCalls"));
        long totalCallers = toLong(care.get("totalCallers"));
        long careCallers = toLong(care.get("careCallers"));
        care.put("careCallRate", totalCalls > 0 ? Math.round(careCalls * 10000d / totalCalls) / 100d : 0d);
        care.put("careCallerRate", totalCallers > 0 ? Math.round(careCallers * 10000d / totalCallers) / 100d : 0d);
        biz.put("careUsage", care);

        // 业务条线转办统计（条线代码→中文名映射与 SLA 看板 bizText 保持一致）
        Map<String, String> lineNames = new LinkedHashMap<>();
        lineNames.put("LEGAL_AID", "法律援助");
        lineNames.put("MEDIATION", "人民调解");
        lineNames.put("NOTARY", "公证");
        lineNames.put("FORENSIC", "司法鉴定");
        lineNames.put("ARBITRATION", "仲裁");
        lineNames.put("HOTLINE_12345", "12345协同");
        List<Map<String, Object>> lines = new java.util.ArrayList<>();
        for (Map<String, Object> row : nullToEmptyList(dashboardMapper.selectTransferLineStats(beginTime, endTime)))
        {
            Map<String, Object> item = new LinkedHashMap<>(row);
            String line = str(row.get("line"));
            item.put("lineName", lineNames.getOrDefault(line, line));
            long transferCount = toLong(row.get("transferCount"));
            long closedCount = toLong(row.get("closedCount"));
            item.put("closeRate", transferCount > 0 ? Math.round(closedCount * 10000d / transferCount) / 100d : 0d);
            lines.add(item);
        }
        biz.put("transferLines", lines);

        // 公众端渠道活跃（区间会话）与渠道绑定（累计），渠道代码→中文名与公众端 MyChannels 保持一致
        Map<String, String> channelNames = new LinkedHashMap<>();
        channelNames.put("PHONE", "热线电话");
        channelNames.put("WECHAT_MP", "微信公众号");
        channelNames.put("WECHAT_MINI", "微信小程序");
        channelNames.put("H5", "H5页面");
        channelNames.put("WEB", "网站");
        biz.put("channelSessions", withChannelName(dashboardMapper.selectChannelSessions(beginTime, endTime), channelNames));
        biz.put("channelBinds", withChannelName(dashboardMapper.selectChannelBinds(), channelNames));

        // 公众端满意度（图文四维评价）
        biz.put("portalSatisfaction", nullToEmpty(dashboardMapper.selectPortalSatisfaction(beginTime, endTime)));

        // Copilot 建议采纳率：依赖 F4 埋点，未启动前透出 null（前端不渲染该卡）
        biz.put("copilotAdoption", null);
        return biz;
    }

    /** 渠道代码行补中文名 */
    private List<Map<String, Object>> withChannelName(List<Map<String, Object>> rows, Map<String, String> names)
    {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> row : nullToEmptyList(rows))
        {
            Map<String, Object> item = new LinkedHashMap<>(row);
            String channel = str(row.get("channel"));
            item.put("channelName", names.getOrDefault(channel, channel));
            out.add(item);
        }
        return out;
    }

    /**
     * T4-3 SLA 汇总：服务水平（X 秒内接听占比）、放弃率、平均等待，
     * 并把技能组配置的服务水平阈值（service_level_threshold，缺省 20 秒）透出。
     */
    private Map<String, Object> buildSlaSummary(Date beginTime, Date endTime)
    {
        Map<String, Object> sla = nullToEmpty(
                dashboardMapper.selectSlaSummary(beginTime, endTime, DEFAULT_SLA_THRESHOLD_SECONDS));
        long answered = toLong(sla.get("answeredCount"));
        long within = toLong(sla.get("withinThreshold"));
        long abandoned = toLong(sla.get("abandonedCount"));
        long totalQueued = toLong(sla.get("totalQueued"));
        sla.put("serviceLevel", answered > 0
                ? Math.round(within * 10000d / answered) / 100d : 0d);
        sla.put("abandonRate", totalQueued > 0
                ? Math.round(abandoned * 10000d / totalQueued) / 100d : 0d);
        return sla;
    }

    /**
     * T4-3 坐席状态时长（秒）：基于 ai_agent_status_log 按目标状态汇总区间内停留时长，
     * 输出在线/忙碌/休息/通话/话后等口径，供效能与大屏展示。
     */
    private Map<String, Object> buildAgentStatusDuration(Date beginTime, Date endTime)
    {
        Map<String, Object> result = new HashMap<>();
        long talkSeconds = 0;     // 通话中（call_status=1）
        long afterWorkSeconds = 0; // 话后（call_status=5）
        long readySeconds = 0;    // 在线且空闲（status=1, call_status=0）
        long busySeconds = 0;     // 忙碌（status=2，通话+话后之外的忙碌口径）
        try
        {
            List<Map<String, Object>> rows = agentStatusLogMapper.sumStatusDuration(
                    formatDate(beginTime), formatDate(endTime));
            for (Map<String, Object> row : rows)
            {
                long secs = toLong(row.get("durationSum"));
                String toStatus = str(row.get("toStatus"));
                String toCall = str(row.get("toCallStatus"));
                if ("1".equals(toCall))
                {
                    talkSeconds += secs;
                }
                else if ("5".equals(toCall))
                {
                    afterWorkSeconds += secs;
                }
                else if ("0".equals(toCall) && "1".equals(toStatus))
                {
                    readySeconds += secs;
                }
                else if ("2".equals(toStatus) && !"5".equals(toCall))
                {
                    busySeconds += secs;
                }
            }
        }
        catch (Exception e)
        {
            // 状态流水为 T4-3 新增表，缺失/异常时不影响大屏其他模块
        }
        result.put("talkSeconds", talkSeconds);
        result.put("afterWorkSeconds", afterWorkSeconds);
        result.put("readySeconds", readySeconds);
        result.put("busySeconds", busySeconds);
        result.put("talkMinutes", Math.round(talkSeconds / 60d));
        result.put("afterWorkMinutes", Math.round(afterWorkSeconds / 60d));
        result.put("readyMinutes", Math.round(readySeconds / 60d));
        return result;
    }

    private static long toLong(Object v)
    {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static String str(Object v)
    {
        return v == null ? "" : String.valueOf(v);
    }

    private static String formatDate(Date d)
    {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(d == null ? new Date() : d);
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
