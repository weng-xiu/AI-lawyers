package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 线路质量统计实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiTrunkMetricTest
{
    @Test
    void 属性读写()
    {
        Date statTime = new Date();
        AiTrunkMetric metric = new AiTrunkMetric();
        metric.setMetricId(1L);
        metric.setTrunkId(2L);
        metric.setTrunkCode("TRUNK-01");
        metric.setCarrier("CM");
        metric.setStatTime(statTime);
        metric.setStatDimension("1");
        metric.setTotalCalls(100);
        metric.setConnectedCalls(80);
        metric.setFailedCalls(20);
        metric.setConnectRate(new BigDecimal("0.80"));
        metric.setAsr(new BigDecimal("0.90"));
        metric.setAcd(120);
        metric.setTotalDuration(9600L);
        metric.setAvgRingDuration(8);
        metric.setMaxConcurrent(15);
        metric.setAvgMos(new BigDecimal("4.10"));
        metric.setAvgPacketLoss(new BigDecimal("0.30"));

        assertEquals(1L, metric.getMetricId());
        assertEquals(2L, metric.getTrunkId());
        assertEquals("TRUNK-01", metric.getTrunkCode());
        assertEquals("CM", metric.getCarrier());
        assertEquals(statTime, metric.getStatTime());
        assertEquals("1", metric.getStatDimension());
        assertEquals(100, metric.getTotalCalls());
        assertEquals(80, metric.getConnectedCalls());
        assertEquals(20, metric.getFailedCalls());
        assertEquals(new BigDecimal("0.80"), metric.getConnectRate());
        assertEquals(new BigDecimal("0.90"), metric.getAsr());
        assertEquals(120, metric.getAcd());
        assertEquals(9600L, metric.getTotalDuration());
        assertEquals(8, metric.getAvgRingDuration());
        assertEquals(15, metric.getMaxConcurrent());
        assertEquals(new BigDecimal("4.10"), metric.getAvgMos());
        assertEquals(new BigDecimal("0.30"), metric.getAvgPacketLoss());
    }

    @Test
    void 默认值为空()
    {
        AiTrunkMetric metric = new AiTrunkMetric();

        assertNull(metric.getMetricId());
        assertNull(metric.getTrunkCode());
        assertNull(metric.getStatTime());
        assertNull(metric.getTotalCalls());
        assertNull(metric.getConnectRate());
        assertNull(metric.getAvgMos());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiTrunkMetric metric = new AiTrunkMetric();
        metric.setMetricId(1L);
        metric.setTrunkCode("TRUNK-01");
        metric.setTotalCalls(100);
        metric.setConnectRate(new BigDecimal("0.80"));

        String text = metric.toString();

        assertTrue(text.contains("TRUNK-01"));
        assertTrue(text.contains("totalCalls"));
        assertTrue(text.contains("connectRate"));
    }
}
