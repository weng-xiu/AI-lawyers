package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 视频咨询实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiVideoConsultTest
{
    @Test
    void 属性读写()
    {
        Date startTime = new Date();
        Date endTime = new Date();
        AiVideoConsult consult = new AiVideoConsult();
        consult.setConsultId(1L);
        consult.setConsultNo("VC20261118001");
        consult.setCustomerName("客户丁");
        consult.setCustomerPhone("13400000000");
        consult.setAgentId(2L);
        consult.setAgentName("坐席戊");
        consult.setRoomNo("ROOM-1001");
        consult.setStartTime(startTime);
        consult.setEndTime(endTime);
        consult.setDuration(600);
        consult.setStatus("2");
        consult.setSatisfaction(5);
        consult.setRecordingUrl("/upload/video/a.mp4");
        consult.setIdentityVerify("1");

        assertEquals(1L, consult.getConsultId());
        assertEquals("VC20261118001", consult.getConsultNo());
        assertEquals("客户丁", consult.getCustomerName());
        assertEquals("13400000000", consult.getCustomerPhone());
        assertEquals(2L, consult.getAgentId());
        assertEquals("坐席戊", consult.getAgentName());
        assertEquals("ROOM-1001", consult.getRoomNo());
        assertEquals(startTime, consult.getStartTime());
        assertEquals(endTime, consult.getEndTime());
        assertEquals(600, consult.getDuration());
        assertEquals("2", consult.getStatus());
        assertEquals(5, consult.getSatisfaction());
        assertEquals("/upload/video/a.mp4", consult.getRecordingUrl());
        assertEquals("1", consult.getIdentityVerify());
    }

    @Test
    void 默认值为空()
    {
        AiVideoConsult consult = new AiVideoConsult();

        assertNull(consult.getConsultId());
        assertNull(consult.getConsultNo());
        assertNull(consult.getCustomerName());
        assertNull(consult.getDuration());
        assertNull(consult.getStatus());
        assertNull(consult.getRecordingUrl());
    }

    @Test
    void 字符串输出包含咨询编号与坐席()
    {
        AiVideoConsult consult = new AiVideoConsult();
        consult.setConsultNo("VC20261118002");
        consult.setAgentName("坐席己");

        String text = consult.toString();
        assertTrue(text.contains("VC20261118002"));
        assertTrue(text.contains("坐席己"));
    }
}
