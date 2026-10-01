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
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.summary.IAiCallSummaryService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiCallRecordController} 权限校验——从注解抽取全部 13 个端点，
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行。
 *
 * <p>两个特例：play/download 授权通过但无话单数据时返回 404（证明授权已通过）；
 * /workbench 无 @PreAuthorize，任何登录用户可访问（仍需登录）。</p>
 */
public class AiCallRecordControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallRecordService recordService;

    @Mock
    private IAiCallSummaryService summaryService;

    /** 需权限码的端点规格。 */
    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(recordService.selectAiCallRecordList(any())).thenReturn(new ArrayList<>());
        startMvc(AiCallRecordController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiCallRecordService", recordService);
        registerMock("aiCallSummaryService", summaryService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/call/record";
        endpoints.add(new Endpoint("lawyers:call:record:list", get(base + "/list"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:export", post(base + "/export"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:query", get(base + "/1"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:remove", delete(base + "/1"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:list", get(base + "/agent/9"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:statistics",
                get(base + "/statistics"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:statistics",
                get(base + "/statistics/agent"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:statistics",
                get(base + "/statistics/category"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:statistics",
                get(base + "/statistics/date"), 200));
        endpoints.add(new Endpoint("lawyers:call:record:edit",
                post(base + "/1/ai-summary"), 200));
        // 授权通过后因 mock 查无话单 → 404，404 即证明 @PreAuthorize 已放行
        endpoints.add(new Endpoint("lawyers:call:record:query",
                get(base + "/1/play"), 404));
        endpoints.add(new Endpoint("lawyers:call:record:query",
                get(base + "/1/download"), 404));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder)
                    .andExpect(status().isUnauthorized());
        }
        // workbench 无方法注解，但 URL 层 anyRequest authenticated 仍要求登录
        mockMvc.perform(get("/lawyers/call/record/workbench"))
                .andExpect(status().isUnauthorized());
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
    void allEndpoints_withCorrectPermission_reachesController() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().is(endpoint.expectedStatus));
        }
    }

    @Test
    void workbench_anyLoggedInUser_200() throws Exception
    {
        // 无 @PreAuthorize：持任意权限码（或空权限集）的登录用户均可访问
        mockMvc.perform(get("/lawyers/call/record/workbench")
                        .with(loginAs("lawyers:unrelated:perm")))
                .andExpect(status().isOk());
    }

    /** 端点规格：方法+路径、要求权限码、授权通过后的期望状态。 */
    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        private final int expectedStatus;

        Endpoint(String permission, MockHttpServletRequestBuilder builder, int expectedStatus)
        {
            this.permission = permission;
            this.builder = builder;
            this.expectedStatus = expectedStatus;
        }
    }
}
