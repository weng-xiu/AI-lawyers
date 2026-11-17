package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * AI模型参数配置实体测试：属性读写、价格字段与字符串输出。
 *
 * @author ai-lawyers
 */
class AiModelConfigTest
{
    @Test
    void 属性读写()
    {
        AiModelConfig config = new AiModelConfig();
        config.setConfigId(1L);
        config.setConfigName("默认对话模型");
        config.setModelType("chat");
        config.setModelName("deepseek-chat");
        config.setApiKey("sk-test");
        config.setApiUrl("https://api.example.com/v1");
        config.setMaxTokens(4096);
        config.setTemperature(0.7D);
        config.setTopP(0.9D);
        config.setFrequencyPenalty(0.1D);
        config.setPresencePenalty(0.2D);
        config.setTimeout(30);
        config.setRetryCount(3);
        config.setStatus("0");
        config.setIsDefault("1");

        assertEquals(1L, config.getConfigId());
        assertEquals("默认对话模型", config.getConfigName());
        assertEquals("chat", config.getModelType());
        assertEquals("deepseek-chat", config.getModelName());
        assertEquals("sk-test", config.getApiKey());
        assertEquals("https://api.example.com/v1", config.getApiUrl());
        assertEquals(4096, config.getMaxTokens());
        assertEquals(0.7D, config.getTemperature());
        assertEquals(0.9D, config.getTopP());
        assertEquals(0.1D, config.getFrequencyPenalty());
        assertEquals(0.2D, config.getPresencePenalty());
        assertEquals(30, config.getTimeout());
        assertEquals(3, config.getRetryCount());
        assertEquals("0", config.getStatus());
        assertEquals("1", config.getIsDefault());
    }

    @Test
    void 单价字段读写()
    {
        AiModelConfig config = new AiModelConfig();
        BigDecimal inputPrice = new BigDecimal("0.001");
        BigDecimal outputPrice = new BigDecimal("0.002");
        config.setInputPrice(inputPrice);
        config.setOutputPrice(outputPrice);

        assertEquals(inputPrice, config.getInputPrice());
        assertEquals(outputPrice, config.getOutputPrice());
    }

    @Test
    void 默认值为空()
    {
        AiModelConfig config = new AiModelConfig();

        assertNull(config.getConfigId());
        assertNull(config.getConfigName());
        assertNull(config.getApiKey());
        assertNull(config.getInputPrice());
        assertNull(config.getTemperature());
        assertNull(config.getIsDefault());
    }

    @Test
    void 字符串输出包含配置名称与模型名称()
    {
        AiModelConfig config = new AiModelConfig();
        config.setConfigName("向量模型");
        config.setModelName("bge-m3");

        String text = config.toString();
        assertTrue(text.contains("向量模型"));
        assertTrue(text.contains("bge-m3"));
    }
}
