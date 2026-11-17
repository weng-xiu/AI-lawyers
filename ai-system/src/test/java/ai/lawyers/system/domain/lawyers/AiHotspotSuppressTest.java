package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 高频置底规则实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiHotspotSuppressTest
{
    @Test
    void 属性读写()
    {
        Date effectiveStart = new Date();
        Date effectiveEnd = new Date();
        AiHotspotSuppress suppress = new AiHotspotSuppress();
        suppress.setSuppressId(1L);
        suppress.setRuleName("骚扰号码置底");
        suppress.setMatchType("PHONE");
        suppress.setMatchValue("10086");
        suppress.setAction("PRIORITY");
        suppress.setPriorityLevel(-10);
        suppress.setTriggerCount(3);
        suppress.setWindowSeconds(60);
        suppress.setHitCount(20);
        suppress.setStatus("0");
        suppress.setEffectiveStart(effectiveStart);
        suppress.setEffectiveEnd(effectiveEnd);

        assertEquals(1L, suppress.getSuppressId());
        assertEquals("骚扰号码置底", suppress.getRuleName());
        assertEquals("PHONE", suppress.getMatchType());
        assertEquals("10086", suppress.getMatchValue());
        assertEquals("PRIORITY", suppress.getAction());
        assertEquals(-10, suppress.getPriorityLevel());
        assertEquals(3, suppress.getTriggerCount());
        assertEquals(60, suppress.getWindowSeconds());
        assertEquals(20, suppress.getHitCount());
        assertEquals("0", suppress.getStatus());
        assertEquals(effectiveStart, suppress.getEffectiveStart());
        assertEquals(effectiveEnd, suppress.getEffectiveEnd());
    }

    @Test
    void 默认值为空()
    {
        AiHotspotSuppress suppress = new AiHotspotSuppress();

        assertNull(suppress.getSuppressId());
        assertNull(suppress.getRuleName());
        assertNull(suppress.getMatchType());
        assertNull(suppress.getPriorityLevel());
        assertNull(suppress.getHitCount());
        assertNull(suppress.getEffectiveStart());
    }

    @Test
    void 字符串输出包含规则名称与匹配值()
    {
        AiHotspotSuppress suppress = new AiHotspotSuppress();
        suppress.setRuleName("关键词拦截");
        suppress.setMatchValue("贷款推销");

        String text = suppress.toString();
        assertTrue(text.contains("关键词拦截"));
        assertTrue(text.contains("贷款推销"));
    }
}
