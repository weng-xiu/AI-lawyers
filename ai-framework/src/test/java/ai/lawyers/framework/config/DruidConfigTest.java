package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.druid.spring.boot3.autoconfigure.properties.DruidStatProperties;

import ai.lawyers.common.enums.DataSourceType;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.framework.datasource.DynamicDataSource;

/**
 * Druid 多数据源配置测试：主从数据源装配、备选源注册与监控页过滤器。
 *
 * @author ai-lawyers
 */
@SuppressWarnings("rawtypes")
class DruidConfigTest
{
    private final DruidConfig config = new DruidConfig();

    private Object oldBeanFactory;

    private DataSource slaveDataSource;

    @BeforeEach
    void setUp() throws Exception
    {
        slaveDataSource = mock(DataSource.class);
        oldBeanFactory = field().get(null);
        field().set(null, Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ConfigurableListableBeanFactory.class }, (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && "slaveDataSource".equals(args[0]))
                    {
                        return slaveDataSource;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        field().set(null, oldBeanFactory);
    }

    @Test
    void dataSource_注册主从数据源()
    {
        DataSource master = mock(DataSource.class);

        DynamicDataSource dynamicDataSource = config.dataSource(master);

        Map<Object, Object> targets = targetsOf(dynamicDataSource);
        assertSame(master, targets.get(DataSourceType.MASTER.name()));
        assertSame(slaveDataSource, targets.get(DataSourceType.SLAVE.name()));
        assertEquals(2, targets.size());
    }

    @Test
    void setDataSource_缺少Bean时忽略()
    {
        Map<Object, Object> targets = new HashMap<>();

        config.setDataSource(targets, DataSourceType.SLAVE.name(), "missingDataSource");
        assertTrue(targets.isEmpty());

        config.setDataSource(targets, DataSourceType.SLAVE.name(), "slaveDataSource");
        assertSame(slaveDataSource, targets.get(DataSourceType.SLAVE.name()));
    }

    @Test
    void removeDruidFilterRegistrationBean_默认监控路径()
    {
        DruidStatProperties properties = new DruidStatProperties();

        FilterRegistrationBean registration = config.removeDruidFilterRegistrationBean(properties);

        assertNotNull(registration.getFilter());
        assertTrue(registration.getUrlPatterns().contains("/druid/js/common.js"));
        assertFalse(registration.getUrlPatterns().isEmpty());
    }

    @Test
    void removeDruidFilterRegistrationBean_自定义监控路径()
    {
        DruidStatProperties properties = new DruidStatProperties();
        properties.getStatViewServlet().setUrlPattern("/monitor/*");

        FilterRegistrationBean registration = config.removeDruidFilterRegistrationBean(properties);

        assertTrue(registration.getUrlPatterns().contains("/monitor/js/common.js"));
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> targetsOf(DynamicDataSource dataSource)
    {
        return (Map<Object, Object>) ReflectionTestUtils.getField(dataSource, "targetDataSources");
    }

    private static Field field() throws NoSuchFieldException
    {
        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        return field;
    }
}
