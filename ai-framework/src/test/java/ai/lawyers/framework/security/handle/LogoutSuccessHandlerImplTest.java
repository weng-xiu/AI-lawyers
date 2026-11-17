package ai.lawyers.framework.security.handle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.MessageUtils;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.framework.manager.AsyncManager;
import ai.lawyers.framework.web.service.TokenService;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;

/**
 * 退出处理测试：未登录直接返回、登录用户签出坐席并清除缓存、坐席异常不影响退出。
 *
 * @author ai-lawyers
 */
class LogoutSuccessHandlerImplTest
{
    private LogoutSuccessHandlerImpl handler;

    private TokenService tokenService;

    private IAiCallAgentStatusService agentStatusService;

    private ConfigurableListableBeanFactory previousBeanFactory;

    private MockedStatic<MessageUtils> messageUtilsMock;

    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp()
    {
        previousBeanFactory = (ConfigurableListableBeanFactory) ReflectionTestUtils
                .getField(SpringUtils.class, "beanFactory");
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean("scheduledExecutorService")).thenReturn(mock(ScheduledExecutorService.class));
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beanFactory);
        // AsyncFactory 生成任务时需要从请求上下文读取客户端信息
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        messageUtilsMock = mockStatic(MessageUtils.class);
        messageUtilsMock.when(() -> MessageUtils.message("user.logout.success")).thenReturn("退出成功");

        handler = new LogoutSuccessHandlerImpl();
        tokenService = mock(TokenService.class);
        agentStatusService = mock(IAiCallAgentStatusService.class);
        ReflectionTestUtils.setField(handler, "tokenService", tokenService);
        ReflectionTestUtils.setField(handler, "aiCallAgentStatusService", agentStatusService);

        // 复用 AsyncManager 单例中已注入的调度线程池，避免真实线程执行
        executor = (ScheduledExecutorService) ReflectionTestUtils.getField(AsyncManager.me(), "executor");
        clearInvocations(executor);
    }

    @AfterEach
    void tearDown()
    {
        messageUtilsMock.close();
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", previousBeanFactory);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void onLogoutSuccess_未登录用户直接返回成功提示() throws Exception
    {
        when(tokenService.getLoginUser(any())).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onLogoutSuccess(new MockHttpServletRequest(), response, null);

        assertTrue(response.getContentAsString().contains("退出成功"));
        verifyNoInteractions(agentStatusService);
        verify(tokenService, never()).delLoginUser(any());
    }

    @Test
    void onLogoutSuccess_登录用户签出坐席并清理缓存和记录日志() throws Exception
    {
        LoginUser loginUser = loginUser(2L);
        when(tokenService.getLoginUser(any())).thenReturn(loginUser);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onLogoutSuccess(new MockHttpServletRequest(), response, null);

        verify(agentStatusService).agentLogout(null, 2L);
        verify(tokenService).delLoginUser("token-1");
        verify(executor).schedule(any(TimerTask.class), eq(10L), eq(TimeUnit.MILLISECONDS));
        assertTrue(response.getContentAsString().contains("退出成功"));
    }

    @Test
    void onLogoutSuccess_坐席签出异常不影响退出() throws Exception
    {
        LoginUser loginUser = loginUser(2L);
        when(tokenService.getLoginUser(any())).thenReturn(loginUser);
        doThrow(new IllegalStateException("down")).when(agentStatusService).agentLogout(null, 2L);

        handler.onLogoutSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), null);

        verify(tokenService).delLoginUser("token-1");
    }

    @Test
    void onLogoutSuccess_用户ID为空时跳过坐席签出() throws Exception
    {
        LoginUser loginUser = loginUser(null);
        when(tokenService.getLoginUser(any())).thenReturn(loginUser);

        handler.onLogoutSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), null);

        verifyNoInteractions(agentStatusService);
        verify(tokenService).delLoginUser("token-1");
    }

    @Test
    void onLogoutSuccess_未启用坐席服务时正常退出() throws Exception
    {
        ReflectionTestUtils.setField(handler, "aiCallAgentStatusService", null);
        LoginUser loginUser = loginUser(2L);
        when(tokenService.getLoginUser(any())).thenReturn(loginUser);

        handler.onLogoutSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), null);

        verify(tokenService).delLoginUser("token-1");
    }

    /** 构造登录用户，userId 可为空 */
    private LoginUser loginUser(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("lawyer");
        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        loginUser.setToken("token-1");
        return loginUser;
    }
}
