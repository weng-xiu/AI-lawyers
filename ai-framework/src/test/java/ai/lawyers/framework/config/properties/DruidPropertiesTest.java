package ai.lawyers.framework.config.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.druid.pool.DruidDataSource;

/**
 * Druid 连接池属性测试：配置值写入数据源并原样返回。
 *
 * @author ai-lawyers
 */
class DruidPropertiesTest
{
    @Test
    void dataSource写入全部连接池参数()
    {
        DruidProperties properties = new DruidProperties();
        ReflectionTestUtils.setField(properties, "initialSize", 5);
        ReflectionTestUtils.setField(properties, "minIdle", 10);
        ReflectionTestUtils.setField(properties, "maxActive", 50);
        ReflectionTestUtils.setField(properties, "maxWait", 60000);
        ReflectionTestUtils.setField(properties, "connectTimeout", 3000);
        ReflectionTestUtils.setField(properties, "socketTimeout", 6000);
        ReflectionTestUtils.setField(properties, "timeBetweenEvictionRunsMillis", 60000);
        ReflectionTestUtils.setField(properties, "minEvictableIdleTimeMillis", 300000);
        ReflectionTestUtils.setField(properties, "maxEvictableIdleTimeMillis", 900000);
        ReflectionTestUtils.setField(properties, "validationQuery", "SELECT 1");
        ReflectionTestUtils.setField(properties, "testWhileIdle", true);
        ReflectionTestUtils.setField(properties, "testOnBorrow", false);
        ReflectionTestUtils.setField(properties, "testOnReturn", false);

        DruidDataSource dataSource = new DruidDataSource();
        DruidDataSource result = properties.dataSource(dataSource);

        assertSame(dataSource, result);
        assertEquals(5, dataSource.getInitialSize());
        assertEquals(10, dataSource.getMinIdle());
        assertEquals(50, dataSource.getMaxActive());
        assertEquals(60000, dataSource.getMaxWait());
        assertEquals(3000, dataSource.getConnectTimeout());
        assertEquals(6000, dataSource.getSocketTimeout());
        assertEquals(60000, dataSource.getTimeBetweenEvictionRunsMillis());
        assertEquals(300000, dataSource.getMinEvictableIdleTimeMillis());
        assertEquals(900000, dataSource.getMaxEvictableIdleTimeMillis());
        assertEquals("SELECT 1", dataSource.getValidationQuery());
        assertTrue(dataSource.isTestWhileIdle());
    }
}
