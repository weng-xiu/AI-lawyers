package ai.lawyers.common.constant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 代码生成常量测试：模板类型、字段分类清单与控件/查询类型取值。
 *
 * @author ai-lawyers
 */
class GenConstantsTest
{
    @Test
    void 模板类型与树字段()
    {
        assertEquals("crud", GenConstants.TPL_CRUD);
        assertEquals("tree", GenConstants.TPL_TREE);
        assertEquals("sub", GenConstants.TPL_SUB);
        assertEquals("treeCode", GenConstants.TREE_CODE);
        assertEquals("treeParentCode", GenConstants.TREE_PARENT_CODE);
        assertEquals("treeName", GenConstants.TREE_NAME);
    }

    @Test
    void 数据库列类型清单()
    {
        assertArrayEquals(new String[] { "char", "varchar", "nvarchar", "varchar2" }, GenConstants.COLUMNTYPE_STR);
        assertArrayEquals(new String[] { "tinytext", "text", "mediumtext", "longtext" }, GenConstants.COLUMNTYPE_TEXT);
        assertArrayEquals(new String[] { "datetime", "time", "date", "timestamp" }, GenConstants.COLUMNTYPE_TIME);
        assertEquals(11, GenConstants.COLUMNTYPE_NUMBER.length);
    }

    @Test
    void 排除字段与控件类型()
    {
        assertArrayEquals(new String[] { "id", "create_by", "create_time", "del_flag" },
                GenConstants.COLUMNNAME_NOT_EDIT);
        assertEquals(6, GenConstants.COLUMNNAME_NOT_LIST.length);
        assertEquals(7, GenConstants.COLUMNNAME_NOT_QUERY.length);
        assertArrayEquals(new String[] { "createBy", "createTime", "updateBy", "updateTime", "remark" },
                GenConstants.BASE_ENTITY);
        assertArrayEquals(new String[] { "parentName", "parentId", "orderNum", "ancestors", "children" },
                GenConstants.TREE_ENTITY);
        assertEquals("input", GenConstants.HTML_INPUT);
        assertEquals("textarea", GenConstants.HTML_TEXTAREA);
        assertEquals("select", GenConstants.HTML_SELECT);
        assertEquals("imageUpload", GenConstants.HTML_IMAGE_UPLOAD);
        assertEquals("editor", GenConstants.HTML_EDITOR);
    }

    @Test
    void Java类型与查询方式()
    {
        assertEquals("String", GenConstants.TYPE_STRING);
        assertEquals("Integer", GenConstants.TYPE_INTEGER);
        assertEquals("Long", GenConstants.TYPE_LONG);
        assertEquals("Double", GenConstants.TYPE_DOUBLE);
        assertEquals("BigDecimal", GenConstants.TYPE_BIGDECIMAL);
        assertEquals("Date", GenConstants.TYPE_DATE);
        assertEquals("LIKE", GenConstants.QUERY_LIKE);
        assertEquals("EQ", GenConstants.QUERY_EQ);
        assertEquals("1", GenConstants.REQUIRE);
    }
}
