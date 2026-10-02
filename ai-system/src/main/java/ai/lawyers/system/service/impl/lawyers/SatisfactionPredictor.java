package ai.lawyers.system.service.impl.lawyers;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 预测性满意度轻量加权模型（P1-11，纯逻辑无依赖便于单测）。
 *
 * <p>量表 1非常满意 / 2满意 / 3一般 / 4不满意。基线 1.0 分起步，按可解释特征
 * 加罚分，映射回 1~4 整数。特征（权重配置化，见 application.yml satisfaction.forecast）：</p>
 * <ul>
 *   <li>转写情绪：复用 {@code EmotionIntentDetector} 纯规则词库——urgent 加急重罚分，
 *       negative 中罚分；</li>
 *   <li>质检情绪态度 emotionAttitude（0~100，低分罚分）；</li>
 *   <li>质检总分 totalScore/adjustedScore（0~100，低分罚分）；</li>
 *   <li>超短通话（≤short-seconds，疑似未解决）；</li>
 *   <li>未接来电（重罚）；</li>
 *   <li>转接（多跳可能积怨）。</li>
 * </ul>
 *
 * <p>非训练模型：权重手工可调，预测结果与真实回访问卷保留对照口径
 * （一致率/±1 容差），用于持续校准权重，不替代回访。</p>
 *
 * @author ai-lawyers
 */
public final class SatisfactionPredictor
{
    /** 量表下限（非常满意） */
    public static final int SCALE_MIN = 1;
    /** 量表上限（不满意） */
    public static final int SCALE_MAX = 4;

    private SatisfactionPredictor()
    {
    }

    /**
     * 加权评分（1.0 基线 + 特征罚分，未截断）。
     *
     * @param emotion   转写情绪 URGENT/NEGATIVE/NEUTRAL（null 视为中性）
     * @param qcEmotion 质检情绪态度分（0~100，null=无质检）
     * @param qcTotal   质检总分（0~100，null=无质检）
     * @param duration  通话时长（秒，可空）
     * @param status    话单状态 1接通 2转接 3未接
     * @param w         权重
     */
    public static double score(String emotion, Double qcEmotion, Double qcTotal,
                               Integer duration, String status, Weights w)
    {
        double score = 1.0;
        if (w != null)
        {
            if ("URGENT".equals(emotion))
            {
                score += w.urgent;
            }
            else if ("NEGATIVE".equals(emotion))
            {
                score += w.negative;
            }
            if (qcEmotion != null)
            {
                score += qcEmotion < 60 ? w.qcLowEmotion : (qcEmotion < 80 ? w.qcLowEmotion / 2 : 0);
            }
            if (qcTotal != null)
            {
                score += qcTotal < 60 ? w.qcLowTotal : (qcTotal < 80 ? w.qcLowTotal / 2 : 0);
            }
            if (duration != null && duration > 0 && duration <= w.shortSeconds)
            {
                score += w.shortCall;
            }
            if ("3".equals(status))
            {
                score += w.missed;
            }
            else if ("2".equals(status))
            {
                score += w.transferred;
            }
        }
        return score;
    }

    /** 预测量表值 1~4（截断后四舍五入取整） */
    public static int predict(String emotion, Double qcEmotion, Double qcTotal,
                              Integer duration, String status, Weights w)
    {
        double score = score(emotion, qcEmotion, qcTotal, duration, status, w);
        int rounded = BigDecimal.valueOf(score).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, rounded));
    }

    /** 评分构成说明（可解释性） */
    public static String detail(String emotion, Double qcEmotion, Double qcTotal,
                                Integer duration, String status, Weights w)
    {
        StringBuilder sb = new StringBuilder();
        if ("URGENT".equals(emotion))
        {
            sb.append("紧急情绪");
        }
        else if ("NEGATIVE".equals(emotion))
        {
            sb.append("负面情绪");
        }
        if (qcEmotion != null && qcEmotion < 80)
        {
            sb.append(sb.length() > 0 ? "+" : "").append("质检情绪低(").append(qcEmotion.intValue()).append(")");
        }
        if (qcTotal != null && qcTotal < 80)
        {
            sb.append(sb.length() > 0 ? "+" : "").append("质检总分低(").append(qcTotal.intValue()).append(")");
        }
        if (duration != null && duration > 0 && w != null && duration <= w.shortSeconds)
        {
            sb.append(sb.length() > 0 ? "+" : "").append("超短通话");
        }
        if ("3".equals(status))
        {
            sb.append(sb.length() > 0 ? "+" : "").append("未接");
        }
        else if ("2".equals(status))
        {
            sb.append(sb.length() > 0 ? "+" : "").append("转接");
        }
        return sb.length() > 0 ? sb.toString() : "无显著负向特征";
    }

    /** 权重配置（application.yml satisfaction.forecast.* 注入） */
    public static class Weights
    {
        public double urgent = 1.5;
        public double negative = 0.8;
        public double qcLowEmotion = 0.8;
        public double qcLowTotal = 0.6;
        public double shortCall = 0.4;
        public double missed = 1.0;
        public double transferred = 0.3;
        public int shortSeconds = 30;
    }
}
