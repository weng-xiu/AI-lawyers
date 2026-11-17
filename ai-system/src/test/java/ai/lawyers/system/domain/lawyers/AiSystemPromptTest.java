package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 系统提示词配置实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiSystemPromptTest
{
    @Test
    void 属性读写()
    {
        AiSystemPrompt prompt = new AiSystemPrompt();
        prompt.setPromptId(1L);
        prompt.setPromptName("法律咨询助手提示词");
        prompt.setPromptType("system");
        prompt.setSceneType("consult");
        prompt.setContent("你是一名专业的中国律师助手");
        prompt.setVariables("{\"name\":\"张三\"}");
        prompt.setIsDefault("1");
        prompt.setStatus("0");

        assertEquals(1L, prompt.getPromptId());
        assertEquals("法律咨询助手提示词", prompt.getPromptName());
        assertEquals("system", prompt.getPromptType());
        assertEquals("consult", prompt.getSceneType());
        assertEquals("你是一名专业的中国律师助手", prompt.getContent());
        assertEquals("{\"name\":\"张三\"}", prompt.getVariables());
        assertEquals("1", prompt.getIsDefault());
        assertEquals("0", prompt.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiSystemPrompt prompt = new AiSystemPrompt();

        assertNull(prompt.getPromptId());
        assertNull(prompt.getPromptName());
        assertNull(prompt.getPromptType());
        assertNull(prompt.getSceneType());
        assertNull(prompt.getContent());
        assertNull(prompt.getVariables());
        assertNull(prompt.getIsDefault());
        assertNull(prompt.getStatus());
    }

    @Test
    void 字符串输出包含提示词名称与内容()
    {
        AiSystemPrompt prompt = new AiSystemPrompt();
        prompt.setPromptName("合同审查提示词");
        prompt.setContent("请逐条审查合同条款");

        String text = prompt.toString();
        assertTrue(text.contains("合同审查提示词"));
        assertTrue(text.contains("请逐条审查合同条款"));
    }
}
