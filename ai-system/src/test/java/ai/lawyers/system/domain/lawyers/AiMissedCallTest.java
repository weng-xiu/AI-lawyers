package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 未接来电实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiMissedCallTest
{
    @Test
    void 属性读写()
    {
        Date callTime = new Date();
        Date callbackTime = new Date(callTime.getTime() + 600000L);
        AiMissedCall missedCall = new AiMissedCall();
        missedCall.setMissedCallId(1L);
        missedCall.setCallerNumber("13500000000");
        missedCall.setCallerName("王五");
        missedCall.setCallTime(callTime);
        missedCall.setStatus("1");
        missedCall.setCallbackTime(callbackTime);
        missedCall.setCallbackBy("客服01");
        missedCall.setRecordId(20L);
        missedCall.setVoiceContent("我想咨询离婚财产分割");
        missedCall.setVoiceDuration(32);
        missedCall.setVoiceFileUrl("/record/voice/1.wav");
        missedCall.setNoticeStatus("1");
        missedCall.setNoticeChannel("1");

        assertEquals(1L, missedCall.getMissedCallId());
        assertEquals("13500000000", missedCall.getCallerNumber());
        assertEquals("王五", missedCall.getCallerName());
        assertEquals(callTime, missedCall.getCallTime());
        assertEquals("1", missedCall.getStatus());
        assertEquals(callbackTime, missedCall.getCallbackTime());
        assertEquals("客服01", missedCall.getCallbackBy());
        assertEquals(20L, missedCall.getRecordId());
        assertEquals("我想咨询离婚财产分割", missedCall.getVoiceContent());
        assertEquals(32, missedCall.getVoiceDuration());
        assertEquals("/record/voice/1.wav", missedCall.getVoiceFileUrl());
        assertEquals("1", missedCall.getNoticeStatus());
        assertEquals("1", missedCall.getNoticeChannel());
    }

    @Test
    void 默认值为空()
    {
        AiMissedCall missedCall = new AiMissedCall();

        assertNull(missedCall.getMissedCallId());
        assertNull(missedCall.getCallerNumber());
        assertNull(missedCall.getCallerName());
        assertNull(missedCall.getCallTime());
        assertNull(missedCall.getStatus());
        assertNull(missedCall.getCallbackTime());
        assertNull(missedCall.getCallbackBy());
        assertNull(missedCall.getVoiceContent());
        assertNull(missedCall.getVoiceDuration());
        assertNull(missedCall.getNoticeStatus());
        assertNull(missedCall.getNoticeChannel());
    }

    @Test
    void 字符串输出包含来电号码与状态()
    {
        AiMissedCall missedCall = new AiMissedCall();
        missedCall.setCallerNumber("13300000000");
        missedCall.setStatus("0");

        String text = missedCall.toString();
        assertTrue(text.contains("13300000000"));
        assertTrue(text.contains("callerNumber"));
    }
}
