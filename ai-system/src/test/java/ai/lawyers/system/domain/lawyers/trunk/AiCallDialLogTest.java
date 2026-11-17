package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 拨号明细日志实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallDialLogTest
{
    @Test
    void 属性读写()
    {
        Date dialTime = new Date();
        AiCallDialLog log = new AiCallDialLog();
        log.setLogId(1L);
        log.setCallUuid("uuid-001");
        log.setRecordId(2L);
        log.setTaskId(3L);
        log.setCalleeId(4L);
        log.setAgentId(5L);
        log.setCallerNumber("01000000000");
        log.setCalleeNumber("13800000000");
        log.setCalleeCarrier("CM");
        log.setCalleeProvince("北京");
        log.setCalleeCity("北京");
        log.setTrunkId(6L);
        log.setTrunkCode("TRUNK-01");
        log.setTrunkCarrier("CM");
        log.setLineType("SIP");
        log.setRouteStrategy("QUALITY");
        log.setFailoverCount(1);
        log.setFailoverTrunks("TRUNK-01,TRUNK-02");
        log.setQueueWaitMs(1200L);
        log.setDialStatus("3");
        log.setHangupCause("NORMAL_CLEARING");
        log.setSipCode(200);
        log.setFailReason("无");
        log.setDialTime(dialTime);
        log.setRingTime(dialTime);
        log.setAnswerTime(dialTime);
        log.setHangupTime(dialTime);
        log.setRingDuration(8);
        log.setTalkDuration(120);
        log.setTotalDuration(130);
        log.setBillDuration(120);
        log.setIsConnected("1");
        log.setMos(new BigDecimal("4.20"));
        log.setPacketLoss(new BigDecimal("0.50"));
        log.setJitter(10);
        log.setRtt(80);
        log.setRecordFile("https://record.example.com/1.mp3");
        log.setBeginTime("2026-01-05 08:00:00");
        log.setEndTimeQuery("2026-01-05 09:00:00");

        assertEquals(1L, log.getLogId());
        assertEquals("uuid-001", log.getCallUuid());
        assertEquals(2L, log.getRecordId());
        assertEquals(3L, log.getTaskId());
        assertEquals(4L, log.getCalleeId());
        assertEquals(5L, log.getAgentId());
        assertEquals("01000000000", log.getCallerNumber());
        assertEquals("13800000000", log.getCalleeNumber());
        assertEquals("CM", log.getCalleeCarrier());
        assertEquals("北京", log.getCalleeProvince());
        assertEquals("北京", log.getCalleeCity());
        assertEquals(6L, log.getTrunkId());
        assertEquals("TRUNK-01", log.getTrunkCode());
        assertEquals("CM", log.getTrunkCarrier());
        assertEquals("SIP", log.getLineType());
        assertEquals("QUALITY", log.getRouteStrategy());
        assertEquals(1, log.getFailoverCount());
        assertEquals("TRUNK-01,TRUNK-02", log.getFailoverTrunks());
        assertEquals(1200L, log.getQueueWaitMs());
        assertEquals("3", log.getDialStatus());
        assertEquals("NORMAL_CLEARING", log.getHangupCause());
        assertEquals(200, log.getSipCode());
        assertEquals("无", log.getFailReason());
        assertEquals(dialTime, log.getDialTime());
        assertEquals(dialTime, log.getRingTime());
        assertEquals(dialTime, log.getAnswerTime());
        assertEquals(dialTime, log.getHangupTime());
        assertEquals(8, log.getRingDuration());
        assertEquals(120, log.getTalkDuration());
        assertEquals(130, log.getTotalDuration());
        assertEquals(120, log.getBillDuration());
        assertEquals("1", log.getIsConnected());
        assertEquals(new BigDecimal("4.20"), log.getMos());
        assertEquals(new BigDecimal("0.50"), log.getPacketLoss());
        assertEquals(10, log.getJitter());
        assertEquals(80, log.getRtt());
        assertEquals("https://record.example.com/1.mp3", log.getRecordFile());
        assertEquals("2026-01-05 08:00:00", log.getBeginTime());
        assertEquals("2026-01-05 09:00:00", log.getEndTimeQuery());
    }

    @Test
    void 默认值为空()
    {
        AiCallDialLog log = new AiCallDialLog();

        assertNull(log.getLogId());
        assertNull(log.getCallUuid());
        assertNull(log.getCalleeNumber());
        assertNull(log.getDialStatus());
        assertNull(log.getDialTime());
        assertNull(log.getMos());
        assertNull(log.getIsConnected());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiCallDialLog log = new AiCallDialLog();
        log.setLogId(1L);
        log.setCallUuid("uuid-001");
        log.setCalleeNumber("13800000000");
        log.setDialStatus("3");
        log.setIsConnected("1");

        String text = log.toString();

        assertTrue(text.contains("uuid-001"));
        assertTrue(text.contains("13800000000"));
        assertTrue(text.contains("dialStatus"));
    }
}
