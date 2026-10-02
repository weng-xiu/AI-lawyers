package ai.lawyers.web.controller.lawyers.skill;

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
import ai.lawyers.system.service.lawyers.skill.IAiSkillGroupService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiSkillGroupController} 权限校验——逐端点验证未登录 401、权限码不符 403、
 * 持对应权限码放行 200。详情端点对 mock 返回的 null 分组立即 setMembers 会 NPE，
 * 不做 200 断言。
 */
public class AiSkillGroupControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiSkillGroupService aiSkillGroupService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiSkillGroupService.selectAiSkillGroupList(any())).thenReturn(new ArrayList<>());
        startMvc(AiSkillGroupController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiSkillGroupService", aiSkillGroupService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/skill/group";
        endpoints.add(new Endpoint("lawyers:skillGroup:query", get(base + "/list")));
        endpoints.add(new Endpoint("lawyers:skillGroup:query", get(base + "/listEnabled")));
        endpoints.add(new Endpoint("lawyers:skillGroup:export", get(base + "/export")));
        // getInfo 对 mock 返回的 null 分组调 setMembers 会 NPE，跳过 200
        endpoints.add(new Endpoint("lawyers:skillGroup:query", get(base + "/1"), false));
        endpoints.add(new Endpoint("lawyers:skillGroup:add",
                post(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:skillGroup:edit",
                put(base).contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:skillGroup:remove", delete(base + "/1")));
        endpoints.add(new Endpoint("lawyers:skillGroup:query", get(base + "/members/1")));
        endpoints.add(new Endpoint("lawyers:skillGroup:edit",
                post(base + "/members/add").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:skillGroup:edit",
                post(base + "/members/remove").contentType(MediaType.APPLICATION_JSON).content("{}")));
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
            if (!endpoint.expect200)
            {
                continue;
            }
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().isOk());
        }
    }

    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        private final boolean expect200;

        Endpoint(String permission, MockHttpServletRequestBuilder builder)
        {
            this(permission, builder, true);
        }

        Endpoint(String permission, MockHttpServletRequestBuilder builder, boolean expect200)
        {
            this.permission = permission;
            this.builder = builder;
            this.expect200 = expect200;
        }
    }
}
