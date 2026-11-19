package ai.lawyers.web.controller.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.SysCache;

/**
 * 缓存监控接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class CacheControllerTest
{
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private CacheController controller;

    @Test
    void 缓存概览汇总信息与命令统计() throws Exception
    {
        Properties info = new Properties();
        info.setProperty("redis_version", "7.0.0");
        Properties commandStats = new Properties();
        commandStats.setProperty("cmdstat_get", "calls=12,usec=34,usec_per_call=2.83");
        doReturn(info).doReturn(commandStats).doReturn(9L)
                .when(redisTemplate).execute(any(RedisCallback.class));

        AjaxResult result = controller.getInfo();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
        assertSame(info, data.get("info"));
        assertEquals(9L, data.get("dbSize"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> pieList = (List<Map<String, String>>) data.get("commandStats");
        assertEquals(1, pieList.size());
        assertEquals("get", pieList.get(0).get("name"));
        assertEquals("12", pieList.get(0).get("value"));
    }

    @Test
    void 缓存名称返回内置列表()
    {
        AjaxResult result = controller.cache();

        @SuppressWarnings("unchecked")
        List<SysCache> caches = (List<SysCache>) result.get(AjaxResult.DATA_TAG);
        assertTrue(caches.size() >= 7);
        assertTrue(caches.stream().anyMatch(cache -> "用户信息".equals(cache.getRemark())));
    }

    @Test
    void 按前缀查询键并排序()
    {
        when(redisTemplate.keys("login_tokens:*"))
                .thenReturn(new java.util.HashSet<String>(Arrays.asList("login_tokens:b", "login_tokens:a")));

        AjaxResult result = controller.getCacheKeys("login_tokens:");

        assertEquals(new TreeSet<String>(Arrays.asList("login_tokens:a", "login_tokens:b")),
                result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 查询缓存值封装为SysCache()
    {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_tokens:1")).thenReturn("payload");

        AjaxResult result = controller.getCacheValue("login_tokens:", "login_tokens:1");

        SysCache cache = (SysCache) result.get(AjaxResult.DATA_TAG);
        assertEquals("login_tokens", cache.getCacheName());
        assertEquals("1", cache.getCacheKey());
        assertEquals("payload", cache.getCacheValue());
    }

    @Test
    void 清空缓存名称下全部键()
    {
        Set<String> keys = new HashSet<String>(Arrays.asList("sys_config:a", "sys_config:b"));
        when(redisTemplate.keys("sys_config:*")).thenReturn(keys);

        AjaxResult result = controller.clearCacheName("sys_config:");

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(redisTemplate).delete(keys);
    }

    @Test
    void 清空单个键与全部缓存()
    {
        Set<String> keys = new HashSet<String>(Arrays.asList("a", "b"));
        when(redisTemplate.keys("*")).thenReturn(keys);

        controller.clearCacheKey("a");
        controller.clearCacheAll();

        verify(redisTemplate).delete("a");
        verify(redisTemplate).delete(keys);
        verify(redisTemplate, org.mockito.Mockito.atLeastOnce()).keys(anyString());
    }
}
