package ai.lawyers.system.domain.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 坐席状态变更流水实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiAgentStatusLogTest
{
    @Test
    void 属性读写()
    {
        Date logTime = new Date();
        AiAgentStatusLog log = new AiAgentStatusLog();
        log.setLogId(1L);
        log.setAgentId(2L);
        log.setUserId(3L);
        log.setEventType("STATUS");
        log.setFromStatus("1");
        log.setToStatus("2");
        log.setFromCallStatus("0");
        log.setToCallStatus("1");
        log.setDuration(120);
        log.setRecordId(4L);
        log.setLogTime(logTime);

        assertEquals(1L, log.getLogId());
        assertEquals(2L, log.getAgentId());
        assertEquals(3L, log.getUserId());
        assertEquals("STATUS", log.getEventType());
        assertEquals("1", log.getFromStatus());
        assertEquals("2", log.getToStatus());
        assertEquals("0", log.getFromCallStatus());
        assertEquals("1", log.getToCallStatus());
        assertEquals(120, log.getDuration());
        assertEquals(4L, log.getRecordId());
        assertEquals(logTime, log.getLogTime());
    }

    @Test
    void 默认值为空()
    {
        AiAgentStatusLog log = new AiAgentStatusLog();

        assertNull(log.getLogId());
        assertNull(log.getAgentId());
        assertNull(log.getEventType());
        assertNull(log.getFromStatus());
        assertNull(log.getToStatus());
        assertNull(log.getDuration());
        assertNull(log.getLogTime());
    }
}
