package ai.lawyers.admin.controller.lawyers;

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
import ai.lawyers.system.service.lawyers.IAiUserConsultationService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiUserConsultationController} 权限校验——类内方法均无 @PreAuthorize，
 * 仅要求登录态（@Anonymous 的 /categories 为公开目录，跳过）。
 * 故只验证未登录 401、登录（任意权限）后 200，不验证 403。
 */
public class AiUserConsultationControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiUserConsultationService aiUserConsultationService;

    private final List<MockHttpServletRequestBuilder> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(aiUserConsultationService.selectAiUserConsultationList(any())).thenReturn(new ArrayList<>());
        startMvc(AiUserConsultationController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiUserConsultationService", aiUserConsultationService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/userConsultation";
        // 非 multipart 请求进入 submit 后会被 catch 兜底为 200，用于稳定验证鉴权边界
        endpoints.add(post(base + "/submit").contentType(MediaType.APPLICATION_JSON).content("{}"));
        endpoints.add(get(base + "/result/1"));
        endpoints.add(get(base + "/info/1"));
        endpoints.add(get(base + "/history"));
        endpoints.add(post(base + "/evaluation").contentType(MediaType.APPLICATION_JSON).content("{}"));
        endpoints.add(delete(base + "/1"));
        // /categories 标注 @Anonymous 公开访问，不在鉴权断言范围
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
