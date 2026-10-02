package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.stat.AiSatisfactionForecast;
import ai.lawyers.system.service.lawyers.IAiSatisfactionForecastService;
import ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentDetector;
import ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentResult;

/**
 * 预测性满意度 Service 实现（P1-11）。
 *
 * <p>数据口径：ai_call_record 主表 left join 质检（ai_status=2 完成，
 * 申诉成立时取 adjusted_score）与台账实际满意度（ai_call_ledger.satisfaction，
 * 1非常满意~4不满意）。转写情绪复用 {@link EmotionIntentDetector}（纯规则，
 * 批量预测成本为零）。预测只读不落库，与实际回收满意度对照输出。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AiSatisfactionForecastServiceImpl implements IAiSatisfactionForecastService
{
    /** 单次查询最大跨度（天） */
    private static final int MAX_SPAN_DAYS = 31;
    /** 页大小上限 */
    private static final int MAX_PAGE_SIZE = 200;

    private static final String FORECAST_SQL =
            "select r.record_id as recordId, r.call_time as callTime, r.duration, r.status,"
          + " r.transcript,"
          + " q.total_score as totalScore, q.adjusted_score as adjustedScore, q.dimension_json as dimensionJson,"
          + " l.satisfaction as actual"
          + " from ai_call_record r"
          + " left join ai_quality_inspection q on q.record_id = r.record_id and q.ai_status = '2'"
          + " left join ai_call_ledger l on l.record_id = r.record_id"
          + "      and l.satisfaction is not null and l.satisfaction != ''"
          + " where r.call_time >= ? and r.call_time < ?"
          + " order by r.record_id desc";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 情绪识别器（纯规则词库，无状态可共享） */
    private final EmotionIntentDetector emotionDetector = new EmotionIntentDetector();

    /** 预测权重（yml 可配） */
    private final SatisfactionPredictor.Weights weights = new SatisfactionPredictor.Weights();

    @Value("${satisfaction.forecast.weight-urgent:1.5}")
    public void setWeightUrgent(double v)
    {
        weights.urgent = v;
    }

    @Value("${satisfaction.forecast.weight-negative:0.8}")
    public void setWeightNegative(double v)
    {
        weights.negative = v;
    }

    @Value("${satisfaction.forecast.weight-qc-low-emotion:0.8}")
    public void setWeightQcLowEmotion(double v)
    {
        weights.qcLowEmotion = v;
    }

    @Value("${satisfaction.forecast.weight-qc-low-total:0.6}")
    public void setWeightQcLowTotal(double v)
    {
        weights.qcLowTotal = v;
    }

    @Value("${satisfaction.forecast.weight-short-call:0.4}")
    public void setWeightShortCall(double v)
    {
        weights.shortCall = v;
    }

    @Value("${satisfaction.forecast.weight-missed:1.0}")
    public void setWeightMissed(double v)
    {
        weights.missed = v;
    }

    @Value("${satisfaction.forecast.weight-transferred:0.3}")
    public void setWeightTransferred(double v)
    {
        weights.transferred = v;
    }

    @Value("${satisfaction.forecast.short-seconds:30}")
    public void setShortSeconds(int v)
    {
        weights.shortSeconds = v;
    }

    @Override
    public Map<String, Object> summary(Date beginTime, Date endTime)
    {
        validateRange(beginTime, endTime);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(FORECAST_SQL, beginTime, endTime);
        int[] dist = new int[5];
        double predictedSum = 0;
        int actualCount = 0;
        double actualSum = 0;
        int agree = 0;
        int agreeWithinOne = 0;
        int total = 0;
        if (rows != null)
        {
            for (Map<String, Object> row : rows)
            {
                ForecastRow fr = toForecastRow(row);
                dist[fr.predicted]++;
                predictedSum += fr.predicted;
                total++;
                if (fr.actual != null)
                {
                    actualCount++;
                    actualSum += fr.actual;
                    if (fr.actual.equals(fr.predicted))
                    {
                        agree++;
                    }
                    if (Math.abs(fr.actual - fr.predicted) <= 1)
                    {
                        agreeWithinOne++;
                    }
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        Map<String, Object> predictedDist = new LinkedHashMap<>();
        for (int level = 1; level <= 4; level++)
        {
            predictedDist.put(String.valueOf(level), dist[level]);
        }
        result.put("predictedDist", predictedDist);
        result.put("avgPredicted", total == 0 ? 0 : round2(predictedSum / total));
        result.put("actualCount", actualCount);
        result.put("avgActual", actualCount == 0 ? 0 : round2(actualSum / actualCount));
        result.put("agreementRate", actualCount == 0 ? 0 : round2(agree * 100.0 / actualCount));
        result.put("agreementWithinOne", actualCount == 0 ? 0 : round2(agreeWithinOne * 100.0 / actualCount));
        return result;
    }

    @Override
    public List<AiSatisfactionForecast> list(Date beginTime, Date endTime, int pageNum, int pageSize)
    {
        validateRange(beginTime, endTime);
        int page = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                FORECAST_SQL + " limit " + ((page - 1) * (long) size) + ", " + size,
                beginTime, endTime);
        List<AiSatisfactionForecast> list = new ArrayList<>();
        if (rows != null)
        {
            for (Map<String, Object> row : rows)
            {
                ForecastRow fr = toForecastRow(row);
                AiSatisfactionForecast item = new AiSatisfactionForecast();
                item.setRecordId(fr.recordId);
                item.setCallTime(fr.callTime);
                item.setDuration(fr.duration);
                item.setStatus(fr.status);
                item.setEmotion(fr.emotion);
                item.setQcTotal(fr.qcTotal);
                item.setQcEmotion(fr.qcEmotion);
                item.setPredicted(fr.predicted);
                item.setScoreDetail(fr.detail);
                item.setActual(fr.actual);
                list.add(item);
            }
        }
        return list;
    }

    private void validateRange(Date beginTime, Date endTime)
    {
        if (beginTime == null || endTime == null)
        {
            throw new ServiceException("时间区间不能为空");
        }
        if (!endTime.after(beginTime))
        {
            throw new ServiceException("截止时间须晚于起始时间");
        }
        if (endTime.getTime() - beginTime.getTime() > (long) MAX_SPAN_DAYS * 86400_000L)
        {
            throw new ServiceException("时间跨度不能超过 " + MAX_SPAN_DAYS + " 天");
        }
    }

    /** 行数据 → 特征与预测（内部结构） */
    private ForecastRow toForecastRow(Map<String, Object> row)
    {
        ForecastRow fr = new ForecastRow();
        fr.recordId = ((Number) row.get("recordId")).longValue();
        Object callTime = row.get("callTime");
        fr.callTime = callTime instanceof Date ? (Date) callTime : null;
        fr.duration = row.get("duration") instanceof Number ? ((Number) row.get("duration")).intValue() : null;
        Object status = row.get("status");
        fr.status = status == null ? null : String.valueOf(status);

        String transcript = row.get("transcript") == null ? null : String.valueOf(row.get("transcript"));
        EmotionIntentResult emotion = emotionDetector.analyze(transcript);
        fr.emotion = emotion.getEmotion();

        fr.qcTotal = effectiveTotalScore(row);
        fr.qcEmotion = parseEmotionAttitude(row.get("dimensionJson"));
        fr.predicted = SatisfactionPredictor.predict(fr.emotion, fr.qcEmotion, fr.qcTotal, fr.duration, fr.status, weights);
        fr.detail = SatisfactionPredictor.detail(fr.emotion, fr.qcEmotion, fr.qcTotal, fr.duration, fr.status, weights);
        fr.actual = parseSatisfaction(row.get("actual"));
        return fr;
    }

    /** 申诉成立取调整分，否则原始 AI 总分 */
    private Double effectiveTotalScore(Map<String, Object> row)
    {
        Object adjusted = row.get("adjustedScore");
        if (adjusted instanceof Number)
        {
            return ((Number) adjusted).doubleValue();
        }
        Object total = row.get("totalScore");
        return total instanceof Number ? ((Number) total).doubleValue() : null;
    }

    /** dimension_json 提取 emotionAttitude（0~100），缺失返回 null */
    private Double parseEmotionAttitude(Object dimensionJson)
    {
        if (dimensionJson == null)
        {
            return null;
        }
        try
        {
            JsonNode node = objectMapper.readTree(String.valueOf(dimensionJson)).path("emotionAttitude");
            return node.isMissingNode() || !node.isNumber() ? null : node.asDouble();
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    /** 实际满意度字符串 → 整数 1~4，非法返回 null */
    private Integer parseSatisfaction(Object value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            int v = Integer.parseInt(String.valueOf(value).trim());
            return v >= 1 && v <= 4 ? v : null;
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private double round2(double v)
    {
        return Math.round(v * 100) / 100.0;
    }

    /** 内部行结构 */
    private static class ForecastRow
    {
        Long recordId;
        Date callTime;
        Integer duration;
        String status;
        String emotion;
        Double qcTotal;
        Double qcEmotion;
        Integer predicted;
        String detail;
        Integer actual;
    }
}
