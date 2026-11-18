package ai.lawyers.web.controller.system;

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
 * 注册接口测试：注册开关判断与注册结果返回。
 *
 * @author ai-lawyers
 */
class SysRegisterControllerTest
{
    private static final String REGISTER_SWITCH = "sys.account.registerUser";

    private SysRegisterController controller;

    private ISysConfigService configService;

    private SysRegisterService registerService;

    @BeforeEach
    void setUp()
    {
        configService = mock(ISysConfigService.class);
        registerService = mock(SysRegisterService.class);
        controller = new SysRegisterController();
        ReflectionTestUtils.setField(controller, "configService", configService);
        ReflectionTestUtils.setField(controller, "registerService", registerService);
    }

    @Test
    void 注册功能关闭时拒绝注册()
    {
        when(configService.selectConfigByKey(REGISTER_SWITCH)).thenReturn("false");

        AjaxResult result = controller.register(newBody());

        assertTrue(result.isError());
        assertEquals("当前系统没有开启注册功能！", result.get(AjaxResult.MSG_TAG));
        verify(registerService, never()).register(any());
    }

    @Test
    void 注册成功返回成功结果()
    {
        when(configService.selectConfigByKey(REGISTER_SWITCH)).thenReturn("true");
        when(registerService.register(any(RegisterBody.class))).thenReturn("");

        AjaxResult result = controller.register(newBody());

        assertTrue(result.isSuccess());
        verify(registerService).register(any(RegisterBody.class));
    }

    @Test
    void 注册失败返回失败消息()
    {
        when(configService.selectConfigByKey(REGISTER_SWITCH)).thenReturn("true");
        when(registerService.register(any(RegisterBody.class))).thenReturn("用户名已存在");

        AjaxResult result = controller.register(newBody());

        assertTrue(result.isError());
        assertEquals("用户名已存在", result.get(AjaxResult.MSG_TAG));
    }

    private RegisterBody newBody()
    {
        RegisterBody body = new RegisterBody();
        body.setUsername("zhangsan");
        body.setPassword("123456");
        body.setNickName("张三");
        return body;
    }
}
