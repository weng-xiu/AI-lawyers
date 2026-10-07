package ai.lawyers.web.controller.lawyers.sms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link SmsUpstreamController} 权限校验——唯一端点 {@code POST /lawyers/sms/upstream}
 * 为 {@code @Anonymous} 匿名端点（短信服务商上行回调）。迷你容器不处理 @Anonymous，
 * 匿名访问仍被 URL 层拦截 401，故仅验证未登录 401 与任意登录用户 200。
 *
 * <p>200 用例携带非退订内容（content=hello），在未配置签名/token 的默认分支直接放行，
 * 不触碰黑名单 Service 与 Redis，无需注册任何 mock。</p>
 */
public class SmsUpstreamControllerPermTest extends AbstractPermMvcTest
{
    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(SmsUpstreamController.class);
    }

    @Test
    void upstream_unauthenticated_401() throws Exception
    {
        mockMvc.perform(post("/lawyers/sms/upstream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone_number\":\"13800138000\",\"content\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upstream_loggedIn_200() throws Exception
    {
        mockMvc.perform(post("/lawyers/sms/upstream")
                        .with(loginAs("lawyers:nope"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone_number\":\"13800138000\",\"content\":\"hello\"}"))
                .andExpect(status().isOk());
    }
}
