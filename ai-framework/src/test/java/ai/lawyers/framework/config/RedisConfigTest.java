package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置测试：模板序列化器与限流脚本。
 *
 * @author ai-lawyers
 */
class RedisConfigTest
{
    private final RedisConfig config = new RedisConfig();

    @Test
    void redisTemplate_设置连接工厂与序列化器()
    {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        RedisTemplate<Object, Object> template = config.redisTemplate(factory);

        assertSame(factory, template.getConnectionFactory());
        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer());
        assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer());
        assertInstanceOf(FastJson2JsonRedisSerializer.class, template.getValueSerializer());
        assertInstanceOf(FastJson2JsonRedisSerializer.class, template.getHashValueSerializer());
    }

    @Test
    void limitScript_返回Long脚本()
    {
        DefaultRedisScript<Long> script = config.limitScript();

        assertNotNull(script);
        assertEquals(Long.class, script.getResultType());

        String text = script.getScriptAsString();
        assertNotNull(text);
        assertTrue(text.contains("redis.call('incr', key)"));
        assertTrue(text.contains("redis.call('expire', key, time)"));
    }
}
