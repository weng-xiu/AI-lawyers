package ai.lawyers.common.core.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 字典数据实体测试：默认项判定与字段读写。
 *
 * @author ai-lawyers
 */
class SysDictDataTest
{
    @Test
    void getDefault_仅Y视为默认()
    {
        SysDictData data = new SysDictData();

        data.setIsDefault("Y");
        assertTrue(data.getDefault());

        data.setIsDefault("N");
        assertFalse(data.getDefault());

        data.setIsDefault(null);
        assertFalse(data.getDefault());
    }

    @Test
    void 字段读写()
    {
        SysDictData data = new SysDictData();
        data.setDictCode(1L);
        data.setDictSort(10L);
        data.setDictLabel("男");
        data.setDictValue("0");
        data.setDictType("sys_user_sex");
        data.setCssClass("primary");
        data.setListClass("default");
        data.setIsDefault("Y");
        data.setStatus("0");

        assertEquals(1L, data.getDictCode());
        assertEquals(10L, data.getDictSort());
        assertEquals("男", data.getDictLabel());
        assertEquals("0", data.getDictValue());
        assertEquals("sys_user_sex", data.getDictType());
        assertEquals("primary", data.getCssClass());
        assertEquals("default", data.getListClass());
        assertEquals("Y", data.getIsDefault());
        assertEquals("0", data.getStatus());
    }

    @Test
    void toString包含字典标签()
    {
        SysDictData data = new SysDictData();
        data.setDictLabel("女");

        assertTrue(data.toString().contains("女"));
    }
}
