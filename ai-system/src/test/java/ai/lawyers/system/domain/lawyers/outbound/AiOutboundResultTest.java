package ai.lawyers.system.domain.lawyers.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 外呼结果实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiOutboundResultTest
{
    @Test
    void 属性读写()
    {
        Date startTime = new Date();
        Date endTime = new Date();
        Date callbackTime = new Date();
        AiOutboundResult result = new AiOutboundResult();
        result.setResultId(1L);
        result.setTaskId(2L);
        result.setCalleeId(3L);
        result.setCalleeNumber("13800000000");
        result.setCalleeName("张三");
        result.setRecordId(4L);
        result.setCallResult("ANSWERED");
        result.setCallDuration(120);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setAgentId(5L);
        result.setAgentName("李四");
        result.setIntentionCode("CONSULT");
        result.setIntentionName("法律咨询");
        result.setKeywords("合同,违约");
        result.setSatisfaction("5");
        result.setNeedCallback("1");
        result.setCallbackTime(callbackTime);
        result.setTranscript("客户咨询合同违约问题");
        result.setSummary("需要回访");
        result.setRecordingUrl("https://record.example.com/1.mp3");
        result.setFlowData("{\"step\":3}");

        assertEquals(1L, result.getResultId());
        assertEquals(2L, result.getTaskId());
        assertEquals(3L, result.getCalleeId());
        assertEquals("13800000000", result.getCalleeNumber());
        assertEquals("张三", result.getCalleeName());
        assertEquals(4L, result.getRecordId());
        assertEquals("ANSWERED", result.getCallResult());
        assertEquals(120, result.getCallDuration());
        assertEquals(startTime, result.getStartTime());
        assertEquals(endTime, result.getEndTime());
        assertEquals(5L, result.getAgentId());
        assertEquals("李四", result.getAgentName());
        assertEquals("CONSULT", result.getIntentionCode());
        assertEquals("法律咨询", result.getIntentionName());
        assertEquals("合同,违约", result.getKeywords());
        assertEquals("5", result.getSatisfaction());
        assertEquals("1", result.getNeedCallback());
        assertEquals(callbackTime, result.getCallbackTime());
        assertEquals("客户咨询合同违约问题", result.getTranscript());
        assertEquals("需要回访", result.getSummary());
        assertEquals("https://record.example.com/1.mp3", result.getRecordingUrl());
        assertEquals("{\"step\":3}", result.getFlowData());
    }

    @Test
    void 默认值为空()
    {
        AiOutboundResult result = new AiOutboundResult();

        assertNull(result.getResultId());
        assertNull(result.getCallResult());
        assertNull(result.getStartTime());
        assertNull(result.getEndTime());
        assertNull(result.getTranscript());
        assertNull(result.getFlowData());
    }
}
