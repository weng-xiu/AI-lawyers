package ai.lawyers.system.service.impl.lawyers;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 话务预测/排班纯计算器（P1-9，无依赖便于单测）。
 *
 * <p>轻量预测口径：hour-of-week 基线剖面（近 N 周 ai_stat_minute call_total
 * 按星期+小时平均）× 日型系数（假日/事件/调休）→ 未来 N 天逐时进线量；
 * 排班建议按 Erlang 简化式：所需坐席 = ceil(话务量 Erlang / 利用率)，
 * 话务量 Erlang = 预测量 × AHT / 3600（忽略排队服务水平目标，
 * 重型 WFM 的 Erlang-C/仿真留给后续增强）。</p>
 *
 * @author ai-lawyers
 */
public class ForecastCalculator
{
    /** 假日/事件/调休以外日型的默认系数 */
    public static final BigDecimal DEFAULT_FACTOR = BigDecimal.ONE;

    /**
     * 日型系数。
     *
     * @param dayType          HOLIDAY/EVENT/WORKDAY/null（常规日）
     * @param eventCoefficient EVENT 日历条目系数（null 或 ≤0 视为 1）
     * @param holidayFactor    假日统一系数（配置）
     * @param workdayFactor    调休补班系数（配置）
     */
    public static BigDecimal dayFactor(String dayType, BigDecimal eventCoefficient,
                                       BigDecimal holidayFactor, BigDecimal workdayFactor)
    {
        if ("HOLIDAY".equals(dayType))
        {
            return positive(holidayFactor);
        }
        if ("WORKDAY".equals(dayType))
        {
            return positive(workdayFactor);
        }
        if ("EVENT".equals(dayType))
        {
            return eventCoefficient == null || eventCoefficient.signum() <= 0
                    ? DEFAULT_FACTOR : eventCoefficient;
        }
        return DEFAULT_FACTOR;
    }

    /**
     * 逐时预测量 = 基线剖面 × 系数（四舍五入取整）。
     *
     * @param baseHourly 基线剖面该 weekday/hour 的平均进线量（无数据为 0）
     * @param factor     日型系数
     */
    public static int forecastVolume(double baseHourly, BigDecimal factor)
    {
        if (baseHourly <= 0)
        {
            return 0;
        }
        return BigDecimal.valueOf(baseHourly)
                .multiply(factor == null ? DEFAULT_FACTOR : factor)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /**
     * 建议坐席数 = ceil(话务量 Erlang / 利用率)，有话量时至少 1 人。
     *
     * @param hourlyVolume 逐时预测进线量
     * @param ahtSeconds   平均处理时长（秒，含通话+话后）
     * @param utilization  坐席利用率（0~1）
     */
    public static int agents(int hourlyVolume, int ahtSeconds, double utilization)
    {
        if (hourlyVolume <= 0 || ahtSeconds <= 0 || utilization <= 0)
        {
            return 0;
        }
        double erlangs = hourlyVolume * (double) ahtSeconds / 3600d;
        return (int) Math.ceil(erlangs / utilization);
    }

    private static BigDecimal positive(BigDecimal value)
    {
        return value == null || value.signum() <= 0 ? DEFAULT_FACTOR : value;
    }
}
