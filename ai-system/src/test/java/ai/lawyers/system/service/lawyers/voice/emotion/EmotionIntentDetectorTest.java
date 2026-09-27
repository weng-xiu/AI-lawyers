package ai.lawyers.system.service.lawyers.voice.emotion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link EmotionIntentDetector} 词库识别测试（P3-E4）。
 *
 * @author ai-lawyers
 */
class EmotionIntentDetectorTest
{
    private final EmotionIntentDetector detector = new EmotionIntentDetector();

    @Test
    void urgentWord_emotionUrgent_intentForcedUrgent()
    {
        EmotionIntentResult r = detector.analyze("我真的不想活了，活着没意思");
        assertThat(r.isUrgent()).isTrue();
        assertThat(r.getIntent()).isEqualTo(EmotionIntentDetector.INTENT_URGENT);
        assertThat(r.getEmotionKeywords()).contains("不想活");
    }

    @Test
    void complaintWord_emotionNegative_intentComplaint()
    {
        // "投诉" 同时是 negative 情绪词与 COMPLAINT 意图词：情绪 negative，意图 COMPLAINT
        EmotionIntentResult r = detector.analyze("我要投诉你们不作为");
        assertThat(r.getEmotion()).isEqualTo(EmotionIntentResult.EMOTION_NEGATIVE);
        assertThat(r.getIntent()).isEqualTo(EmotionIntentDetector.INTENT_COMPLAINT);
        assertThat(r.getEmotionKeywords()).contains("投诉");
    }

    @Test
    void businessOnly_neutralEmotion_withIntent()
    {
        EmotionIntentResult r = detector.analyze("我想申请劳动仲裁，需要什么材料");
        assertThat(r.getEmotion()).isEqualTo(EmotionIntentResult.EMOTION_NEUTRAL);
        assertThat(r.getIntent()).isEqualTo(EmotionIntentDetector.INTENT_ARBITRATION);
        assertThat(r.getIntentKeywords()).contains("劳动仲裁");
    }

    @Test
    void intentPriority_complaintBeatsBusinessLine()
    {
        // 同时命中 COMPLAINT 与 ARBITRATION：COMPLAINT 优先级更高
        EmotionIntentResult r = detector.analyze("我要投诉仲裁委乱收费");
        assertThat(r.getIntent()).isEqualTo(EmotionIntentDetector.INTENT_COMPLAINT);
    }

    @Test
    void noHit_neutralNullIntent()
    {
        EmotionIntentResult r = detector.analyze("请问上班时间是几点");
        assertThat(r.getEmotion()).isEqualTo(EmotionIntentResult.EMOTION_NEUTRAL);
        assertThat(r.getIntent()).isNull();
        assertThat(r.getEmotionKeywords()).isEmpty();
    }

    @Test
    void emptyAndNullText_neutral()
    {
        assertThat(detector.analyze("").getEmotion()).isEqualTo(EmotionIntentResult.EMOTION_NEUTRAL);
        assertThat(detector.analyze("   ").getEmotion()).isEqualTo(EmotionIntentResult.EMOTION_NEUTRAL);
        assertThat(detector.analyze(null).getEmotion()).isEqualTo(EmotionIntentResult.EMOTION_NEUTRAL);
    }

    @Test
    void multipleHits_keywordsDeduped()
    {
        EmotionIntentResult r = detector.analyze("骗子，都是骗子，我要投诉你们骗人");
        assertThat(r.getEmotion()).isEqualTo(EmotionIntentResult.EMOTION_NEGATIVE);
        // 同一关键词不重复出现
        assertThat(r.getEmotionKeywords()).doesNotHaveDuplicates().contains("骗子", "投诉");
    }
}
