package ai.lawyers.system.domain.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 路由显示信息测试：构造器组合与外部链接过滤。
 *
 * @author ai-lawyers
 */
class MetaVoTest
{
    @Test
    void 两参构造器()
    {
        MetaVo meta = new MetaVo("用户管理", "user");

        assertEquals("用户管理", meta.getTitle());
        assertEquals("user", meta.getIcon());
        assertFalse(meta.isNoCache());
        assertNull(meta.getLink());
    }

    @Test
    void 三参构造器_是否缓存()
    {
        MetaVo meta = new MetaVo("用户管理", "user", true);

        assertTrue(meta.isNoCache());
        assertNull(meta.getLink());
    }

    @Test
    void 三参构造器_外链()
    {
        MetaVo meta = new MetaVo("外部系统", "link", "https://example.com");

        assertEquals("https://example.com", meta.getLink());
        assertFalse(meta.isNoCache());
    }

    @Test
    void 四参构造器_仅保留http外链()
    {
        MetaVo httpLink = new MetaVo("外部系统", "link", true, "http://example.com");
        assertEquals("http://example.com", httpLink.getLink());
        assertTrue(httpLink.isNoCache());

        MetaVo innerPath = new MetaVo("内部页面", "link", false, "/system/user");
        assertNull(innerPath.getLink());
    }

    @Test
    void 属性读写()
    {
        MetaVo meta = new MetaVo();
        meta.setTitle("角色管理");
        meta.setIcon("peoples");
        meta.setNoCache(true);
        meta.setLink("https://example.com");

        assertEquals("角色管理", meta.getTitle());
        assertEquals("peoples", meta.getIcon());
        assertTrue(meta.isNoCache());
        assertEquals("https://example.com", meta.getLink());
    }
}
