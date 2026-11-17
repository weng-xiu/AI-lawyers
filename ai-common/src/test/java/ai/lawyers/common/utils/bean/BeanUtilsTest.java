package ai.lawyers.common.utils.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Bean 工具测试：属性复制、getter/setter 识别与属性名比较。
 *
 * @author ai-lawyers
 */
class BeanUtilsTest
{
    @Test
    void copyBeanProp_复制同名字段()
    {
        DemoBean source = new DemoBean();
        source.setName("张三");
        source.setAge(30);

        DemoBean target = new DemoBean();
        BeanUtils.copyBeanProp(target, source);

        assertEquals("张三", target.getName());
        assertEquals(30, target.getAge());
    }

    @Test
    void getSetterMethods_仅返回单参数的setter()
    {
        List<Method> setters = BeanUtils.getSetterMethods(new DemoBean());

        assertEquals(2, setters.size());
        assertTrue(hasMethod(setters, "setName"));
        assertTrue(hasMethod(setters, "setAge"));
    }

    @Test
    void getGetterMethods_包含属性getter()
    {
        List<Method> getters = BeanUtils.getGetterMethods(new DemoBean());

        assertTrue(hasMethod(getters, "getName"));
        assertTrue(hasMethod(getters, "getAge"));
        // 非 getter 命名的方法不会被识别
        assertFalse(hasMethod(getters, "setName"));
    }

    @Test
    void isMethodPropEquals_按属性名比较()
    {
        assertTrue(BeanUtils.isMethodPropEquals("getName", "setName"));
        assertFalse(BeanUtils.isMethodPropEquals("getName", "setAge"));
    }

    /** 判断方法列表中是否存在指定名称的方法 */
    private boolean hasMethod(List<Method> methods, String name)
    {
        for (Method method : methods)
        {
            if (name.equals(method.getName()))
            {
                assertNotNull(method);
                return true;
            }
        }
        return false;
    }

    /** 测试用简单 Bean */
    public static class DemoBean
    {
        private String name;

        private int age;

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

        public void setAge(int age)
        {
            this.age = age;
        }
    }
}
