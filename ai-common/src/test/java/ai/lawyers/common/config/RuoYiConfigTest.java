package ai.lawyers.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 项目配置测试：静态属性读写与各类上传路径拼接。
 *
 * @author ai-lawyers
 */
class RuoYiConfigTest
{
    private String oldProfile;

    private boolean oldAddressEnabled;

    private String oldCaptchaType;

    @BeforeEach
    void setUp()
    {
        oldProfile = RuoYiConfig.getProfile();
        oldAddressEnabled = RuoYiConfig.isAddressEnabled();
        oldCaptchaType = RuoYiConfig.getCaptchaType();
    }

    @AfterEach
    void tearDown()
    {
        RuoYiConfig config = new RuoYiConfig();
        config.setProfile(oldProfile);
        config.setAddressEnabled(oldAddressEnabled);
        config.setCaptchaType(oldCaptchaType);
    }

    @Test
    void 静态属性读写()
    {
        RuoYiConfig config = new RuoYiConfig();
        config.setProfile("/data/upload");
        config.setAddressEnabled(true);
        config.setCaptchaType("math");

        assertEquals("/data/upload", RuoYiConfig.getProfile());
        assertTrue(RuoYiConfig.isAddressEnabled());
        assertEquals("math", RuoYiConfig.getCaptchaType());

        config.setAddressEnabled(false);
        assertFalse(RuoYiConfig.isAddressEnabled());
    }

    @Test
    void 上传路径按profile拼接()
    {
        new RuoYiConfig().setProfile("/data/upload");

        assertEquals("/data/upload/import", RuoYiConfig.getImportPath());
        assertEquals("/data/upload/avatar", RuoYiConfig.getAvatarPath());
        assertEquals("/data/upload/download/", RuoYiConfig.getDownloadPath());
        assertEquals("/data/upload/upload", RuoYiConfig.getUploadPath());
    }

    @Test
    void 实例属性读写()
    {
        RuoYiConfig config = new RuoYiConfig();
        config.setName("AI律师");
        config.setVersion("1.0.0");
        config.setCopyrightYear("2026");

        assertEquals("AI律师", config.getName());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("2026", config.getCopyrightYear());
    }
}
