package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 意图识别结果测试：未命中工厂与属性读写。
 *
 * @author ai-lawyers
 */
class IntentionMatchResultTest
{
    @Test
    void 未命中工厂()
    {
        IntentionMatchResult result = IntentionMatchResult.none("未识别到明确意图");

        assertFalse(result.isMatched());
        assertEquals("未识别到明确意图", result.getMessage());
        assertNull(result.getIntentionId());
        assertNull(result.getConfidence());
    }

    @Test
    void 属性读写()
    {
        IntentionMatchResult result = new IntentionMatchResult();
        result.setMatched(true);
        result.setIntentionId(1001L);
        result.setIntentionCode("LABOR_DISPUTE");
        result.setIntentionName("劳动争议");
        result.setCategory("劳动纠纷");
        result.setConfidence(new BigDecimal("0.92"));
        result.setMatchMethod("keyword");
        result.setCategoryId(20L);
        result.setCategoryName("民事诉讼");
        result.setMessage("命中");

        assertTrue(result.isMatched());
        assertEquals(1001L, result.getIntentionId());
        assertEquals("LABOR_DISPUTE", result.getIntentionCode());
        assertEquals("劳动争议", result.getIntentionName());
        assertEquals("劳动纠纷", result.getCategory());
        assertEquals(new BigDecimal("0.92"), result.getConfidence());
        assertEquals("keyword", result.getMatchMethod());
        assertEquals(20L, result.getCategoryId());
        assertEquals("民事诉讼", result.getCategoryName());
        assertEquals("命中", result.getMessage());
    }

    @Test
    void 未设置时为默认值()
    {
        IntentionMatchResult result = new IntentionMatchResult();

        assertFalse(result.isMatched());
        assertNull(result.getIntentionId());
        assertNull(result.getMessage());
    }
}
