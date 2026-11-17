package ai.lawyers.common.core.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 表格分页数据测试：默认空值、构造器赋值与字段读写。
 *
 * @author ai-lawyers
 */
class TableDataInfoTest
{
    @Test
    void 默认构造_全部零值()
    {
        TableDataInfo data = new TableDataInfo();

        assertEquals(0L, data.getTotal());
        assertEquals(0, data.getCode());
        assertNull(data.getRows());
        assertNull(data.getMsg());
    }

    @Test
    void 构造器设置列表与总数()
    {
        TableDataInfo data = new TableDataInfo(List.of("a", "b"), 20L);

        assertEquals(20L, data.getTotal());
        assertEquals(List.of("a", "b"), data.getRows());
    }

    @Test
    void 字段读写()
    {
        TableDataInfo data = new TableDataInfo();
        data.setCode(200);
        data.setMsg("查询成功");
        data.setRows(List.of());
        data.setTotal(3L);

        assertEquals(200, data.getCode());
        assertEquals("查询成功", data.getMsg());
        assertTrue(data.getRows().isEmpty());
        assertEquals(3L, data.getTotal());
    }
}
