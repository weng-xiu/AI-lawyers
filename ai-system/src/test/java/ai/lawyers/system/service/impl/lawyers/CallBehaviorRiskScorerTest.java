package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * P2-15：呼叫行为风控评分器纯逻辑单测。
 *
 * @author ai-lawyers
 */
class CallBehaviorRiskScorerTest
{
    @Test
    void score_zeroCalls_returnsZero()
    {
        assertEquals(0, CallBehaviorRiskScorer.score(0, 0, 0, 0));
    }

    @Test
    void score_healthyCaller_belowThreshold()
    {
        // 12 通来电、仅 1 次未接：频次24 + 未接1 = 25 分，属正常范围
        int score = CallBehaviorRiskScorer.score(12, 0, 0, 1);
        assertEquals(25, score);
        assertEquals("LOW", CallBehaviorRiskScorer.level(score, 60, 80));
    }

    @Test
    void score_frequentCaller_reachesMedium()
    {
        // 25 通满频次 40 + 未接占比 40% 得 8 分 = 48……用 30 通 + 未接 12（40%）→ 40+8=48 不到 60
        // 换高频 + 半数未接：20 通 + 未接 10 → 40 + 10 = 50；40 通 + 未接 20 → 40 + 10 = 50？
        // 未接分上限 20：40 通未接 40 → 40+20 = 60 达 MEDIUM
        int score = CallBehaviorRiskScorer.score(40, 0, 0, 40);
        assertEquals(60, score);
        assertEquals("MEDIUM", CallBehaviorRiskScorer.level(score, 60, 80));
    }

    @Test
    void score_harassPattern_reachesHigh()
    {
        // 25 通全超短 + 多夜间 + 半数未接：频次40 + 超短20 + 夜间占比48%取9 + 未接10 = 79 仍不到 80
        // 全恶意的号码：30 通全超短全夜间全未接 → 40+20+20+20 = 100 封顶
        int score = CallBehaviorRiskScorer.score(30, 30, 30, 30);
        assertEquals(100, score);
        assertEquals("HIGH", CallBehaviorRiskScorer.level(score, 60, 80));
    }

    @Test
    void score_ratios_cappedPerComponent()
    {
        // 100 通（频次封顶40）+ 10% 超短(2) + 10% 夜间(2) + 10% 未接(2) = 46
        assertEquals(46, CallBehaviorRiskScorer.score(100, 10, 10, 10));
        // 频次分封顶校验：1000 通健康来电也只 40 分
        assertEquals(40, CallBehaviorRiskScorer.score(1000, 0, 0, 0));
    }

    @Test
    void scoreDetail_containsComponents()
    {
        String detail = CallBehaviorRiskScorer.scoreDetail(20, 10, 5, 2);
        assertEquals("频次40+超短10+夜间5+未接2", detail);
    }

    @Test
    void maskNumber_keepsHeadAndTail()
    {
        assertEquals("138****5678", CallBehaviorRiskScorer.maskNumber("13812345678"));
        assertEquals("****", CallBehaviorRiskScorer.maskNumber("1234"));
        assertEquals(null, CallBehaviorRiskScorer.maskNumber(null));
    }

    @Test
    void level_boundaries()
    {
        assertEquals("LOW", CallBehaviorRiskScorer.level(59, 60, 80));
        assertEquals("MEDIUM", CallBehaviorRiskScorer.level(60, 60, 80));
        assertEquals("MEDIUM", CallBehaviorRiskScorer.level(79, 60, 80));
        assertEquals("HIGH", CallBehaviorRiskScorer.level(80, 60, 80));
    }
}
