package ai.lawyers.common.core.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.core.domain.BaseEntity;

/**
 * 字典类型实体测试：字段读写、继承基础实体、校验注解与字符串输出。
 *
 * @author ai-lawyers
 */
class SysDictTypeTest
{
    @Test
    void 默认字段为空且继承基础实体()
    {
        SysDictType dictType = new SysDictType();

        assertTrue(dictType instanceof BaseEntity);
        assertNull(dictType.getDictId());
        assertNull(dictType.getDictName());
        assertNull(dictType.getDictType());
        assertNull(dictType.getStatus());
    }

    @Test
    void 可写入字典字段()
    {
        SysDictType dictType = new SysDictType();
        dictType.setDictId(1L);
        dictType.setDictName("用户性别");
        dictType.setDictType("sys_user_sex");
        dictType.setStatus("0");

        assertEquals(1L, dictType.getDictId());
        assertEquals("用户性别", dictType.getDictName());
        assertEquals("sys_user_sex", dictType.getDictType());
        assertEquals("0", dictType.getStatus());
    }

    @Test
    void 字典类型与名称带校验注解() throws Exception
    {
        Method dictTypeGetter = SysDictType.class.getMethod("getDictType");
        Method dictNameGetter = SysDictType.class.getMethod("getDictName");

        assertEquals("^[a-z][a-z0-9_]*$", dictTypeGetter.getAnnotation(Pattern.class).regexp());
        assertEquals("字典类型不能为空", dictTypeGetter.getAnnotation(NotBlank.class).message());
        assertEquals("字典名称不能为空", dictNameGetter.getAnnotation(NotBlank.class).message());
    }

    @Test
    void toString包含关键字段()
    {
        SysDictType dictType = new SysDictType();
        dictType.setDictId(2L);
        dictType.setDictName("字典");
        dictType.setDictType("demo_type");

        String text = dictType.toString();

        assertTrue(text.contains("dictId=2"));
        assertTrue(text.contains("dictName=字典"));
        assertTrue(text.contains("dictType=demo_type"));
    }
}
