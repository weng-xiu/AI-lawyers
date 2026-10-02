package ai.lawyers.system.service.lawyers;

import java.util.Date;
import java.util.List;
import java.util.Map;
import ai.lawyers.system.domain.lawyers.stat.AiSatisfactionForecast;

/**
 * 预测性满意度 Service 接口（P1-11）
 *
 * <p>轻量加权模型对无问卷回访的通话批量预测满意度（1~4），
 * 已回收实际满意度的通话保留真实值并输出预测对照（一致率）。</p>
 *
 * @author ai-lawyers
 */
public interface IAiSatisfactionForecastService
{
    /**
     * 区间内满意度预测汇总：预测分布/均分、实际回收对照、一致率。
     *
     * @param beginTime 起始时间（含）
     * @param endTime   截止时间（跨度 ≤ 31 天）
     * @return total/predictedDist/avgPredicted/actualCount/avgActual/agreementRate/agreementWithinOne
     */
    Map<String, Object> summary(Date beginTime, Date endTime);

    /**
     * 区间内逐条预测明细（分页）。
     *
     * @param pageNum  页码（1 起）
     * @param pageSize 页大小（≤200）
     */
    List<AiSatisfactionForecast> list(Date beginTime, Date endTime, int pageNum, int pageSize);
}
