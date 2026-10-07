package ai.lawyers.web.controller.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ai.lawyers.framework.web.service.SysRegisterService;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysUserService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AiUserController} 权限校验——/register 标注 @Anonymous 属公开注册接口
 * （跳过）；/user/type 无 @PreAuthorize，仅要求登录态。
 * 仅验证未登录 401、登录（任意权限）后 200，不验证 403。
 */
public class AiUserControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ISysUserService userService;

    @Mock
    private SysRegisterService registerService;

    @Mock
    private ISysConfigService configService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AiUserController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("userService", userService);
        registerMock("registerService", registerService);
        registerMock("configService", configService);
    }

    @Test
    void getUserType_unauthenticated_401() throws Exception
    {
        mockMvc.perform(get("/user/type")).andExpect(status().isUnauthorized());
    }

    @Test
    void getUserType_whenLoggedIn_200() throws Exception
    {
        mockMvc.perform(get("/user/type").with(loginAs()))
                .andExpect(status().isOk());
    }
}
