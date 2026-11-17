package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 通话记录实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallRecordTest
{
    @Test
    void 属性读写()
    {
        Date callTime = new Date();
        Date endTime = new Date();
        Date aiSummaryTime = new Date();
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1L);
        record.setCallerNumber("13800000000");
        record.setCallerNumberIndex("index-001");
        record.setCallerName("张三");
        record.setCallerAddress("北京市朝阳区");
        record.setCallTime(callTime);
        record.setEndTime(endTime);
        record.setDuration(120);
        record.setAgentId(2L);
        record.setCategoryId(3L);
        record.setContent("咨询劳动合同解除");
        record.setAnswer("已告知补偿标准");
        record.setStatus("1");
        record.setTransferId(4L);
        record.setTicketId(5L);
        record.setCallUuid("uuid-001");
        record.setRecordFile("/record/1.wav");
        record.setRecordingUrl("https://record.example.com/1.mp3");
        record.setRecordDuration(120);
        record.setAsrStatus(2);
        record.setTranscript("客户咨询劳动合同解除");
        record.setAiSummary("案情摘要：违法解除");
        record.setAiSummaryStatus("2");
        record.setAiSummaryTime(aiSummaryTime);
        record.setAiSummaryFailReason("无");
        record.setAgentName("李四");
        record.setCategoryName("劳动争议");
        record.setTokenGroups(List.of(List.of("劳动", "合同")));

        assertEquals(1L, record.getRecordId());
        assertEquals("13800000000", record.getCallerNumber());
        assertEquals("index-001", record.getCallerNumberIndex());
        assertEquals("张三", record.getCallerName());
        assertEquals("北京市朝阳区", record.getCallerAddress());
        assertEquals(callTime, record.getCallTime());
        assertEquals(endTime, record.getEndTime());
        assertEquals(120, record.getDuration());
        assertEquals(2L, record.getAgentId());
        assertEquals(3L, record.getCategoryId());
        assertEquals("咨询劳动合同解除", record.getContent());
        assertEquals("已告知补偿标准", record.getAnswer());
        assertEquals("1", record.getStatus());
        assertEquals(4L, record.getTransferId());
        assertEquals(5L, record.getTicketId());
        assertEquals("uuid-001", record.getCallUuid());
        assertEquals("/record/1.wav", record.getRecordFile());
        assertEquals("https://record.example.com/1.mp3", record.getRecordingUrl());
        assertEquals(120, record.getRecordDuration());
        assertEquals(2, record.getAsrStatus());
        assertEquals("客户咨询劳动合同解除", record.getTranscript());
        assertEquals("案情摘要：违法解除", record.getAiSummary());
        assertEquals("2", record.getAiSummaryStatus());
        assertEquals(aiSummaryTime, record.getAiSummaryTime());
        assertEquals("无", record.getAiSummaryFailReason());
        assertEquals("李四", record.getAgentName());
        assertEquals("劳动争议", record.getCategoryName());
        assertEquals(2, record.getTokenGroups().get(0).size());
        assertEquals("劳动", record.getTokenGroups().get(0).get(0));
    }

    @Test
    void 默认值为空()
    {
        AiCallRecord record = new AiCallRecord();

        assertNull(record.getRecordId());
        assertNull(record.getCallerNumber());
        assertNull(record.getCallTime());
        assertNull(record.getStatus());
        assertNull(record.getAsrStatus());
        assertNull(record.getAiSummaryStatus());
        assertNull(record.getTokenGroups());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1L);
        record.setCallerNumber("13800000000");
        record.setCallerName("张三");
        record.setStatus("1");

        String text = record.toString();

        assertTrue(text.contains("13800000000"));
        assertTrue(text.contains("张三"));
        assertTrue(text.contains("callerNumber"));
    }
}
