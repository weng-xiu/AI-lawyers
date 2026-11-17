package ai.lawyers.system.domain.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 报表自助分析查询条件测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AdHocStatQueryTest
{
    @Test
    void 属性读写()
    {
        AdHocStatQuery query = new AdHocStatQuery();
        query.setBeginTime("2026-01-01 00:00:00");
        query.setEndTime("2026-01-02 00:00:00");
        query.setGranularity("hour");
        query.setMetrics(List.of("call.total", "call.connected"));
        query.setDimensions(List.of("hour", "agent"));

        assertEquals("2026-01-01 00:00:00", query.getBeginTime());
        assertEquals("2026-01-02 00:00:00", query.getEndTime());
        assertEquals("hour", query.getGranularity());
        assertEquals(2, query.getMetrics().size());
        assertEquals("call.total", query.getMetrics().get(0));
        assertEquals(2, query.getDimensions().size());
        assertEquals("agent", query.getDimensions().get(1));
    }

    @Test
    void 默认值为空()
    {
        AdHocStatQuery query = new AdHocStatQuery();

        assertNull(query.getBeginTime());
        assertNull(query.getEndTime());
        assertNull(query.getGranularity());
        assertNull(query.getMetrics());
        assertNull(query.getDimensions());
    }
}
