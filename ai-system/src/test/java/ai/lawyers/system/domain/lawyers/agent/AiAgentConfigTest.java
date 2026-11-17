package ai.lawyers.system.domain.lawyers.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 智能体配置实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiAgentConfigTest
{
    @Test
    void 属性读写()
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setAgentId(1L);
        config.setAgentName("劳动法律助手");
        config.setProvider("dify");
        config.setApiUrl("https://api.dify.ai/v1/chat-messages");
        config.setApiKey("app-key-001");
        config.setAppId("app-001");
        config.setCategoryId(2L);
        config.setSystemPrompt("你是一名专业的劳动法律师助手");
        config.setModelId(3L);
        config.setKnowledgeIds("10,11");
        config.setEnableContext("1");
        config.setContextRounds(5);
        config.setHandoffKeywords("人工,转人工");
        config.setWelcome("您好，请问有什么可以帮您？");
        config.setStatus("1");

        assertEquals(1L, config.getAgentId());
        assertEquals("劳动法律助手", config.getAgentName());
        assertEquals("dify", config.getProvider());
        assertEquals("https://api.dify.ai/v1/chat-messages", config.getApiUrl());
        assertEquals("app-key-001", config.getApiKey());
        assertEquals("app-001", config.getAppId());
        assertEquals(2L, config.getCategoryId());
        assertEquals("你是一名专业的劳动法律师助手", config.getSystemPrompt());
        assertEquals(3L, config.getModelId());
        assertEquals("10,11", config.getKnowledgeIds());
        assertEquals("1", config.getEnableContext());
        assertEquals(5, config.getContextRounds());
        assertEquals("人工,转人工", config.getHandoffKeywords());
        assertEquals("您好，请问有什么可以帮您？", config.getWelcome());
        assertEquals("1", config.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiAgentConfig config = new AiAgentConfig();

        assertNull(config.getAgentId());
        assertNull(config.getAgentName());
        assertNull(config.getProvider());
        assertNull(config.getApiUrl());
        assertNull(config.getApiKey());
        assertNull(config.getAppId());
        assertNull(config.getCategoryId());
        assertNull(config.getSystemPrompt());
        assertNull(config.getModelId());
        assertNull(config.getKnowledgeIds());
        assertNull(config.getEnableContext());
        assertNull(config.getContextRounds());
        assertNull(config.getHandoffKeywords());
        assertNull(config.getWelcome());
        assertNull(config.getStatus());
    }

    @Test
    void 字符串输出包含智能体名称与平台()
    {
        AiAgentConfig config = new AiAgentConfig();
        config.setAgentName("合同审查助手");
        config.setProvider("coze");

        String text = config.toString();
        assertTrue(text.contains("合同审查助手"));
        assertTrue(text.contains("coze"));
    }
}
