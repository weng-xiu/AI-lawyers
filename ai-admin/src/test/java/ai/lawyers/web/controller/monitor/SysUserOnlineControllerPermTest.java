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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.service.ISysUserOnlineService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SysUserOnlineController} 权限校验——在线用户端点依赖真实 Redis 会话数据，
 * 这里仅验证未登录 401。
 */
public class SysUserOnlineControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysUserOnlineService userOnlineService;

    @Mock
    private RedisCache redisCache;

    private final List<MockHttpServletRequestBuilder> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(SysUserOnlineController.class);
        endpoints.add(get("/monitor/online/list"));
        endpoints.add(delete("/monitor/online/token-1"));
    }

    @Override
    protected void registerMocks()
    {
        registerMock("userOnlineService", userOnlineService);
        registerMock("redisCache", redisCache);
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
