package ai.lawyers.system.domain.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 质检评分模板实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiQualityTemplateTest
{
    @Test
    void 属性读写()
    {
        AiQualityTemplate template = new AiQualityTemplate();
        template.setTemplateId(1L);
        template.setTemplateName("通用服务质检模板");
        template.setDimensions("[{\"key\":\"serviceNorm\",\"name\":\"服务规范\",\"weight\":1.0}]");
        template.setScorePrompt("请按服务规范维度评分");
        template.setIsDefault("1");
        template.setStatus("0");

        assertEquals(1L, template.getTemplateId());
        assertEquals("通用服务质检模板", template.getTemplateName());
        assertEquals("[{\"key\":\"serviceNorm\",\"name\":\"服务规范\",\"weight\":1.0}]", template.getDimensions());
        assertEquals("请按服务规范维度评分", template.getScorePrompt());
        assertEquals("1", template.getIsDefault());
        assertEquals("0", template.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiQualityTemplate template = new AiQualityTemplate();

        assertNull(template.getTemplateId());
        assertNull(template.getTemplateName());
        assertNull(template.getDimensions());
        assertNull(template.getScorePrompt());
        assertNull(template.getIsDefault());
        assertNull(template.getStatus());
    }
}
