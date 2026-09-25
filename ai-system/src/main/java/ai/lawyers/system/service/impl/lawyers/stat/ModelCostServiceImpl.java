package ai.lawyers.system.service.impl.lawyers.stat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.mapper.lawyers.stat.AiModelCallLogMapper;
import ai.lawyers.system.service.lawyers.stat.IModelCostService;

/**
 * 大模型调用成本与质量看板 Service 实现（P3-E5）
 *
 * <p>时间区间默认近 7 天；费用与 Token 直接取异步埋点表 ai_model_call_log 的聚合结果，
 * 单位通话成本 = 区间估算费用 ÷ 区间话单量（ai_call_record.call_time 同口径）。</p>
 *
 * @author ai-lawyers
 * @date 2026-09-25
 */
@Service
public class ModelCostServiceImpl implements IModelCostService
{
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 单次查询最大区间（天），防止全表扫描拖垮库 */
    private static final int MAX_RANGE_DAYS = 92;

    @Autowired
    private AiModelCallLogMapper modelCallLogMapper;

    @Override
    public Map<String, Object> overview(String beginTime, String endTime)
    {
        String[] range = normalizeRange(beginTime, endTime);
        Map<String, Object> data = modelCallLogMapper.selectOverview(range[0], range[1]);
        if (data == null)
        {
            data = new LinkedHashMap<>();
        }
        long totalCalls = asLong(data.get("totalCalls"));
        long successCalls = asLong(data.get("successCalls"));
        BigDecimal costAmount = asDecimal(data.get("costAmount"));
        long callCount = modelCallLogMapper.selectCallCount(range[0], range[1]);

        Map<String, Object> result = new LinkedHashMap<>(data);
        result.put("beginTime", range[0]);
        result.put("endTime", range[1]);
        // 成功率（%，保留 2 位）
        result.put("successRate", totalCalls <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(successCalls).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCalls), 2, RoundingMode.HALF_UP));
        // 单位通话 AI 成本（元/通，6 位小数；话单为 0 时记 0）
        result.put("callCount", callCount);
        result.put("costPerCall", callCount <= 0 ? BigDecimal.ZERO
                : costAmount.divide(BigDecimal.valueOf(callCount), 6, RoundingMode.HALF_UP));
        return result;
    }

    @Override
    public List<Map<String, Object>> trend(String beginTime, String endTime)
    {
        String[] range = normalizeRange(beginTime, endTime);
        return modelCallLogMapper.selectTrend(range[0], range[1]);
    }

    @Override
    public List<Map<String, Object>> byModel(String beginTime, String endTime)
    {
        String[] range = normalizeRange(beginTime, endTime);
        return modelCallLogMapper.selectByModel(range[0], range[1]);
    }

    @Override
    public List<Map<String, Object>> byScene(String beginTime, String endTime)
    {
        String[] range = normalizeRange(beginTime, endTime);
        return modelCallLogMapper.selectByScene(range[0], range[1]);
    }

    @Override
    public List<AiModelCallLog> logList(AiModelCallLog query)
    {
        if (query == null)
        {
            query = new AiModelCallLog();
        }
        // 明细未传区间时同样兜默认近 7 天，避免无界查询
        String begin = query.getParams() == null ? null
                : (String) query.getParams().get("beginTime");
        String end = query.getParams() == null ? null
                : (String) query.getParams().get("endTime");
        if (StringUtils.isEmpty(begin) || StringUtils.isEmpty(end))
        {
            String[] range = normalizeRange(begin, end);
            query.getParams().put("beginTime", range[0]);
            query.getParams().put("endTime", range[1] + " 23:59:59");
        }
        return modelCallLogMapper.selectLogList(query);
    }

    /**
     * 规范化时间区间：空值默认近 7 天（含今天）；仅接受 yyyy-MM-dd；
     * 区间超 {@link #MAX_RANGE_DAYS} 天时以 begin 为起点截断；begin 晚于 end 时互换。
     *
     * @return [beginDate, endDate]，Mapper 内部按 end+1 天开区间处理
     */
    private String[] normalizeRange(String beginTime, String endTime)
    {
        LocalDate end;
        LocalDate begin;
        try
        {
            end = StringUtils.isEmpty(endTime) ? LocalDate.now() : LocalDate.parse(endTime, DAY_FMT);
            begin = StringUtils.isEmpty(beginTime) ? end.minusDays(6) : LocalDate.parse(beginTime, DAY_FMT);
        }
        catch (Exception e)
        {
            // 非法日期格式：直接兜近 7 天，避免 500
            LocalDate today = LocalDate.now();
            return new String[] { today.minusDays(6).format(DAY_FMT), today.format(DAY_FMT) };
        }
        if (begin.isAfter(end))
        {
            LocalDate tmp = begin;
            begin = end;
            end = tmp;
        }
        if (begin.isBefore(end.minusDays(MAX_RANGE_DAYS - 1L)))
        {
            begin = end.minusDays(MAX_RANGE_DAYS - 1L);
        }
        return new String[] { begin.format(DAY_FMT), end.format(DAY_FMT) };
    }

    private long asLong(Object v)
    {
        if (v == null)
        {
            return 0L;
        }
        if (v instanceof Number)
        {
            return ((Number) v).longValue();
        }
        try
        {
            return Long.parseLong(String.valueOf(v));
        }
        catch (NumberFormatException e)
        {
            return 0L;
        }
    }

    private BigDecimal asDecimal(Object v)
    {
        if (v == null)
        {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal)
        {
            return (BigDecimal) v;
        }
        try
        {
            return new BigDecimal(String.valueOf(v));
        }
        catch (NumberFormatException e)
        {
            return BigDecimal.ZERO;
        }
    }
}
