package ai.lawyers.web.controller.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.model.RegisterBody;
import ai.lawyers.framework.web.service.SysRegisterService;
import ai.lawyers.system.service.ISysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 用户端用户接口测试：用户类型与注册开关。
 *
 * @author ai-lawyers
 */
class AiUserControllerTest
{
    private static final String REGISTER_SWITCH = "sys.account.registerUser";

    private AiUserController controller;

    private SysRegisterService registerService;

    private ISysConfigService configService;

    @BeforeEach
    void setUp()
    {
        registerService = mock(SysRegisterService.class);
        configService = mock(ISysConfigService.class);
        controller = new AiUserController();
        ReflectionTestUtils.setField(controller, "registerService", registerService);
        ReflectionTestUtils.setField(controller, "configService", configService);
    }

    @Test
    void 获取用户类型返回user()
    {
        AjaxResult result = controller.getUserType();

        assertEquals("user", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 注册开关关闭时拒绝注册()
    {
        when(configService.selectConfigByKey(REGISTER_SWITCH)).thenReturn("false");

        AjaxResult result = controller.register(new RegisterBody());

        assertTrue(result.isError());
        assertEquals("当前系统没有开启注册功能", result.get(AjaxResult.MSG_TAG));
        verify(registerService, never()).register(any());
    }

    @Test
    void 注册成功返回成功结果()
    {
        when(configService.selectConfigByKey(REGISTER_SWITCH)).thenReturn("true");
        when(registerService.register(any(RegisterBody.class))).thenReturn("");

        AjaxResult result = controller.register(new RegisterBody());

        assertTrue(result.isSuccess());
    }

    @Test
    void 注册失败返回失败原因()
    {
        when(configService.selectConfigByKey(REGISTER_SWITCH)).thenReturn("true");
        when(registerService.register(any(RegisterBody.class))).thenReturn("手机号已被注册");

        AjaxResult result = controller.register(new RegisterBody());

        assertTrue(result.isError());
        assertEquals("手机号已被注册", result.get(AjaxResult.MSG_TAG));
    }
}
