package ai.lawyers.common.constant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 缓存键常量测试：各业务缓存前缀。
 *
 * @author ai-lawyers
 */
class CacheConstantsTest
{
    @Test
    void 缓存键前缀()
    {
        assertEquals("login_tokens:", CacheConstants.LOGIN_TOKEN_KEY);
        assertEquals("captcha_codes:", CacheConstants.CAPTCHA_CODE_KEY);
        assertEquals("sys_config:", CacheConstants.SYS_CONFIG_KEY);
        assertEquals("sys_dict:", CacheConstants.SYS_DICT_KEY);
        assertEquals("repeat_submit:", CacheConstants.REPEAT_SUBMIT_KEY);
        assertEquals("rate_limit:", CacheConstants.RATE_LIMIT_KEY);
        assertEquals("pwd_err_cnt:", CacheConstants.PWD_ERR_CNT_KEY);
    }
}
