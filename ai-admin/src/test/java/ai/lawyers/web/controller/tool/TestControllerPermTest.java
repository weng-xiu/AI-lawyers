package ai.lawyers.web.controller.tool;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link TestController} 权限校验——swagger 测试端点，全部方法均无 @PreAuthorize，
 * 仅要求登录态。仅验证未登录 401、登录（任意权限）后 200，不验证 403。
 */
public class TestControllerPermTest extends AbstractPermMvcTest
{
    private final List<MockHttpServletRequestBuilder> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        startMvc(TestController.class);
        buildEndpoints();
    }

    private void buildEndpoints()
    {
        String base = "/test/user";
        endpoints.add(get(base + "/list"));
        endpoints.add(get(base + "/1"));
        endpoints.add(post(base + "/save").param("userId", "3"));
        endpoints.add(put(base + "/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"username\":\"admin\"}"));
        endpoints.add(delete(base + "/1"));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : endpoints)
        {
            mockMvc.perform(endpoint).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void allEndpoints_whenLoggedIn_200() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : endpoints)
        {
            mockMvc.perform(endpoint.with(loginAs()))
                    .andExpect(status().isOk());
        }
    }
}
