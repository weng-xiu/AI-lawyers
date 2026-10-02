package ai.lawyers.system.mapper.lawyers.forecast;

import java.util.List;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastPlan;

/**
 * 排班计划确认快照 Mapper（P1-9）
 *
 * @author ai-lawyers
 */
public interface AiForecastPlanMapper
{
    /** 新增确认快照 */
    int insertPlan(AiForecastPlan plan);

    /** 条件查询（确认人/日期区间） */
    List<AiForecastPlan> selectPlanList(AiForecastPlan query);

    /** 按ID查询 */
    AiForecastPlan selectPlanById(Long planId);
}
