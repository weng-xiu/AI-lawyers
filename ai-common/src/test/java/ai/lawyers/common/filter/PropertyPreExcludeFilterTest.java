package ai.lawyers.common.filter;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;

/**
 * JSON 属性排除过滤器测试：排除字段登记与链式调用。
 *
 * @author ai-lawyers
 */
class PropertyPreExcludeFilterTest
{
    @Test
    void 默认构造_无排除字段()
    {
        assertTrue(new PropertyPreExcludeFilter().getExcludes().isEmpty());
    }

    @Test
    void addExcludes_登记多个字段并返回自身()
    {
        PropertyPreExcludeFilter filter = new PropertyPreExcludeFilter();

        assertSame(filter, filter.addExcludes("password", "salt"));
        assertTrue(filter.getExcludes().contains("password"));
        assertTrue(filter.getExcludes().contains("salt"));
        // 重复添加同一字段不会报错
        filter.addExcludes("password");
        assertTrue(filter.getExcludes().contains("password"));
    }

    @Test
    void addExcludes_参与序列化时排除字段()
    {
        PropertyPreExcludeFilter filter = new PropertyPreExcludeFilter().addExcludes("password");

        Sample sample = new Sample();
        sample.setName("张三");
        sample.setPassword("secret");

        String json = JSON.toJSONString(sample, filter);

        assertTrue(json.contains("\"张三\""));
        assertTrue(!json.contains("secret"));
    }

    /**
     * 序列化样例对象。
     */
    public static class Sample
    {
        private String name;

        private String password;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getPassword()
        {
            return password;
        }

        public void setPassword(String password)
        {
            this.password = password;
        }
    }
}
