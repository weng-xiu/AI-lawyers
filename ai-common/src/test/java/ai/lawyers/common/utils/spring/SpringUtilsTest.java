package ai.lawyers.common.utils.spring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Spring 工具测试：静态上下文取 Bean、Bean 定义查询与配置读取。
 *
 * @author ai-lawyers
 */
class SpringUtilsTest
{
    private static final String BEAN_NAME = "demoService";

    private final Object demoBean = new Object();

    private Object oldBeanFactory;

    private Object oldApplicationContext;

    @BeforeEach
    void setUp() throws Exception
    {
        ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { ConfigurableListableBeanFactory.class },
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getBean":
                            return demoBean;
                        case "containsBean":
                            return BEAN_NAME.equals(args[0]);
                        case "isSingleton":
                            return true;
                        case "getType":
                            return Object.class;
                        case "getAliases":
                            return new String[] { "aliasA" };
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });

        Environment environment = (Environment) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Environment.class }, (proxy, method, args) -> {
                    if ("getActiveProfiles".equals(method.getName()))
                    {
                        return new String[] { "dev", "test" };
                    }
                    if ("getRequiredProperty".equals(method.getName()))
                    {
                        return "value:" + args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        ApplicationContext applicationContext = (ApplicationContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { ApplicationContext.class },
                (proxy, method, args) -> {
                    if ("getEnvironment".equals(method.getName()))
                    {
                        return environment;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        oldBeanFactory = field("beanFactory").get(null);
        oldApplicationContext = field("applicationContext").get(null);
        field("beanFactory").set(null, beanFactory);
        field("applicationContext").set(null, applicationContext);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        field("beanFactory").set(null, oldBeanFactory);
        field("applicationContext").set(null, oldApplicationContext);
    }

    @Test
    void getBean_按类型与名称获取()
    {
        assertSame(demoBean, SpringUtils.getBean(Object.class));
        assertSame(demoBean, SpringUtils.getBean(BEAN_NAME));
    }

    @Test
    void containsBean与isSingleton与getType与getAliases()
    {
        assertTrue(SpringUtils.containsBean(BEAN_NAME));
        assertFalse(SpringUtils.containsBean("other"));
        assertTrue(SpringUtils.isSingleton(BEAN_NAME));
        assertEquals(Object.class, SpringUtils.getType(BEAN_NAME));
        assertArrayEquals(new String[] { "aliasA" }, SpringUtils.getAliases(BEAN_NAME));
    }

    @Test
    void getActiveProfiles与getActiveProfile()
    {
        assertArrayEquals(new String[] { "dev", "test" }, SpringUtils.getActiveProfiles());
        assertEquals("dev", SpringUtils.getActiveProfile());
    }

    @Test
    void getRequiredProperty_读取配置项()
    {
        assertEquals("value:ruoyi.name", SpringUtils.getRequiredProperty("ruoyi.name"));
    }

    @Test
    void getActiveProfile_无环境配置时返回null()
    {
        Environment emptyEnvironment = (Environment) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Environment.class }, (proxy, method, args) -> {
                    if ("getActiveProfiles".equals(method.getName()))
                    {
                        return new String[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        try
        {
            field("applicationContext").set(null, Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { ApplicationContext.class }, (proxy, method, args) -> {
                        if ("getEnvironment".equals(method.getName()))
                        {
                            return emptyEnvironment;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        assertNull(SpringUtils.getActiveProfile());
    }

    private static Field field(String name) throws NoSuchFieldException
    {
        Field field = SpringUtils.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
