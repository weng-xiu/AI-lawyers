package ai.lawyers.system.service.impl.lawyers;

/**
 * 呼叫行为风控评分器（P2-15，纯逻辑无依赖，便于单测）。
 *
 * <p>可解释加权模型：总分 = 频次(40) + 超短通话(20) + 夜间来电(20) + 未接(20)，上限 100。</p>
 * <ul>
 *   <li>频次分：每通 2 分，40 分封顶（20 通/窗口即满分）；</li>
 *   <li>超短通话分：超短占比 × 20（占比 100% 得满分）；</li>
 *   <li>夜间来电分：夜间占比 × 20；</li>
 *   <li>未接分：未接占比 × 20。</li>
 * </ul>
 *
 * <p>等级：score ≥ highThreshold(默认80) HIGH；≥ mediumThreshold(默认60) MEDIUM；否则 LOW。</p>
 *
 * @author ai-lawyers
 */
public class CallBehaviorRiskScorer
{
    /** 频次分上限 */
    public static final int FREQ_MAX = 40;
    /** 比率类分数（超短/夜间/未接）单项上限 */
    public static final int RATE_MAX = 20;

    /** 默认中风险阈值 */
    public static final int DEFAULT_MEDIUM_THRESHOLD = 60;
    /** 默认高风险阈值 */
    public static final int DEFAULT_HIGH_THRESHOLD = 80;

    /**
     * 计算风险评分。
     *
     * @param callCount   窗口内来电次数（&gt;0，否则返回 0）
     * @param shortCount  超短通话次数
     * @param nightCount  夜间来电次数
     * @param missedCount 未接来电次数
     * @return 0~100 评分
     */
    public static int score(int callCount, int shortCount, int nightCount, int missedCount)
    {
        if (callCount <= 0)
        {
            return 0;
        }
        int freqScore = Math.min(FREQ_MAX, callCount * 2);
        int shortScore = Math.min(RATE_MAX, shortCount * RATE_MAX / callCount);
        int nightScore = Math.min(RATE_MAX, nightCount * RATE_MAX / callCount);
        int missedScore = Math.min(RATE_MAX, missedCount * RATE_MAX / callCount);
        return Math.min(100, freqScore + shortScore + nightScore + missedScore);
    }

    /** 评分构成说明（可解释性，写入 score_detail） */
    public static String scoreDetail(int callCount, int shortCount, int nightCount, int missedCount)
    {
        if (callCount <= 0)
        {
            return "无来电样本";
        }
        int freqScore = Math.min(FREQ_MAX, callCount * 2);
        int shortScore = Math.min(RATE_MAX, shortCount * RATE_MAX / callCount);
        int nightScore = Math.min(RATE_MAX, nightCount * RATE_MAX / callCount);
        int missedScore = Math.min(RATE_MAX, missedCount * RATE_MAX / callCount);
        return "频次" + freqScore + "+超短" + shortScore + "+夜间" + nightScore + "+未接" + missedScore;
    }

    /** 风险等级：≥high HIGH，≥medium MEDIUM，否则 LOW */
    public static String level(int score, int mediumThreshold, int highThreshold)
    {
        if (score >= highThreshold)
        {
            return "HIGH";
        }
        if (score >= mediumThreshold)
        {
            return "MEDIUM";
        }
        return "LOW";
    }

    /** 号码脱敏：保留前 3 后 4，其余置 *（长度不足 8 位全部置 *） */
    public static String maskNumber(String plain)
    {
        if (plain == null || plain.isEmpty())
        {
            return plain;
        }
        int len = plain.length();
        if (len < 8)
        {
            return "*".repeat(len);
        }
        return plain.substring(0, 3) + "*".repeat(len - 7) + plain.substring(len - 4);
    }
}
