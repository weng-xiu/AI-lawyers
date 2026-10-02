package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiHotspotSuppressController} 权限校验——6 个端点、5 个权限边界
 * （热点规则与处置日志两个列表共用 list 权限），逐端点验证未登录 401、
 * 权限码不符 403、持对应权限码放行 200。
 */
public class AiHotspotSuppressControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiHotspotSuppressService hotspotSuppressService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(hotspotSuppressService.selectSuppressList(any())).thenReturn(new ArrayList<>());
        when(hotspotSuppressService.selectLogList(any())).thenReturn(new ArrayList<>());
        startMvc(AiHotspotSuppressController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("hotspotSuppressService", hotspotSuppressService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/hotspot";
        endpoints.add(new Endpoint("lawyers:hotspot:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:hotspot:list", get(base + "/log/list")));
        endpoints.add(new Endpoint("lawyers:hotspot:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:hotspot:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:hotspot:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:hotspot:remove", delete(base + "/1")));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void allEndpoints_wrongPermission_403() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs("lawyers:nope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allEndpoints_withCorrectPermission_200() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().isOk());
        }
    }

    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        Endpoint(String permission, MockHttpServletRequestBuilder builder)
        {
            this.permission = permission;
            this.builder = builder;
        }
    }
}
