package ai.lawyers.system.service.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模型 Token 用量解析测试：兼容 OpenAI / Claude 两类响应结构。
 *
 * @author ai-lawyers
 */
class ModelUsageTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseOpenAi_解析chat完整用量()
    {
        ModelUsage usage = ModelUsage.parseOpenAi(json(
                "{\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":34,\"total_tokens\":46}}"));

        assertEquals(12, usage.getPromptTokens());
        assertEquals(34, usage.getCompletionTokens());
        assertEquals(46, usage.getTotalTokens());
    }

    @Test
    void parseOpenAi_缺少total时按输入输出合计()
    {
        ModelUsage usage = ModelUsage.parseOpenAi(json(
                "{\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":5}}"));

        assertEquals(7, usage.getPromptTokens());
        assertEquals(5, usage.getCompletionTokens());
        assertEquals(12, usage.getTotalTokens());
    }

    @Test
    void parseOpenAi_仅返回total的向量模型计入输入侧()
    {
        ModelUsage usage = ModelUsage.parseOpenAi(json("{\"usage\":{\"total_tokens\":9}}"));

        assertEquals(0, usage.getPromptTokens());
        assertEquals(0, usage.getCompletionTokens());
        assertEquals(9, usage.getTotalTokens());
    }

    @Test
    void parseOpenAi_空节点或无usage返回零用量()
    {
        assertEquals(0, ModelUsage.parseOpenAi(null).getTotalTokens());
        assertEquals(0, ModelUsage.parseOpenAi(json("{}")).getTotalTokens());
        assertEquals(0, ModelUsage.parseOpenAi(json("{\"usage\":\"text\"}")).getTotalTokens());
    }

    @Test
    void parseClaude_解析input与output并合计()
    {
        ModelUsage usage = ModelUsage.parseClaude(json(
                "{\"usage\":{\"input_tokens\":20,\"output_tokens\":30}}"));

        assertEquals(20, usage.getPromptTokens());
        assertEquals(30, usage.getCompletionTokens());
        assertEquals(50, usage.getTotalTokens());
    }

    @Test
    void parseClaude_无usage返回零用量()
    {
        assertEquals(0, ModelUsage.parseClaude(null).getTotalTokens());
        assertEquals(0, ModelUsage.parseClaude(json("{\"id\":\"msg_1\"}")).getTotalTokens());
    }

    @Test
    void 构造函数_负值归零()
    {
        ModelUsage usage = new ModelUsage(-1, -5, -9);

        assertEquals(0, usage.getPromptTokens());
        assertEquals(0, usage.getCompletionTokens());
        assertEquals(0, usage.getTotalTokens());
    }

    @Test
    void ZERO_为零用量常量()
    {
        assertEquals(0, ModelUsage.ZERO.getPromptTokens());
        assertEquals(0, ModelUsage.ZERO.getCompletionTokens());
        assertEquals(0, ModelUsage.ZERO.getTotalTokens());
    }

    /** 将 JSON 文本解析为树节点 */
    private JsonNode json(String text)
    {
        try
        {
            return objectMapper.readTree(text);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }
}
