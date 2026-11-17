package ai.lawyers.common.core.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 分页数据测试：排序字段下划线转换、前端排序类型兼容、合理分页默认值。
 *
 * @author ai-lawyers
 */
class PageDomainTest
{
    @Test
    void getOrderBy_排序列为空时返回空串()
    {
        PageDomain pageDomain = new PageDomain();

        assertEquals("", pageDomain.getOrderBy());
        pageDomain.setOrderByColumn("");
        assertEquals("", pageDomain.getOrderBy());
    }

    @Test
    void getOrderBy_驼峰列名转下划线并拼接方向()
    {
        PageDomain pageDomain = new PageDomain();
        pageDomain.setOrderByColumn("createTime");

        // 默认升序
        assertEquals("create_time asc", pageDomain.getOrderBy());

        pageDomain.setIsAsc("descending");
        assertEquals("create_time desc", pageDomain.getOrderBy());
    }

    @Test
    void setIsAsc_兼容前端排序类型()
    {
        PageDomain pageDomain = new PageDomain();

        pageDomain.setIsAsc("ascending");
        assertEquals("asc", pageDomain.getIsAsc());

        pageDomain.setIsAsc("descending");
        assertEquals("desc", pageDomain.getIsAsc());

        // 非前端枚举值原样保留
        pageDomain.setIsAsc("desc");
        assertEquals("desc", pageDomain.getIsAsc());
    }

    @Test
    void setIsAsc_空值保留原排序方向()
    {
        PageDomain pageDomain = new PageDomain();
        pageDomain.setIsAsc("desc");

        pageDomain.setIsAsc(null);
        assertEquals("desc", pageDomain.getIsAsc());

        pageDomain.setIsAsc("");
        assertEquals("desc", pageDomain.getIsAsc());

        // 未设置过时默认 asc
        assertEquals("asc", new PageDomain().getIsAsc());
    }

    @Test
    void getReasonable_空值默认开启合理分页()
    {
        PageDomain pageDomain = new PageDomain();
        assertTrue(pageDomain.getReasonable());

        pageDomain.setReasonable(null);
        assertTrue(pageDomain.getReasonable());

        pageDomain.setReasonable(Boolean.FALSE);
        assertFalse(pageDomain.getReasonable());

        pageDomain.setReasonable(Boolean.TRUE);
        assertTrue(pageDomain.getReasonable());
    }

    @Test
    void getter与setter_页码与每页条数()
    {
        PageDomain pageDomain = new PageDomain();
        assertNull(pageDomain.getPageNum());
        assertNull(pageDomain.getPageSize());
        assertNull(pageDomain.getOrderByColumn());

        pageDomain.setPageNum(3);
        pageDomain.setPageSize(20);
        pageDomain.setOrderByColumn("updateTime");

        assertEquals(Integer.valueOf(3), pageDomain.getPageNum());
        assertEquals(Integer.valueOf(20), pageDomain.getPageSize());
        assertEquals("updateTime", pageDomain.getOrderByColumn());
    }
}
