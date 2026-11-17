package ai.lawyers.system.domain.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 路由配置测试：字段读写、子路由与空字段序列化。
 *
 * @author ai-lawyers
 */
class RouterVoTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 字段读写()
    {
        RouterVo router = new RouterVo();
        MetaVo meta = new MetaVo("用户管理", "user");
        router.setName("User");
        router.setPath("user");
        router.setHidden(true);
        router.setRedirect("noRedirect");
        router.setComponent("system/user/index");
        router.setQuery("{\"id\":1}");
        router.setAlwaysShow(true);
        router.setMeta(meta);
        router.setChildren(List.of(new RouterVo()));

        assertEquals("User", router.getName());
        assertEquals("user", router.getPath());
        assertTrue(router.getHidden());
        assertEquals("noRedirect", router.getRedirect());
        assertEquals("system/user/index", router.getComponent());
        assertEquals("{\"id\":1}", router.getQuery());
        assertTrue(router.getAlwaysShow());
        assertEquals(meta, router.getMeta());
        assertEquals(1, router.getChildren().size());
    }

    @Test
    void 序列化时忽略空字段()
    {
        String emptyJson = objectMapper.valueToTree(new RouterVo()).toString();
        assertFalse(emptyJson.contains("name"));

        RouterVo router = new RouterVo();
        router.setName("User");
        router.setPath("user");

        String json = objectMapper.valueToTree(router).toString();
        assertTrue(json.contains("\"name\":\"User\""));
        assertTrue(json.contains("\"path\":\"user\""));
    }
}
