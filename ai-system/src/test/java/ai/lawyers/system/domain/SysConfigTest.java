package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 参数配置实体测试：属性读写与字符串输出。
 *
 * @author ai-lawyers
 */
class SysConfigTest
{
    @Test
    void 属性读写()
    {
        SysConfig config = new SysConfig();
        config.setConfigId(1L);
        config.setConfigName("账号自助-验证码开关");
        config.setConfigKey("sys.account.captchaEnabled");
        config.setConfigValue("true");
        config.setConfigType("Y");

        assertEquals(1L, config.getConfigId());
        assertEquals("账号自助-验证码开关", config.getConfigName());
        assertEquals("sys.account.captchaEnabled", config.getConfigKey());
        assertEquals("true", config.getConfigValue());
        assertEquals("Y", config.getConfigType());
    }

    @Test
    void 默认值为空()
    {
        SysConfig config = new SysConfig();

        assertNull(config.getConfigId());
        assertNull(config.getConfigName());
        assertNull(config.getConfigKey());
        assertNull(config.getConfigValue());
        assertNull(config.getConfigType());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        SysConfig config = new SysConfig();
        config.setConfigId(2L);
        config.setConfigKey("sys.user.initPassword");
        config.setConfigValue("123456");

        String text = config.toString();
        assertTrue(text.contains("sys.user.initPassword"));
        assertTrue(text.contains("123456"));
    }
}
