package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

/**
 * MyBatis 配置测试：别名包扫描与 Mapper 资源解析。
 *
 * @author ai-lawyers
 */
class MyBatisConfigTest
{
    private final MyBatisConfig config = new MyBatisConfig();

    @Test
    void setTypeAliasesPackage_单包扫描()
    {
        String result = MyBatisConfig.setTypeAliasesPackage("ai.lawyers.common.constant");

        assertEquals("ai.lawyers.common.constant", result);
    }

    @Test
    void setTypeAliasesPackage_多包去重()
    {
        String result = MyBatisConfig.setTypeAliasesPackage(
                "ai.lawyers.common.constant, ai.lawyers.common.enums");

        String[] packages = result.split(",");
        assertEquals(2, packages.length);
        assertTrue(Arrays.asList(packages).contains("ai.lawyers.common.constant"));
        assertTrue(Arrays.asList(packages).contains("ai.lawyers.common.enums"));
    }

    @Test
    void setTypeAliasesPackage_无效包抛出异常()
    {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> MyBatisConfig.setTypeAliasesPackage("ai.lawyers.not.exists"));

        assertTrue(exception.getMessage().contains("typeAliasesPackage"));
    }

    @Test
    void resolveMapperLocations_空配置返回空数组()
    {
        assertArrayEquals(new Resource[0], config.resolveMapperLocations(null));
        assertArrayEquals(new Resource[0],
                config.resolveMapperLocations(new String[] { "classpath*:not/exists/**/*.xml" }));
    }

    @Test
    void resolveMapperLocations_匹配已有映射文件()
    {
        Resource[] resources = config.resolveMapperLocations(
                new String[] { "classpath*:mapper/system/SysUserMapper.xml" });

        assertTrue(resources.length >= 1);
        assertTrue(resources[0].exists());
    }
}
