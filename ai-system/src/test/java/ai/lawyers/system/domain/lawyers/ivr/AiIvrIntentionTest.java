package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * IVR意图实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiIvrIntentionTest
{
    @Test
    void 属性读写()
    {
        AiIvrIntention intention = new AiIvrIntention();
        intention.setIntentionId(1L);
        intention.setIntentionName("劳动争议");
        intention.setIntentionCode("LABOR");
        intention.setDescription("涉及劳动关系的咨询");
        intention.setCategory("民事");
        intention.setRegexPattern("工资|仲裁|解除");
        intention.setPromptTemplate("请识别用户意图");
        intention.setExampleUtterances("公司拖欠工资怎么办");
        intention.setModelId(2L);
        intention.setPriority(1);
        intention.setStatus("0");

        assertEquals(1L, intention.getIntentionId());
        assertEquals("劳动争议", intention.getIntentionName());
        assertEquals("LABOR", intention.getIntentionCode());
        assertEquals("涉及劳动关系的咨询", intention.getDescription());
        assertEquals("民事", intention.getCategory());
        assertEquals("工资|仲裁|解除", intention.getRegexPattern());
        assertEquals("请识别用户意图", intention.getPromptTemplate());
        assertEquals("公司拖欠工资怎么办", intention.getExampleUtterances());
        assertEquals(2L, intention.getModelId());
        assertEquals(1, intention.getPriority());
        assertEquals("0", intention.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiIvrIntention intention = new AiIvrIntention();

        assertNull(intention.getIntentionId());
        assertNull(intention.getIntentionName());
        assertNull(intention.getIntentionCode());
        assertNull(intention.getRegexPattern());
        assertNull(intention.getPriority());
        assertNull(intention.getStatus());
    }

    @Test
    void 字符串输出包含意图名称与编码()
    {
        AiIvrIntention intention = new AiIvrIntention();
        intention.setIntentionName("婚姻家事");
        intention.setIntentionCode("MARRIAGE");

        String text = intention.toString();
        assertTrue(text.contains("婚姻家事"));
        assertTrue(text.contains("MARRIAGE"));
    }
}
