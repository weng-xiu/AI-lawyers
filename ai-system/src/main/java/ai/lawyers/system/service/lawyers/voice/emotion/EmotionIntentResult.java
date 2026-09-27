package ai.lawyers.system.service.lawyers.voice.emotion;

import java.util.Collections;
import java.util.List;

/**
 * 情绪/意图识别结果（P3-E4）。
 *
 * <p>纯规则词库产出，无置信度浮点计算：命中词即结论。误报由班长在风险预警页
 * 置"已忽略"（ai_risk_warning.status=3）；紧急口径宁错报不遗漏。</p>
 *
 * @author ai-lawyers
 */
public class EmotionIntentResult
{
    /** 情绪：紧急危机（高危，正在/可能发生的严重侵害或自伤） */
    public static final String EMOTION_URGENT = "urgent";

    /** 情绪：负面（不满、投诉、愤怒、悲伤） */
    public static final String EMOTION_NEGATIVE = "negative";

    /** 情绪：平和 */
    public static final String EMOTION_NEUTRAL = "neutral";

    private final String emotion;

    private final String intent;

    private final List<String> emotionKeywords;

    private final List<String> intentKeywords;

    public EmotionIntentResult(String emotion, String intent,
            List<String> emotionKeywords, List<String> intentKeywords)
    {
        this.emotion = emotion;
        this.intent = intent;
        this.emotionKeywords = emotionKeywords == null ? Collections.emptyList() : emotionKeywords;
        this.intentKeywords = intentKeywords == null ? Collections.emptyList() : intentKeywords;
    }

    /** 文本无任何命中时的平和结果 */
    public static EmotionIntentResult neutral()
    {
        return new EmotionIntentResult(EMOTION_NEUTRAL, null,
                Collections.emptyList(), Collections.emptyList());
    }

    public String getEmotion()
    {
        return emotion;
    }

    /**
     * 业务意图（取优先级最高的一个）：
     * URGENT 紧急危机 / COMPLAINT 投诉举报 / LEGAL_AID 法律援助 / MEDIATION 人民调解 /
     * NOTARY 公证 / FORENSIC 司法鉴定 / ARBITRATION 仲裁；未命中返回 null。
     */
    public String getIntent()
    {
        return intent;
    }

    public List<String> getEmotionKeywords()
    {
        return emotionKeywords;
    }

    public List<String> getIntentKeywords()
    {
        return intentKeywords;
    }

    public boolean isUrgent()
    {
        return EMOTION_URGENT.equals(emotion);
    }

    public boolean isNegative()
    {
        return EMOTION_NEGATIVE.equals(emotion);
    }
}
