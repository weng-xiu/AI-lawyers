package ai.lawyers.framework.manager.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.TimerTask;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.domain.SysLogininfor;
import ai.lawyers.system.domain.SysOperLog;
import ai.lawyers.system.service.ISysLogininforService;
import ai.lawyers.system.service.ISysOperLogService;

/**
 * 异步工厂测试：登录日志与操作日志任务的字段封装、状态映射及服务落库调用。
 *
 * <p>IP 固定为 127.0.0.1，地址解析走内网分支，不发起外网请求。</p>
 *
 * @author ai-lawyers
 */
class AsyncFactoryTest
{
    private ISysLogininforService logininforService;

    private ISysOperLogService operLogService;

    private Field beanFactoryField;

    private Object originalBeanFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        logininforService = mock(ISysLogininforService.class);
        operLogService = mock(ISysOperLogService.class);
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean(ISysLogininforService.class)).thenReturn(logininforService);
        when(beanFactory.getBean(ISysOperLogService.class)).thenReturn(operLogService);

        beanFactoryField = SpringUtils.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        originalBeanFactory = beanFactoryField.get(null);
        beanFactoryField.set(null, beanFactory);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        RequestContextHolder.resetRequestAttributes();
        beanFactoryField.set(null, originalBeanFactory);
    }

    @Test
    void recordLogininfor登录成功写入成功状态并保存()
    {
        TimerTask task = AsyncFactory.recordLogininfor("admin", Constants.LOGIN_SUCCESS, "登录成功");

        task.run();

        verify(logininforService).insertLogininfor(argThat(log ->
                "admin".equals(log.getUserName())
                        && Constants.SUCCESS.equals(log.getStatus())
                        && "127.0.0.1".equals(log.getIpaddr())
                        && "内网IP".equals(log.getLoginLocation())
                        && "登录成功".equals(log.getMsg())
                        && log.getBrowser() != null
                        && log.getOs() != null));
    }

    @Test
    void recordLogininfor登录失败映射为失败状态()
    {
        TimerTask task = AsyncFactory.recordLogininfor("admin", Constants.LOGIN_FAIL, "密码错误");

        task.run();

        verify(logininforService).insertLogininfor(argThat(log ->
                Constants.FAIL.equals(log.getStatus()) && "密码错误".equals(log.getMsg())));
    }

    @Test
    void recordLogininfor未知状态不设置状态字段()
    {
        TimerTask task = AsyncFactory.recordLogininfor("admin", "Unknown", "其他");

        task.run();

        verify(logininforService).insertLogininfor(argThat(log ->
                log.getStatus() == null
                        && "admin".equals(log.getUserName())
                        && "其他".equals(log.getMsg())));
    }

    @Test
    void recordOper回填操作地点并保存()
    {
        SysOperLog operLog = new SysOperLog();
        operLog.setOperIp("127.0.0.1");
        operLog.setTitle("用户管理");

        TimerTask task = AsyncFactory.recordOper(operLog);
        task.run();

        assertEquals("内网IP", operLog.getOperLocation());
        verify(operLogService).insertOperlog(argThat(saved ->
                "内网IP".equals(saved.getOperLocation())
                        && "用户管理".equals(saved.getTitle())));
    }

    @Test
    void recordLogininfor注册与注销同样映射为成功状态()
    {
        assertNotNull(AsyncFactory.recordLogininfor("lawyer", Constants.LOGOUT, "退出成功"));

        AsyncFactory.recordLogininfor("lawyer", Constants.LOGOUT, "退出成功").run();
        AsyncFactory.recordLogininfor("lawyer", Constants.REGISTER, "注册成功").run();

        verify(logininforService, org.mockito.Mockito.times(2)).insertLogininfor(argThat(log ->
                Constants.SUCCESS.equals(log.getStatus()) && "lawyer".equals(log.getUserName())));
    }
}
