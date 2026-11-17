package ai.lawyers.common.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 树结构基类测试：默认子节点与层级字段读写。
 *
 * @author ai-lawyers
 */
class TreeEntityTest
{
    @Test
    void 默认子节点为空列表()
    {
        assertTrue(new TreeEntity().getChildren().isEmpty());
    }

    @Test
    void 层级字段读写()
    {
        TreeEntity entity = new TreeEntity();
        entity.setParentName("总公司");
        entity.setParentId(100L);
        entity.setOrderNum(1);
        entity.setAncestors("0,100");

        assertEquals("总公司", entity.getParentName());
        assertEquals(100L, entity.getParentId());
        assertEquals(1, entity.getOrderNum());
        assertEquals("0,100", entity.getAncestors());
    }

    @Test
    void 子节点可替换()
    {
        TreeEntity entity = new TreeEntity();
        List<String> children = List.of("a", "b");

        entity.setChildren(children);

        assertSame(children, entity.getChildren());
    }
}
