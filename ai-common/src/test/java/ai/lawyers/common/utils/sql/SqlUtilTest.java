package ai.lawyers.common.utils.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.exception.UtilException;

/**
 * SQL 工具测试：排序字段白名单与注入关键字拦截。
 *
 * @author ai-lawyers
 */
class SqlUtilTest
{
    @Test
    void isValidOrderBySql_允许字母数字下划线空格逗号小数点()
    {
        assertTrue(SqlUtil.isValidOrderBySql("name asc, id desc"));
        assertTrue(SqlUtil.isValidOrderBySql("t1.create_time"));
    }

    @Test
    void isValidOrderBySql_拒绝含特殊符号的排序()
    {
        assertFalse(SqlUtil.isValidOrderBySql("name desc--"));
        assertFalse(SqlUtil.isValidOrderBySql("id;drop table t"));
    }

    @Test
    void escapeOrderBySql_合法值原样返回()
    {
        assertEquals("id desc", SqlUtil.escapeOrderBySql("id desc"));
        assertNull(SqlUtil.escapeOrderBySql(null));
    }

    @Test
    void escapeOrderBySql_非法字符抛异常()
    {
        assertThrows(UtilException.class, () -> SqlUtil.escapeOrderBySql("id desc;drop"));
    }

    @Test
    void escapeOrderBySql_超长排序抛异常()
    {
        String longValue = new String(new char[501]).replace('\0', 'a');

        assertThrows(UtilException.class, () -> SqlUtil.escapeOrderBySql(longValue));
    }

    @Test
    void filterKeyword_拦截SQL关键字()
    {
        assertThrows(UtilException.class, () -> SqlUtil.filterKeyword("select * from t_user"));
        assertThrows(UtilException.class, () -> SqlUtil.filterKeyword("1 union 2"));
        assertThrows(UtilException.class, () -> SqlUtil.filterKeyword("admin' or 1=1"));
    }

    @Test
    void filterKeyword_普通文本与空值放行()
    {
        SqlUtil.filterKeyword("劳动仲裁咨询");
        SqlUtil.filterKeyword(null);
        SqlUtil.filterKeyword("");
    }
}
