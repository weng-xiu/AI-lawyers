package ai.lawyers.framework.config.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;

import ai.lawyers.common.annotation.Anonymous;

/**
 * 匿名访问地址收集测试：方法/类注解、路径变量替换为 *、PathPattern 回退。
 *
 * @author ai-lawyers
 */
class PermitAllUrlPropertiesTest
{
    /** 方法级匿名注解 */
    static class MethodAnonController
    {
        @Anonymous
        public void login()
        {
        }

        public void list()
        {
        }
    }

    /** 类级匿名注解 */
    @Anonymous
    static class ClassAnonController
    {
        public void open()
        {
        }
    }

    @Test
    void afterPropertiesSet_方法注解收集并替换路径变量() throws Exception
    {
        PermitAllUrlProperties properties = properties(info("/api/anon/{id}"),
                handler(new MethodAnonController(), "login"));

        properties.afterPropertiesSet();

        assertEquals(Collections.singletonList("/api/anon/*"), properties.getUrls());
    }

    @Test
    void afterPropertiesSet_类注解收集方法路径() throws Exception
    {
        PermitAllUrlProperties properties = properties(info("/api/open"), handler(new ClassAnonController(), "open"));

        properties.afterPropertiesSet();

        assertEquals(Collections.singletonList("/api/open"), properties.getUrls());
    }

    @Test
    void afterPropertiesSet_无匿名注解不收集() throws Exception
    {
        PermitAllUrlProperties properties = properties(info("/api/user"), handler(new MethodAnonController(), "list"));

        properties.afterPropertiesSet();

        assertTrue(properties.getUrls().isEmpty());
    }

    @Test
    void afterPropertiesSet_PathPattern回退收集() throws Exception
    {
        RequestMappingInfo info = mock(RequestMappingInfo.class);
        when(info.getPatternsCondition()).thenReturn(null);
        PathPatternsRequestCondition condition = mock(PathPatternsRequestCondition.class);
        when(condition.getPatternValues()).thenReturn(Set.of("/p/{code}"));
        when(info.getPathPatternsCondition()).thenReturn(condition);
        PermitAllUrlProperties properties = properties(info, handler(new ClassAnonController(), "open"));

        properties.afterPropertiesSet();

        assertEquals(Collections.singletonList("/p/*"), properties.getUrls());
    }

    @Test
    void setUrls_可直接替换匿名地址列表()
    {
        PermitAllUrlProperties properties = new PermitAllUrlProperties();
        List<String> urls = Collections.singletonList("/custom/*");

        properties.setUrls(urls);

        assertEquals(urls, properties.getUrls());
        assertFalse(properties.getUrls().isEmpty());
    }

    /** 构造只含单个处理器映射的配置对象 */
    private PermitAllUrlProperties properties(RequestMappingInfo info, HandlerMethod handlerMethod)
    {
        Map<RequestMappingInfo, HandlerMethod> handlers = new HashMap<>();
        handlers.put(info, handlerMethod);
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        when(mapping.getHandlerMethods()).thenReturn(handlers);
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)).thenReturn(mapping);
        PermitAllUrlProperties properties = new PermitAllUrlProperties();
        properties.setApplicationContext(context);
        return properties;
    }

    /** 构造带路径条件的映射信息 */
    private RequestMappingInfo info(String pattern)
    {
        RequestMappingInfo info = mock(RequestMappingInfo.class);
        when(info.getPatternsCondition()).thenReturn(new PatternsRequestCondition(pattern));
        return info;
    }

    /** 构造指定控制器方法的处理器 */
    private HandlerMethod handler(Object controller, String methodName) throws Exception
    {
        return new HandlerMethod(controller, controller.getClass().getMethod(methodName));
    }
}
