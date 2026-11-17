package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 高频置底命中处置日志实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiHotspotSuppressLogTest
{
    @Test
    void 属性读写()
    {
        Date hitTime = new Date();
        AiHotspotSuppressLog log = new AiHotspotSuppressLog();
        log.setLogId(1L);
        log.setSuppressId(2L);
        log.setRuleName("高频骚扰置底");
        log.setDirection("INBOUND");
        log.setMatchType("NUMBER");
        log.setMatchValue("13800000000");
        log.setCallerNumber("13800000000");
        log.setCalleeNumber("01000000000");
        log.setAction("PRIORITY");
        log.setPriority(10);
        log.setChannelUuid("uuid-001");
        log.setHitTime(hitTime);

        assertEquals(1L, log.getLogId());
        assertEquals(2L, log.getSuppressId());
        assertEquals("高频骚扰置底", log.getRuleName());
        assertEquals("INBOUND", log.getDirection());
        assertEquals("NUMBER", log.getMatchType());
        assertEquals("13800000000", log.getMatchValue());
        assertEquals("13800000000", log.getCallerNumber());
        assertEquals("01000000000", log.getCalleeNumber());
        assertEquals("PRIORITY", log.getAction());
        assertEquals(10, log.getPriority());
        assertEquals("uuid-001", log.getChannelUuid());
        assertEquals(hitTime, log.getHitTime());
    }

    @Test
    void 默认值为空()
    {
        AiHotspotSuppressLog log = new AiHotspotSuppressLog();

        assertNull(log.getLogId());
        assertNull(log.getSuppressId());
        assertNull(log.getDirection());
        assertNull(log.getAction());
        assertNull(log.getPriority());
        assertNull(log.getHitTime());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiHotspotSuppressLog log = new AiHotspotSuppressLog();
        log.setLogId(1L);
        log.setRuleName("高频骚扰置底");
        log.setCallerNumber("13800000000");
        log.setAction("REJECT");

        String text = log.toString();

        assertTrue(text.contains("高频骚扰置底"));
        assertTrue(text.contains("13800000000"));
        assertTrue(text.contains("action"));
    }
}
