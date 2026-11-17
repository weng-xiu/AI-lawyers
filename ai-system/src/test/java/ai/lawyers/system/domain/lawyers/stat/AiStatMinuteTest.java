package ai.lawyers.system.domain.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 分钟级物化统计实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiStatMinuteTest
{
    @Test
    void 属性读写()
    {
        Date statTime = new Date();
        Date createTime = new Date();
        AiStatMinute stat = new AiStatMinute();
        stat.setStatId(1L);
        stat.setStatTime(statTime);
        stat.setDimension("ALL");
        stat.setMetricKey("call.connected.count");
        stat.setMetricValue(new BigDecimal("128"));
        stat.setCreateTime(createTime);

        assertEquals(1L, stat.getStatId());
        assertEquals(statTime, stat.getStatTime());
        assertEquals("ALL", stat.getDimension());
        assertEquals("call.connected.count", stat.getMetricKey());
        assertEquals(new BigDecimal("128"), stat.getMetricValue());
        assertEquals(createTime, stat.getCreateTime());
    }

    @Test
    void 默认值为空()
    {
        AiStatMinute stat = new AiStatMinute();

        assertNull(stat.getStatId());
        assertNull(stat.getStatTime());
        assertNull(stat.getDimension());
        assertNull(stat.getMetricKey());
        assertNull(stat.getMetricValue());
        assertNull(stat.getCreateTime());
    }
}
