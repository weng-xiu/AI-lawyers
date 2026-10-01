package ai.lawyers.system.service.impl.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.service.impl.lawyers.quality.QualityRuleEngine.RuleResult;
import ai.lawyers.system.service.impl.lawyers.quality.QualityRuleEngine.RuleViolation;

/**
 * P1-7：规则引擎纯逻辑单测（违禁词扣分、服务规范用语检查）。
 *
 * @author ai-lawyers
 */
class QualityRuleEngineTest
{
    private QualityRuleEngine engine;

    @BeforeEach
    void setUp() throws Exception
    {
        engine = new QualityRuleEngine();
        // 不走 Spring，@Value 字段置 null，parseBannedWords 会回退内置默认词表
        Field field = QualityRuleEngine.class.getDeclaredField("bannedWordsConfig");
        field.setAccessible(true);
        field.set(engine, null);
    }

    @Test
    void evaluate_fullCompliance_scores100()
    {
        RuleResult result = engine.evaluate(
                "您好，这里是12348公共法律服务热线，请问有什么可以帮您？感谢您的来电，再见");
        assertEquals(0, result.getBannedHits().size());
        assertEquals(100d, result.getComplianceScore(), 0.01);
        assertEquals(100d, result.serviceNormScore(), 0.01);
    }

    @Test
    void evaluate_twoBannedWords_complianceDeduct50()
    {
        RuleResult result = engine.evaluate(
                "您好，12348为您服务。这个案子我们承诺胜诉，风险代理也可以。再见");
        List<String> hits = result.getBannedHits();
        assertEquals(2, hits.size());
        assertTrue(hits.contains("承诺胜诉"));
        assertTrue(hits.contains("风险代理"));
        assertEquals(50d, result.getComplianceScore(), 0.01);
    }

    @Test
    void evaluate_emptyText_allNormMissing_score30()
    {
        RuleResult result = engine.evaluate("");
        assertEquals(30d, result.serviceNormScore(), 0.01);
        boolean greeting = false, identity = false, closing = false;
        for (RuleViolation v : result.getViolations())
        {
            greeting |= "问候缺失".equals(v.getKeyword());
            identity |= "身份缺失".equals(v.getKeyword());
            closing |= "结束语缺失".equals(v.getKeyword());
        }
        assertTrue(greeting && identity && closing);
    }

    @Test
    void evaluate_fourBannedWords_complianceFloor0()
    {
        RuleResult result = engine.evaluate(
                "承诺胜诉包赢保证打赢官司包在我身上");
        assertEquals(4, result.getBannedHits().size());
        assertEquals(0d, result.getComplianceScore(), 0.01);
    }

    @Test
    void evaluate_nullTranscript_treatedAsEmpty()
    {
        RuleResult result = engine.evaluate(null);
        assertEquals(0, result.getBannedHits().size());
        assertEquals(30d, result.serviceNormScore(), 0.01);
    }
}
