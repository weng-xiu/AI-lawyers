package ai.lawyers.web.controller.lawyers.sms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsLogMapper;
import ai.lawyers.system.service.lawyers.sms.ISmsService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiSmsLogController} 权限校验——共 3 个端点：list 带 {@code lawyers:smsLog:view}
 * 权限码，走 401/403/200 三段式；调试发送、删除 2 个端点无 {@code @PreAuthorize}
 * （仅登录即可），只验证未登录 401 与任意登录用户 200。调试发送解引用
 * {@link SmsResult}，stub 为非空失败结果以稳定 200 断言。
 */
public class AiSmsLogControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private AiSmsLogMapper aiSmsLogMapper;

    @Mock
    private ISmsService smsService;

    private final List<Endpoint> protectedEndpoints = new ArrayList<>();

    private final List<MockHttpServletRequestBuilder> loginOnlyEndpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiSmsLogMapper.selectAiSmsLogList(any())).thenReturn(new ArrayList<>());
        when(smsService.send(any(), any(), any(), any(), any()))
                .thenReturn(SmsResult.fail("发送失败"));
        startMvc(AiSmsLogController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiSmsLogMapper", aiSmsLogMapper);
        registerMock("smsService", smsService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/sms/log";
        protectedEndpoints.add(new Endpoint("lawyers:smsLog:view", get(base + "/list")));
        loginOnlyEndpoints.add(
                post(base + "/send").contentType(MediaType.APPLICATION_JSON).content("{}"));
        loginOnlyEndpoints.add(delete(base + "/1"));
    }

    @Test
    void protectedEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : protectedEndpoints)
        {
            mockMvc.perform(endpoint.builder).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void protectedEndpoints_wrongPermission_403() throws Exception
    {
        for (Endpoint endpoint : protectedEndpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs("lawyers:nope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void protectedEndpoints_withCorrectPermission_200() throws Exception
    {
        for (Endpoint endpoint : protectedEndpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void loginOnlyEndpoints_unauthenticated_401() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : loginOnlyEndpoints)
        {
            mockMvc.perform(endpoint).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void loginOnlyEndpoints_anyLogin_200() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : loginOnlyEndpoints)
        {
            mockMvc.perform(endpoint.with(loginAs("lawyers:nope")))
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
