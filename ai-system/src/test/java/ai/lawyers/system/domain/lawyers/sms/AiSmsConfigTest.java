package ai.lawyers.system.domain.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 短信通道配置实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiSmsConfigTest
{
    @Test
    void 属性读写()
    {
        AiSmsConfig config = new AiSmsConfig();
        config.setConfigId(1L);
        config.setConfigName("主短信通道");
        config.setProvider("aliyun");
        config.setAccessKeyId("ak-001");
        config.setAccessKeySecret("secret-001");
        config.setSignName("AI律师");
        config.setRegionId("cn-hangzhou");
        config.setSdkAppId("1400000000");
        config.setDailyLimit(100);
        config.setStatus("1");

        assertEquals(1L, config.getConfigId());
        assertEquals("主短信通道", config.getConfigName());
        assertEquals("aliyun", config.getProvider());
        assertEquals("ak-001", config.getAccessKeyId());
        assertEquals("secret-001", config.getAccessKeySecret());
        assertEquals("AI律师", config.getSignName());
        assertEquals("cn-hangzhou", config.getRegionId());
        assertEquals("1400000000", config.getSdkAppId());
        assertEquals(100, config.getDailyLimit());
        assertEquals("1", config.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiSmsConfig config = new AiSmsConfig();

        assertNull(config.getConfigId());
        assertNull(config.getConfigName());
        assertNull(config.getProvider());
        assertNull(config.getDailyLimit());
        assertNull(config.getStatus());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiSmsConfig config = new AiSmsConfig();
        config.setConfigId(1L);
        config.setConfigName("主短信通道");
        config.setProvider("aliyun");
        config.setStatus("1");

        String text = config.toString();

        assertTrue(text.contains("主短信通道"));
        assertTrue(text.contains("aliyun"));
        assertTrue(text.contains("configId"));
    }
}
