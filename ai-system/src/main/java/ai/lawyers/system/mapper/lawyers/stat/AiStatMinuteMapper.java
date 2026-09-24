package ai.lawyers.system.mapper.lawyers.stat;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.stat.AiStatMinute;

/**
 * 分钟级物化统计 Mapper（P3-F3）
 *
 * @author ai-lawyers
 */
public interface AiStatMinuteMapper
{
    /** 幂等覆盖写（uk_stat 唯一键冲突时更新 metric_value/create_time） */
    int upsert(AiStatMinute stat);

    /** 批量幂等覆盖写 */
    int batchUpsert(@Param("list") List<AiStatMinute> list);

    /** 按周期+指标键查询（dimension=ALL） */
    List<AiStatMinute> selectByPeriod(@Param("beginTime") String beginTime,
                                      @Param("endTime") String endTime,
                                      @Param("metricKey") String metricKey);

    /** 按周期+指标键集合查询 */
    List<AiStatMinute> selectByPeriodAndKeys(@Param("beginTime") String beginTime,
                                             @Param("endTime") String endTime,
                                             @Param("metricKeys") List<String> metricKeys);
}
