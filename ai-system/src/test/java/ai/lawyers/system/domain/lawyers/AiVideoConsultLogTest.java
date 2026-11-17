package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 视频咨询事件日志实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiVideoConsultLogTest
{
    @Test
    void 属性读写()
    {
        Date eventTime = new Date();
        AiVideoConsultLog log = new AiVideoConsultLog();
        log.setLogId(1L);
        log.setConsultId(2L);
        log.setEventType("JOIN");
        log.setEventContent("客户已进入视频房间");
        log.setEventTime(eventTime);

        assertEquals(1L, log.getLogId());
        assertEquals(2L, log.getConsultId());
        assertEquals("JOIN", log.getEventType());
        assertEquals("客户已进入视频房间", log.getEventContent());
        assertEquals(eventTime, log.getEventTime());
    }

    @Test
    void 默认值为空()
    {
        AiVideoConsultLog log = new AiVideoConsultLog();

        assertNull(log.getLogId());
        assertNull(log.getConsultId());
        assertNull(log.getEventType());
        assertNull(log.getEventContent());
        assertNull(log.getEventTime());
    }

    @Test
    void 字符串输出包含事件类型与内容()
    {
        AiVideoConsultLog log = new AiVideoConsultLog();
        log.setEventType("LEAVE");
        log.setEventContent("咨询已结束");

        String text = log.toString();
        assertTrue(text.contains("LEAVE"));
        assertTrue(text.contains("咨询已结束"));
    }
}
