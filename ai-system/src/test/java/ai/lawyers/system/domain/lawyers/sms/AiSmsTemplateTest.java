package ai.lawyers.system.domain.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 短信模板实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiSmsTemplateTest
{
    @Test
    void 属性读写()
    {
        AiSmsTemplate template = new AiSmsTemplate();
        template.setTemplateId(1L);
        template.setTemplateName("排队提醒");
        template.setProviderTemplateCode("SMS_001");
        template.setContent("您前面的排队人数为 ${count} 人");
        template.setSceneType("queue");
        template.setConfigId(2L);
        template.setStatus("1");
        template.setConfigName("阿里云短信");

        assertEquals(1L, template.getTemplateId());
        assertEquals("排队提醒", template.getTemplateName());
        assertEquals("SMS_001", template.getProviderTemplateCode());
        assertEquals("您前面的排队人数为 ${count} 人", template.getContent());
        assertEquals("queue", template.getSceneType());
        assertEquals(2L, template.getConfigId());
        assertEquals("1", template.getStatus());
        assertEquals("阿里云短信", template.getConfigName());
    }

    @Test
    void 默认值为空()
    {
        AiSmsTemplate template = new AiSmsTemplate();

        assertNull(template.getTemplateId());
        assertNull(template.getTemplateName());
        assertNull(template.getProviderTemplateCode());
        assertNull(template.getContent());
        assertNull(template.getSceneType());
        assertNull(template.getConfigId());
        assertNull(template.getStatus());
        assertNull(template.getConfigName());
    }

    @Test
    void 字符串输出包含模板名称与场景()
    {
        AiSmsTemplate template = new AiSmsTemplate();
        template.setTemplateId(5L);
        template.setTemplateName("欢迎语");
        template.setSceneType("welcome");

        String text = template.toString();
        assertTrue(text.contains("欢迎语"));
        assertTrue(text.contains("welcome"));
    }
}
