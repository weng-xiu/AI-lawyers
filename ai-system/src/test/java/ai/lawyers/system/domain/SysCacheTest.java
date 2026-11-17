package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 缓存信息测试：默认空串、名称备注构造与键名裁剪。
 *
 * @author ai-lawyers
 */
class SysCacheTest
{
    @Test
    void 默认构造_字段为空串()
    {
        SysCache cache = new SysCache();

        assertEquals("", cache.getCacheName());
        assertEquals("", cache.getCacheKey());
        assertEquals("", cache.getCacheValue());
        assertEquals("", cache.getRemark());
    }

    @Test
    void 名称与备注构造器()
    {
        SysCache cache = new SysCache("sys_config", "系统配置");

        assertEquals("sys_config", cache.getCacheName());
        assertEquals("系统配置", cache.getRemark());
        assertEquals("", cache.getCacheKey());
    }

    @Test
    void 名称键值构造器_裁剪缓存名称()
    {
        SysCache cache = new SysCache("sys_config", "sys_config:sys.index.skinName", "skin");

        assertEquals("sys_config", cache.getCacheName());
        assertEquals(":sys.index.skinName", cache.getCacheKey());
        assertEquals("skin", cache.getCacheValue());
    }

    @Test
    void 名称键值构造器_移除名称中的冒号()
    {
        SysCache cache = new SysCache("sys_dict:user_sex", "sys_dict:user_sex:0", "男");

        assertEquals("sys_dictuser_sex", cache.getCacheName());
        assertEquals(":0", cache.getCacheKey());
        assertTrue(cache.getCacheValue().contains("男"));
    }

    @Test
    void 属性读写()
    {
        SysCache cache = new SysCache();
        cache.setCacheName("name");
        cache.setCacheKey("key");
        cache.setCacheValue("value");
        cache.setRemark("备注");

        assertEquals("name", cache.getCacheName());
        assertEquals("key", cache.getCacheKey());
        assertEquals("value", cache.getCacheValue());
        assertEquals("备注", cache.getRemark());
    }
}
