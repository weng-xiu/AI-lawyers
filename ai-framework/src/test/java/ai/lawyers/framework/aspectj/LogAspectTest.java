package ai.lawyers.framework.aspectj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson2.JSON;

import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.domain.entity.SysDept;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.enums.BusinessStatus;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.framework.manager.AsyncManager;
import ai.lawyers.system.domain.SysOperLog;
import ai.lawyers.system.service.ISysOperLogService;

/**
 * 操作日志切面测试：成功/异常日志字段封装、状态与耗时、请求参数序列化、
 * 敏感字段排除以及需要过滤的参数类型判定。
 *
 * <p>{@link AsyncManager} 使用 mock 调度线程池，捕获异步任务后同步执行，
 * 由操作日志服务 mock 断言最终落库对象。</p>
 *
 * @author ai-lawyers
 */
class LogAspectTest
{
    private final LogAspect aspect = new LogAspect();

    private ScheduledExecutorService executor;

    private ISysOperLogService operLogService;

    private Object originalExecutor;

    private Field beanFactoryField;

    private Object originalBeanFactory;

    /** 被切面拦截的示例控制器 */
    static class SampleController
    {
        @Log(title = "用户管理", businessType = BusinessType.INSERT)
        public Object create(UserForm form)
        {
            return form;
        }

        @Log(title = "导出", businessType = BusinessType.EXPORT, isSaveRequestData = false, isSaveResponseData = false)
        public Object export()
        {
            return null;
        }

        @Log(title = "查询", businessType = BusinessType.OTHER, excludeParamNames = { "username" })
        public Object query()
        {
            return null;
        }
    }

    /** 请求参数示例对象 */
    static class UserForm
    {
        private String username;

        private String password;

        UserForm(String username, String password)
        {
            this.username = username;
            this.password = password;
        }

        public String getUsername()
        {
            return username;
        }

        public String getPassword()
        {
            return password;
        }
    }

    @BeforeEach
    void setUp() throws Exception
    {
        executor = mock(ScheduledExecutorService.class);
        operLogService = mock(ISysOperLogService.class);

        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBean("scheduledExecutorService")).thenReturn(executor);
        when(beanFactory.getBean(ISysOperLogService.class)).thenReturn(operLogService);

        beanFactoryField = SpringUtils.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        originalBeanFactory = beanFactoryField.get(null);
        beanFactoryField.set(null, beanFactory);

        originalExecutor = ReflectionTestUtils.getField(AsyncManager.me(), "executor");
        ReflectionTestUtils.setField(AsyncManager.me(), "executor", executor);

        SysDept dept = new SysDept();
        dept.setDeptName("技术部");
        SysUser user = new SysUser();
        user.setUserName("admin");
        user.setDept(dept);
        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        ReflectionTestUtils.setField(AsyncManager.me(), "executor", originalExecutor);
        beanFactoryField.set(null, originalBeanFactory);
    }

    @Test
    void 成功请求记录操作日志完整字段() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/system/user");
        request.setMethod("POST");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Log log = annotation("create");
        JoinPoint point = point(new Object[] { new UserForm("张三", "secret123") }, "create", new SampleController());

        aspect.doBefore(point, log);
        aspect.doAfterReturning(point, log, Map.of("code", 200));

        SysOperLog operLog = runScheduledTaskAndCapture();
        assertEquals(BusinessStatus.SUCCESS.ordinal(), operLog.getStatus());
        assertEquals("用户管理", operLog.getTitle());
        assertEquals(BusinessType.INSERT.ordinal(), operLog.getBusinessType());
        assertEquals("admin", operLog.getOperName());
        assertEquals("技术部", operLog.getDeptName());
        assertEquals("/system/user", operLog.getOperUrl());
        assertEquals("POST", operLog.getRequestMethod());
        assertTrue(operLog.getMethod().endsWith("SampleController.create()"));
        assertTrue(operLog.getOperParam().contains("张三"));
        assertFalse(operLog.getOperParam().contains("secret123"));
        assertTrue(operLog.getJsonResult().contains("200"));
        assertNotNull(operLog.getCostTime());
        assertEquals("内网IP", operLog.getOperLocation());
    }

    @Test
    void 异常请求记录失败状态与错误信息() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/system/user");
        request.setMethod("POST");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Log log = annotation("create");
        JoinPoint point = point(new Object[] { new UserForm("张三", "secret123") }, "create", new SampleController());

        aspect.doBefore(point, log);
        aspect.doAfterThrowing(point, log, new RuntimeException("数据库连接失败"));

        SysOperLog operLog = runScheduledTaskAndCapture();
        assertEquals(BusinessStatus.FAIL.ordinal(), operLog.getStatus());
        assertTrue(operLog.getErrorMsg().contains("数据库连接失败"));
    }

    @Test
    void 关闭请求与响应保存时不记录参数和返回值() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/system/user/export");
        request.setMethod("POST");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Log log = annotation("export");
        JoinPoint point = point(new Object[] { new UserForm("张三", "secret123") }, "export", new SampleController());

        aspect.doBefore(point, log);
        aspect.doAfterReturning(point, log, Map.of("code", 200));

        SysOperLog operLog = runScheduledTaskAndCapture();
        assertNull(operLog.getOperParam());
        assertNull(operLog.getJsonResult());
        assertEquals(BusinessType.EXPORT.ordinal(), operLog.getBusinessType());
    }

    @Test
    void GET请求按参数表记录且排除注解指定字段() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/system/user/list");
        request.setMethod("GET");
        request.addParameter("username", "张三");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Log log = annotation("query");
        JoinPoint point = point(new Object[0], "query", new SampleController());

        aspect.doBefore(point, log);
        aspect.doAfterReturning(point, log, null);

        SysOperLog operLog = runScheduledTaskAndCapture();
        assertEquals("GET", operLog.getRequestMethod());
        assertNull(operLog.getJsonResult());
        assertNotNull(operLog.getOperParam());
        assertFalse(operLog.getOperParam().contains("张三"));
    }

    @Test
    void 敏感属性排除过滤器不输出密码与注解排除字段()
    {
        UserForm form = new UserForm("张三", "secret123");

        String json = JSON.toJSONString(form, aspect.excludePropertyPreFilter(new String[] { "username" }));

        assertFalse(json.contains("secret123"));
        assertFalse(json.contains("张三"));
    }

    @Test
    void isFilterObject识别文件请求响应与集合()
    {
        assertTrue(aspect.isFilterObject(new MockMultipartFile("file", "a.txt", "text/plain", new byte[0])));
        assertTrue(aspect.isFilterObject(new MultipartFile[] { mock(MultipartFile.class) }));
        assertTrue(aspect.isFilterObject(List.of(mock(MultipartFile.class))));
        assertTrue(aspect.isFilterObject(Map.of("file", mock(MultipartFile.class))));
        assertTrue(aspect.isFilterObject(mock(HttpServletRequest.class)));

        assertFalse(aspect.isFilterObject("plain"));
        assertFalse(aspect.isFilterObject(List.of("plain")));
        assertFalse(aspect.isFilterObject(Collections.emptyList()));
        assertFalse(aspect.isFilterObject(Map.of("id", "1")));
    }

    /** 取示例控制器上指定方法的日志注解 */
    private Log annotation(String methodName) throws Exception
    {
        Class<?>[] paramTypes = methodName.equals("create") ? new Class<?>[] { UserForm.class } : new Class<?>[0];
        Method method = SampleController.class.getMethod(methodName, paramTypes);
        return method.getAnnotation(Log.class);
    }

    /** 构造切点 mock */
    private JoinPoint point(Object[] args, String methodName, Object target)
    {
        JoinPoint point = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        when(point.getSignature()).thenReturn(signature);
        when(point.getTarget()).thenReturn(target);
        when(point.getArgs()).thenReturn(args);
        return point;
    }

    /** 捕获并同步执行切面提交的异步任务，返回最终落库的操作日志 */
    private SysOperLog runScheduledTaskAndCapture()
    {
        ArgumentCaptor<TimerTask> taskCaptor = ArgumentCaptor.forClass(TimerTask.class);
        verify(executor).schedule(taskCaptor.capture(), eq(10L), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        ArgumentCaptor<SysOperLog> logCaptor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(operLogService).insertOperlog(logCaptor.capture());
        return logCaptor.getValue();
    }
}
