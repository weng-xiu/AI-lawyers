package ai.lawyers.system.domain.lawyers.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 排班计划确认快照实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiForecastPlanTest
{
    @Test
    void 属性读写()
    {
        Date beginDate = new Date();
        Date endDate = new Date();
        Date confirmedTime = new Date();
        Date createTime = new Date();
        AiForecastPlan plan = new AiForecastPlan();
        plan.setPlanId(1L);
        plan.setBeginDate(beginDate);
        plan.setEndDate(endDate);
        plan.setTotalVolume(5000);
        plan.setPeakHour("10:00");
        plan.setPeakAgents(20);
        plan.setPayloadJson("{\"days\":[]}");
        plan.setConfirmedBy("班组长甲");
        plan.setConfirmedTime(confirmedTime);
        plan.setRemark("节假日排班");
        plan.setCreateTime(createTime);

        assertEquals(1L, plan.getPlanId());
        assertEquals(beginDate, plan.getBeginDate());
        assertEquals(endDate, plan.getEndDate());
        assertEquals(5000, plan.getTotalVolume());
        assertEquals("10:00", plan.getPeakHour());
        assertEquals(20, plan.getPeakAgents());
        assertEquals("{\"days\":[]}", plan.getPayloadJson());
        assertEquals("班组长甲", plan.getConfirmedBy());
        assertEquals(confirmedTime, plan.getConfirmedTime());
        assertEquals("节假日排班", plan.getRemark());
        assertEquals(createTime, plan.getCreateTime());
    }

    @Test
    void 默认值为空()
    {
        AiForecastPlan plan = new AiForecastPlan();

        assertNull(plan.getPlanId());
        assertNull(plan.getBeginDate());
        assertNull(plan.getTotalVolume());
        assertNull(plan.getPeakHour());
        assertNull(plan.getPayloadJson());
        assertNull(plan.getConfirmedTime());
    }
}
