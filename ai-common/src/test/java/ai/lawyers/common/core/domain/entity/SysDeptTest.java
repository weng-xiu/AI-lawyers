package ai.lawyers.common.core.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 部门实体测试：默认子部门与层级字段读写。
 *
 * @author ai-lawyers
 */
class SysDeptTest
{
    @Test
    void 默认子部门为空列表()
    {
        assertTrue(new SysDept().getChildren().isEmpty());
    }

    @Test
    void 字段读写()
    {
        SysDept dept = new SysDept();
        dept.setDeptId(100L);
        dept.setParentId(0L);
        dept.setAncestors("0");
        dept.setDeptName("技术部");
        dept.setOrderNum(1);
        dept.setLeader("张三");
        dept.setPhone("13800000000");
        dept.setEmail("tech@example.com");
        dept.setStatus("0");
        dept.setDelFlag("0");
        dept.setParentName("总公司");

        assertEquals(100L, dept.getDeptId());
        assertEquals(0L, dept.getParentId());
        assertEquals("0", dept.getAncestors());
        assertEquals("技术部", dept.getDeptName());
        assertEquals(1, dept.getOrderNum());
        assertEquals("张三", dept.getLeader());
        assertEquals("13800000000", dept.getPhone());
        assertEquals("tech@example.com", dept.getEmail());
        assertEquals("0", dept.getStatus());
        assertEquals("0", dept.getDelFlag());
        assertEquals("总公司", dept.getParentName());
    }

    @Test
    void 子部门可替换且toString包含名称()
    {
        SysDept dept = new SysDept();
        dept.setDeptName("技术部");
        SysDept child = new SysDept();
        dept.setChildren(List.of(child));

        assertEquals(1, dept.getChildren().size());
        assertTrue(dept.toString().contains("技术部"));
    }
}
