package ai.lawyers.web.controller.lawyers.trunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.service.lawyers.trunk.IAiCallTrunkService;
import ai.lawyers.system.service.lawyers.trunk.ICarrierRouteService;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiCallTrunkController} 权限校验——15 个端点逐端点验证未登录 401、
 * 权限码不符 403、持对应权限码放行 200。连通测试/拨测端点解引用返回值，
 * 分别 stub 为非空的 {@link GatewayHealth}、{@link DialResult} 以稳定 200 断言。
 */
public class AiCallTrunkControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallTrunkService aiCallTrunkService;

    @Mock
    private ICarrierRouteService carrierRouteService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiCallTrunkService.selectAiCallTrunkList(any())).thenReturn(new ArrayList<>());
        when(aiCallTrunkService.selectAiNumberSegmentList(any())).thenReturn(new ArrayList<>());
        when(aiCallTrunkService.testTrunk(anyLong())).thenReturn(GatewayHealth.up(5));
        when(aiCallTrunkService.testCall(anyLong(), anyString()))
                .thenReturn(DialResult.fail("TEST_ERR", "拨测失败"));
        when(carrierRouteService.selectCandidateTrunks(anyString(), any()))
                .thenReturn(new ArrayList<>());
        startMvc(AiCallTrunkController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiCallTrunkService", aiCallTrunkService);
        registerMock("carrierRouteService", carrierRouteService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/trunk";
        endpoints.add(new Endpoint("lawyers:trunk:list", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:trunk:export",
                post(base + "/export").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:query", get(base + "/1")));
        endpoints.add(new Endpoint("lawyers:trunk:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:edit",
                put(base + "/changeStatus").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:trunk:test",
                post(base + "/test/1").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:test",
                post(base + "/testCall/1").param("calleeNumber", "13800000000")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:list", get(base + "/segment/list")));
        endpoints.add(new Endpoint("lawyers:trunk:add",
                post(base + "/segment").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:edit",
                put(base + "/segment").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:remove", delete(base + "/segment/1")));
        endpoints.add(new Endpoint("lawyers:trunk:query",
                get(base + "/recognize").param("number", "13800000000")));
        endpoints.add(new Endpoint("lawyers:trunk:edit",
                post(base + "/segment/refresh").contentType(MediaType.APPLICATION_JSON).content("{}")));
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
