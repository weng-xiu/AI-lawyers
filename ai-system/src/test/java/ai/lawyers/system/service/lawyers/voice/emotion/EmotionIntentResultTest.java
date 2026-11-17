package ai.lawyers.system.service.lawyers.voice.emotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * 情绪/意图识别结果测试：空值兜底与情绪判定。
 *
 * @author ai-lawyers
 */
class EmotionIntentResultTest
{
    @Test
    void neutral_返回平和结果且无命中词()
    {
        EmotionIntentResult result = EmotionIntentResult.neutral();

        assertEquals(EmotionIntentResult.EMOTION_NEUTRAL, result.getEmotion());
        assertNull(result.getIntent());
        assertTrue(result.getEmotionKeywords().isEmpty());
        assertTrue(result.getIntentKeywords().isEmpty());
        assertFalse(result.isUrgent());
        assertFalse(result.isNegative());
    }

    @Test
    void 构造_关键词为空时给出空列表()
    {
        EmotionIntentResult result = new EmotionIntentResult(EmotionIntentResult.EMOTION_NEGATIVE,
                "COMPLAINT", null, null);

        assertEquals(EmotionIntentResult.EMOTION_NEGATIVE, result.getEmotion());
        assertEquals("COMPLAINT", result.getIntent());
        assertTrue(result.getEmotionKeywords().isEmpty());
        assertTrue(result.getIntentKeywords().isEmpty());
    }

    @Test
    void isUrgent_仅紧急危机为真()
    {
        EmotionIntentResult result = new EmotionIntentResult(EmotionIntentResult.EMOTION_URGENT,
                "URGENT", Arrays.asList("救命", "被打"), Arrays.asList("人身安全"));

        assertTrue(result.isUrgent());
        assertFalse(result.isNegative());
        assertEquals("URGENT", result.getIntent());
    }

    @Test
    void isNegative_负面情绪命中并保留关键词()
    {
        EmotionIntentResult result = new EmotionIntentResult(EmotionIntentResult.EMOTION_NEGATIVE,
                "COMPLAINT", Arrays.asList("投诉", "太差"), Arrays.asList("举报"));

        assertTrue(result.isNegative());
        assertFalse(result.isUrgent());
        assertEquals(Arrays.asList("投诉", "太差"), result.getEmotionKeywords());
        assertEquals(Arrays.asList("举报"), result.getIntentKeywords());
    }

    @Test
    void 情绪常量_取值互不相同()
    {
        assertFalse(EmotionIntentResult.EMOTION_URGENT.equals(EmotionIntentResult.EMOTION_NEGATIVE));
        assertFalse(EmotionIntentResult.EMOTION_NEGATIVE.equals(EmotionIntentResult.EMOTION_NEUTRAL));
    }
}
