package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 号段-运营商路由实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiNumberSegmentTest
{
    @Test
    void 属性读写()
    {
        AiNumberSegment segment = new AiNumberSegment();
        segment.setSegmentId(1L);
        segment.setSegmentPrefix("138");
        segment.setCarrier("CM");
        segment.setNumberType("1");
        segment.setProvince("北京");
        segment.setCity("北京");
        segment.setMatchLength(3);
        segment.setEnableFlag("1");

        assertEquals(1L, segment.getSegmentId());
        assertEquals("138", segment.getSegmentPrefix());
        assertEquals("CM", segment.getCarrier());
        assertEquals("1", segment.getNumberType());
        assertEquals("北京", segment.getProvince());
        assertEquals("北京", segment.getCity());
        assertEquals(3, segment.getMatchLength());
        assertEquals("1", segment.getEnableFlag());
    }

    @Test
    void 默认值为空()
    {
        AiNumberSegment segment = new AiNumberSegment();

        assertNull(segment.getSegmentId());
        assertNull(segment.getSegmentPrefix());
        assertNull(segment.getCarrier());
        assertNull(segment.getMatchLength());
        assertNull(segment.getEnableFlag());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiNumberSegment segment = new AiNumberSegment();
        segment.setSegmentId(1L);
        segment.setSegmentPrefix("138");
        segment.setCarrier("CM");
        segment.setCity("北京");

        String text = segment.toString();

        assertTrue(text.contains("138"));
        assertTrue(text.contains("CM"));
        assertTrue(text.contains("segmentPrefix"));
    }
}
