package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Redis FastJson 序列化器测试：空值处理、白名单类型往返与字符集。
 *
 * @author ai-lawyers
 */
class FastJson2JsonRedisSerializerTest
{
    /** 位于 ai.lawyers 白名单包下的示例对象 */
    public static class DemoBean
    {
        public String name;

        public int age;

        public DemoBean()
        {
        }

        public DemoBean(String name, int age)
        {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void serialize与deserialize_空值返回空结果()
    {
        FastJson2JsonRedisSerializer<DemoBean> serializer = new FastJson2JsonRedisSerializer<>(DemoBean.class);

        assertArrayEquals(new byte[0], serializer.serialize(null));
        assertNull(serializer.deserialize(null));
        assertNull(serializer.deserialize(new byte[0]));
    }

    @Test
    void 白名单类型_序列化后可完整还原()
    {
        FastJson2JsonRedisSerializer<DemoBean> serializer = new FastJson2JsonRedisSerializer<>(DemoBean.class);

        DemoBean source = new DemoBean("张三", 18);
        byte[] bytes = serializer.serialize(source);
        String json = new String(bytes, StandardCharsets.UTF_8);
        // 开启 WriteClassName 后会写入 @type 便于反序列化还原类型
        assertTrue(json.contains("@type"), json);

        DemoBean target = serializer.deserialize(bytes);
        assertEquals("张三", target.name);
        assertEquals(18, target.age);
    }

    @Test
    void 字符串类型_不带类型标记同样可还原()
    {
        FastJson2JsonRedisSerializer<String> serializer = new FastJson2JsonRedisSerializer<>(String.class);

        byte[] bytes = serializer.serialize("hello");
        assertEquals("\"hello\"", new String(bytes, StandardCharsets.UTF_8));
        assertEquals("hello", serializer.deserialize(bytes));
    }

    @Test
    void 默认字符集为UTF8()
    {
        assertEquals(StandardCharsets.UTF_8, FastJson2JsonRedisSerializer.DEFAULT_CHARSET);
    }
}
