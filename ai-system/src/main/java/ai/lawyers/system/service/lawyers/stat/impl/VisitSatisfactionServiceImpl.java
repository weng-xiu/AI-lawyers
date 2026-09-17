package ai.lawyers.system.service.lawyers.stat.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.stat.VisitSatisfactionMapper;
import ai.lawyers.system.service.lawyers.stat.IVisitSatisfactionService;

/**
 * F5 智能回访满意度闭环 —— 满意度归因分析服务实现
 *
 * <p>规则版归因（P3 LLM 聚类上线前的可落地兜底，前端契约不变）：
 * 对回访意见 / 图文反馈文本按关键词归类为 6 类不满意原因，允许多因叠加，未命中归"其他"；
 * 情绪极性以结构化满意度档位为准（电话 1/2 档正向、3 中性、4 负向；图文 ≥4 正向、=3 中性、≤2 负向）。
 * 逾期任务按超期时长做三级分级（L1 &gt;0、L2 ≥24h、L3 ≥72h），供逐级升级决策。</p>
 *
 * @author ai-lawyers
 */
@Service
public class VisitSatisfactionServiceImpl implements IVisitSatisfactionService
{
    /** 归因取明细上限（防止内存归因文本量过大） */
    private static final int REASON_LIMIT = 1000;

    /** 逾期任务看板明细上限 */
    private static final int OVERDUE_LIMIT = 100;

    /** 逾期分级阈值（分钟）：L1 已逾期、L2 逾期≥1 天、L3 逾期≥3 天 */
    private static final long L2_MINUTES = 24 * 60L;
    private static final long L3_MINUTES = 72 * 60L;

    /** 不满意原因分类（顺序即 TOP 展示顺序），关键词小写包含匹配 */
    private static final String[][] REASON_RULES = new String[][]{
        {"解答未解决问题", "没解决", "未解决", "解决不了", "不专业", "没用", "没帮助", "答非所问", "没说清", "没解释", "敷衍", "推诿", "踢皮球", "不懂", "不会", "没听懂", "错误", "误导"},
        {"等待久/响应慢", "等待", "没人接", "打不通", "占线", "排队", "响应慢", "回拨慢", "半天", "太慢", "等了", "久等", "迟迟"},
        {"流程繁琐/多次转接", "流程", "手续", "麻烦", "转接", "转来转去", "反复", "多次", "材料多", "证明", "跑了", "复杂", "繁琐"},
        {"服务态度问题", "态度", "不耐烦", "凶", "语气", "生硬", "冷漠", "恶劣", "训斥", "不耐烦"},
        {"结果未落实", "没办成", "没落实", "没人管", "不了了之", "没回复", "没下文", "失望", "没结果", "石沉大海"},
        {"渠道/系统问题", "系统", "网站", "app", "小程序", "卡顿", "登录", "闪退", "网页", "链接", "打不开", "黑屏", "故障"}
    };

    private static final String REASON_OTHER = "其他";

    @Autowired
    private VisitSatisfactionMapper mapper;

    @Override
    public Map<String, Object> getBoard(Date beginTime, Date endTime)
    {
        // 1. 电话回访
        Map<String, Object> callbackOverview = defaultMap(mapper.selectCallbackOverview(beginTime, endTime));
        fillCallbackDerived(callbackOverview);

        // 2. 不满意原因（电话 + 图文 分开归因，再合并 TOP）
        List<Map<String, Object>> negCallbacks = nullToEmpty(
                mapper.selectNegativeCallbackOpinions(beginTime, endTime, REASON_LIMIT));
        List<Map<String, Object>> negEvaluations = nullToEmpty(
                mapper.selectNegativeEvaluationFeedback(beginTime, endTime, REASON_LIMIT));
        Map<String, Long> callbackReasonMap = classifyReasons(negCallbacks, "opinion");
        Map<String, Long> evaluationReasonMap = classifyReasons(negEvaluations, "feedback");
        Map<String, Long> mergedReasonMap = new LinkedHashMap<>(callbackReasonMap);
        evaluationReasonMap.forEach((k, v) -> mergedReasonMap.merge(k, v, Long::sum));

        // 3. 图文评价
        Map<String, Object> evaluationOverview = defaultMap(mapper.selectEvaluationOverview(beginTime, endTime));
        fillEvaluationDerived(evaluationOverview);

        // 4. 情绪极性（电话 + 图文 合并）
        Map<String, Object> sentiment = buildSentiment(callbackOverview, evaluationOverview);

        // 5. 回访任务运营 + 逾期分级
        Map<String, Object> taskOverview = defaultMap(mapper.selectTaskOverview());
        List<Map<String, Object>> overdueTasks = nullToEmpty(
                mapper.selectOverdueTasks(new Date(), OVERDUE_LIMIT));
        Map<String, Object> overdueSummary = buildOverdueSummary(overdueTasks);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callbackOverview", callbackOverview);
        data.put("callbackTrend", nullToEmpty(mapper.selectCallbackTrend(beginTime, endTime)));
        data.put("visitorRanking", nullToEmpty(mapper.selectByVisitor(beginTime, endTime)));
        data.put("callbackReasons", toReasonList(callbackReasonMap));
        data.put("evaluation", evaluationOverview);
        data.put("evaluationReasons", toReasonList(evaluationReasonMap));
        data.put("reasonTop", toReasonList(mergedReasonMap));
        data.put("sentiment", sentiment);
        data.put("taskOverview", taskOverview);
        data.put("overdueSummary", overdueSummary);
        data.put("overdueTasks", overdueTasks);
        return data;
    }

    /**
     * 对负向文本做关键词归因。一条文本可命中多个分类（多因叠加），全未命中归"其他"。
     *
     * @param rows    文本明细
     * @param textKey 文本字段名（opinion / feedback）
     * @return 原因 → 命中条数（按 REASON_RULES 顺序 + 其他）
     */
    private Map<String, Long> classifyReasons(List<Map<String, Object>> rows, String textKey)
    {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String[] rule : REASON_RULES)
        {
            counts.put(rule[0], 0L);
        }
        counts.put(REASON_OTHER, 0L);
        for (Map<String, Object> row : rows)
        {
            Object textObj = row.get(textKey);
            if (textObj == null)
            {
                continue;
            }
            String text = textObj.toString().toLowerCase();
            boolean hit = false;
            for (String[] rule : REASON_RULES)
            {
                for (int i = 1; i < rule.length; i++)
                {
                    if (text.contains(rule[i]))
                    {
                        counts.merge(rule[0], 1L, Long::sum);
                        hit = true;
                        break;
                    }
                }
            }
            if (!hit)
            {
                counts.merge(REASON_OTHER, 1L, Long::sum);
            }
        }
        return counts;
    }

    /** 原因计数 Map 转排序后的 TOP 列表（剔除 0 计数，按数量降序）。 */
    private List<Map<String, Object>> toReasonList(Map<String, Long> reasonMap)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        TreeMap<Long, List<String>> byCount = new TreeMap<>(Collections.reverseOrder());
        reasonMap.forEach((reason, cnt) -> {
            if (cnt > 0)
            {
                byCount.computeIfAbsent(cnt, k -> new ArrayList<>()).add(reason);
            }
        });
        byCount.forEach((cnt, reasons) -> reasons.forEach(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reason", r);
            item.put("count", cnt);
            list.add(item);
        }));
        return list;
    }

    /** 电话回访派生指标：满意率（1/2 档占已评价）、不满意率。 */
    private void fillCallbackDerived(Map<String, Object> o)
    {
        long rated = toLong(o.get("ratedCount"));
        long very = toLong(o.get("verySatisfiedCount"));
        long sat = toLong(o.get("satisfiedCount"));
        long unsat = toLong(o.get("unsatisfiedCount"));
        o.put("satisfiedRate", rated > 0 ? round2((very + sat) * 100d / rated) : 0d);
        o.put("unsatisfiedRate", rated > 0 ? round2(unsat * 100d / rated) : 0d);
    }

    /** 图文评价派生指标：好评率（overall≥4）、差评率（overall≤2）。 */
    private void fillEvaluationDerived(Map<String, Object> o)
    {
        long total = toLong(o.get("evalCount"));
        long positive = toLong(o.get("positiveCount"));
        long negative = toLong(o.get("negativeCount"));
        o.put("positiveRate", total > 0 ? round2(positive * 100d / total) : 0d);
        o.put("negativeRate", total > 0 ? round2(negative * 100d / total) : 0d);
    }

    /** 合并电话 + 图文的情绪极性分布（正/中/负）与正向率。 */
    private Map<String, Object> buildSentiment(Map<String, Object> cb, Map<String, Object> ev)
    {
        long cbPositive = toLong(cb.get("verySatisfiedCount")) + toLong(cb.get("satisfiedCount"));
        long cbNeutral = toLong(cb.get("normalCount"));
        long cbNegative = toLong(cb.get("unsatisfiedCount"));

        long evTotal = toLong(ev.get("evalCount"));
        long evPositive = toLong(ev.get("positiveCount"));
        long evNegative = toLong(ev.get("negativeCount"));
        long evNeutral = evTotal - evPositive - evNegative;

        long positive = cbPositive + evPositive;
        long neutral = cbNeutral + Math.max(evNeutral, 0L);
        long negative = cbNegative + evNegative;
        long total = positive + neutral + negative;

        Map<String, Object> sentiment = new LinkedHashMap<>();
        sentiment.put("positive", positive);
        sentiment.put("neutral", neutral);
        sentiment.put("negative", negative);
        sentiment.put("total", total);
        sentiment.put("positiveRate", total > 0 ? round2(positive * 100d / total) : 0d);
        return sentiment;
    }

    /** 逾期三级分级：给每条任务打 level，并汇总各级数量。 */
    private Map<String, Object> buildOverdueSummary(List<Map<String, Object>> tasks)
    {
        long l1 = 0, l2 = 0, l3 = 0;
        for (Map<String, Object> t : tasks)
        {
            long minutes = toLong(t.get("overdueMinutes"));
            int level;
            if (minutes >= L3_MINUTES) { level = 3; l3++; }
            else if (minutes >= L2_MINUTES) { level = 2; l2++; }
            else { level = 1; l1++; }
            t.put("level", level);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        // 注意：列表只取了 OVERDUE_LIMIT 条，各级计数为该明细窗口内口径
        summary.put("level1Count", l1);
        summary.put("level2Count", l2);
        summary.put("level3Count", l3);
        summary.put("windowTotal", tasks.size());
        summary.put("l2ThresholdMinutes", L2_MINUTES);
        summary.put("l3ThresholdMinutes", L3_MINUTES);
        return summary;
    }

    private static double round2(double v)
    {
        return Math.round(v * 100d) / 100d;
    }

    private static long toLong(Object v)
    {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return 0L; }
    }

    private Map<String, Object> defaultMap(Map<String, Object> map)
    {
        return map == null ? new LinkedHashMap<>() : map;
    }

    private <T> List<T> nullToEmpty(List<T> list)
    {
        return list == null ? Collections.emptyList() : list;
    }
}
