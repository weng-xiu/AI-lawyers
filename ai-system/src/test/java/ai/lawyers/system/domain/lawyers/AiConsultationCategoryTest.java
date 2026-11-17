package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 咨询分类实体测试：属性读写、子节点与默认值。
 *
 * @author ai-lawyers
 */
class AiConsultationCategoryTest
{
    @Test
    void 属性读写()
    {
        AiConsultationCategory category = new AiConsultationCategory();
        category.setCategoryId(1L);
        category.setCategoryName("劳动争议");
        category.setParentId(0L);
        category.setAncestors("0");
        category.setOrderNum(1);
        category.setLeaderId(2L);
        category.setLeader("张三");
        category.setPhone("13800000000");
        category.setEmail("zhangsan@example.com");
        category.setStatus("0");
        category.setDelFlag("0");

        assertEquals(1L, category.getCategoryId());
        assertEquals("劳动争议", category.getCategoryName());
        assertEquals(0L, category.getParentId());
        assertEquals("0", category.getAncestors());
        assertEquals(1, category.getOrderNum());
        assertEquals(2L, category.getLeaderId());
        assertEquals("张三", category.getLeader());
        assertEquals("13800000000", category.getPhone());
        assertEquals("zhangsan@example.com", category.getEmail());
        assertEquals("0", category.getStatus());
        assertEquals("0", category.getDelFlag());
    }

    @Test
    void 默认值为空()
    {
        AiConsultationCategory category = new AiConsultationCategory();

        assertNull(category.getCategoryId());
        assertNull(category.getCategoryName());
        assertNull(category.getParentId());
        assertNull(category.getAncestors());
        assertNull(category.getOrderNum());
        assertNull(category.getLeaderId());
        assertNull(category.getLeader());
        assertNull(category.getPhone());
        assertNull(category.getEmail());
        assertNull(category.getStatus());
        assertNull(category.getDelFlag());
        assertNull(category.getChildren());
    }

    @Test
    void 设置子分类()
    {
        AiConsultationCategory parent = new AiConsultationCategory();
        parent.setCategoryId(1L);

        AiConsultationCategory child = new AiConsultationCategory();
        child.setCategoryId(2L);
        child.setParentId(1L);

        List<AiConsultationCategory> children = Collections.singletonList(child);
        parent.setChildren(children);

        assertSame(children, parent.getChildren());
        assertEquals(1, parent.getChildren().size());
        assertEquals(2L, parent.getChildren().get(0).getCategoryId());
    }

    @Test
    void 字符串输出包含分类名称()
    {
        AiConsultationCategory category = new AiConsultationCategory();
        category.setCategoryId(3L);
        category.setCategoryName("婚姻家事");

        String text = category.toString();
        assertTrue(text.contains("婚姻家事"));
        assertTrue(text.contains("categoryId"));
    }
}
