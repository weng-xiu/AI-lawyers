package ai.lawyers.web.controller.monitor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link CacheController} 权限校验——缓存监控端点依赖真实 Redis 数据，
 * 这里仅验证未登录 401（含清缓存写操作）。
 */
public class CacheControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private final List<MockHttpServletRequestBuilder> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(CacheController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("redisTemplate", redisTemplate);
    }

    private void buildEndpoints()
    {
        String base = "/monitor/cache";
        endpoints.add(get(base));
        endpoints.add(get(base + "/getNames"));
        endpoints.add(get(base + "/getKeys/sys-config"));
        endpoints.add(get(base + "/getValue/sys-config/test-key"));
        endpoints.add(delete(base + "/clearCacheName/sys-config"));
        endpoints.add(delete(base + "/clearCacheKey/test-key"));
        endpoints.add(delete(base + "/clearCacheAll"));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : endpoints)
        {
            mockMvc.perform(endpoint).andExpect(status().isUnauthorized());
        }
    }
}
