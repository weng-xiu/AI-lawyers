package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * P1-9：话务预测/排班纯计算器单测。
 *
 * @author ai-lawyers
 */
class ForecastCalculatorTest
{
    @Test
    void dayFactor_regularDay_isOne()
    {
        assertEquals(0, BigDecimal.ONE.compareTo(ForecastCalculator.dayFactor(null, null, bd("0.4"), bd("1.2"))));
    }

    @Test
    void dayFactor_holiday_usesConfiguredFactor()
    {
        assertEquals(0, bd("0.4").compareTo(ForecastCalculator.dayFactor("HOLIDAY", null, bd("0.4"), bd("1.2"))));
        // 配置缺失回退 1
        assertEquals(0, BigDecimal.ONE.compareTo(ForecastCalculator.dayFactor("HOLIDAY", null, null, bd("1.2"))));
    }

    @Test
    void dayFactor_event_usesCalendarCoefficient()
    {
        assertEquals(0, bd("1.5").compareTo(ForecastCalculator.dayFactor("EVENT", bd("1.5"), bd("0.4"), bd("1.2"))));
        // 事件条目系数缺失/非法回退 1
        assertEquals(0, BigDecimal.ONE.compareTo(ForecastCalculator.dayFactor("EVENT", null, bd("0.4"), bd("1.2"))));
        assertEquals(0, BigDecimal.ONE.compareTo(ForecastCalculator.dayFactor("EVENT", bd("0"), bd("0.4"), bd("1.2"))));
    }

    @Test
    void dayFactor_workday_takesWorkdayFactor()
    {
        assertEquals(0, bd("1.2").compareTo(ForecastCalculator.dayFactor("WORKDAY", null, bd("0.4"), bd("1.2"))));
    }

    @Test
    void forecastVolume_roundsHalfUp()
    {
        // 基线 12.4 × 1.5 = 18.6 → 19
        assertEquals(19, ForecastCalculator.forecastVolume(12.4, bd("1.5")));
        // 基线 0 → 0（无数据时段不预测）
        assertEquals(0, ForecastCalculator.forecastVolume(0, bd("1.5")));
    }

    @Test
    void agents_erlangDivision()
    {
        // 60 通 × 300s AHT = 5 Erlang / 0.85 = 5.88 → ceil 6
        assertEquals(6, ForecastCalculator.agents(60, 300, 0.85));
        // 1 通也有 1 人
        assertEquals(1, ForecastCalculator.agents(1, 300, 0.85));
        // 无话量不派坐席
        assertEquals(0, ForecastCalculator.agents(0, 300, 0.85));
        // 非法利用率保守返回 0（调用方应保证配置合法）
        assertEquals(0, ForecastCalculator.agents(60, 300, 0));
    }

    private BigDecimal bd(String v)
    {
        return new BigDecimal(v);
    }
}
