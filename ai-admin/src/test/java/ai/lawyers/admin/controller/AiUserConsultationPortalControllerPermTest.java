package ai.lawyers.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import ai.lawyers.system.service.lawyers.IAiUserConsultationService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiUserConsultationPortalController} 权限校验——公众端 5 个端点均无
 * @PreAuthorize，但公众端安全链要求必须登录（Service 内按登录态 userId 做归属）。
 * 仅验证未登录 401、登录（任意权限）后 200，不验证 403。
 */
public class AiUserConsultationPortalControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiUserConsultationService consultationService;

    private final java.util.List<MockHttpServletRequestBuilder> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(consultationService.selectAiUserConsultationList(any())).thenReturn(new ArrayList<>());
        startMvc(AiUserConsultationPortalController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        // 迷你容器非 Spring Boot 上下文，缺少 MultipartAutoConfiguration 注册的 multipartResolver，
        // 补注册标准解析器，使 multipart 请求在 DispatcherServlet 阶段被包装成
        // MultipartHttpServletRequest（submit 方法体内据此强转并取参）
        registerMock("multipartResolver", new StandardServletMultipartResolver());
        registerMock("consultationService", consultationService);
    }

    private void buildEndpoints()
    {
        String base = "/aiuser/consultation";
        // submit 生产签名为 multipart；这里用普通 POST + multipart/form-data 内容类型而非 MockMvc
        // 的 multipart()：后者在过滤器前即生成 MultipartHttpServletRequest，会被 Security 提前
        // 包成其私有包装类型、DispatcherServlet 又短路不再包装，导致方法体强转失败。
        // 普通 POST 走标准解析路径（无 part 时解析为空），mock Service 后正常返回 200。
        endpoints.add(post(base + "/submit").contentType("multipart/form-data"));
        endpoints.add(get(base + "/result/1"));
        endpoints.add(get(base + "/info/1"));
        endpoints.add(get(base + "/history"));
        endpoints.add(post(base + "/evaluation")
                .contentType("application/json").content("{}"));
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
