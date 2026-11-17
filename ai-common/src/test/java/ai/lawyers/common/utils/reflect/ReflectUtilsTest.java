package ai.lawyers.common.utils.reflect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

/**
 * 反射工具测试：多级 getter/setter、私有字段读写、缺失成员安全降级、
 * 按名称调用方法、反射异常转换、泛型参数解析。
 *
 * @author ai-lawyers
 */
class ReflectUtilsTest
{
    /** 嵌套结构，用于验证多级属性访问 */
    static class Demo
    {
        private String name = "demo";

        private int age = 3;

        private Inner inner = new Inner();

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public int getAge()
        {
            return age;
        }

        public Inner getInner()
        {
            return inner;
        }

        public String greet(String who)
        {
            return "hi " + who;
        }

        public String boom()
        {
            throw new IllegalStateException("boom");
        }
    }

    static class Inner
    {
        private String code = "c1";

        public String getCode()
        {
            return code;
        }

        public void setCode(String code)
        {
            this.code = code;
        }
    }

    static class StringList extends ArrayList<String>
    {
        private static final long serialVersionUID = 1L;
    }

    @Test
    void invokeGetter_支持多级属性()
    {
        Demo demo = new Demo();

        assertEquals("demo", ReflectUtils.<String>invokeGetter(demo, "name"));
        assertEquals("c1", ReflectUtils.<String>invokeGetter(demo, "inner.code"));
    }

    @Test
    void invokeSetter_支持多级属性()
    {
        Demo demo = new Demo();

        ReflectUtils.invokeSetter(demo, "name", "改名");
        ReflectUtils.invokeSetter(demo, "inner.code", "c2");

        assertEquals("改名", demo.getName());
        assertEquals("c2", demo.getInner().getCode());
    }

    @Test
    void getFieldValue与setFieldValue_绕过getter读写私有字段()
    {
        Demo demo = new Demo();

        assertEquals(3, ReflectUtils.<Integer>getFieldValue(demo, "age"));

        ReflectUtils.setFieldValue(demo, "age", 9);

        assertEquals(9, demo.getAge());
    }

    @Test
    void 缺失字段返回null且写入静默忽略()
    {
        Demo demo = new Demo();

        assertNull(ReflectUtils.getFieldValue(demo, "notExists"));
        assertDoesNotThrow(() -> ReflectUtils.setFieldValue(demo, "notExists", "x"));
    }

    @Test
    void invokeMethodByName_按参数值推断类型()
    {
        Demo demo = new Demo();

        assertEquals("hi 张三", ReflectUtils.<String>invokeMethodByName(demo, "greet", new Object[]{"张三"}));
    }

    @Test
    void invokeMethod_空对象或空方法名返回null()
    {
        Demo demo = new Demo();

        assertNull(ReflectUtils.invokeMethod(null, "greet", new Class<?>[]{String.class}, new Object[]{"x"}));
        assertNull(ReflectUtils.invokeMethod(demo, null, new Class<?>[0], new Object[0]));
        assertNull(ReflectUtils.invokeMethod(demo, "notExists", new Class<?>[0], new Object[0]));
    }

    @Test
    void invokeMethod_目标方法异常转为运行时异常()
    {
        Demo demo = new Demo();

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> ReflectUtils.invokeMethod(demo, "boom", new Class<?>[0], new Object[0]));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void getAccessibleMethodByName_参数个数不匹配返回null()
    {
        Demo demo = new Demo();

        Method matched = ReflectUtils.getAccessibleMethodByName(demo, "greet", 1);
        assertNotNull(matched);
        assertNull(ReflectUtils.getAccessibleMethodByName(demo, "greet", 2));
    }

    @Test
    void getClassGenricType_解析父类泛型()
    {
        assertEquals(String.class, ReflectUtils.getClassGenricType(StringList.class));
    }

    @Test
    void getUserClass_普通实例返回自身类型()
    {
        Demo demo = new Demo();

        assertEquals(Demo.class, ReflectUtils.getUserClass(demo));
    }
}
