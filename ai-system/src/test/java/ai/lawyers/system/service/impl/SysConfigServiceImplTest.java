package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.SysConfig;
import ai.lawyers.system.mapper.SysConfigMapper;

/**
 * 参数配置服务单测。
 *
 * <p>覆盖按 ID/键名查询与缓存回填、验证码开关默认值、新增修改后刷新缓存、
 * 内置参数删除拦截、缓存加载/清空/重置，以及参数键唯一性校验。</p>
 *
 * @author ai-lawyers
 */
class SysConfigServiceImplTest
{
    private SysConfigServiceImpl service;

    @Mock private SysConfigMapper configMapper;
    @Mock private RedisCache redisCache;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysConfigServiceImpl();
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "redisCache", redisCache);
    }

    private static SysConfig config(Long configId, String key, String value)
    {
        SysConfig config = new SysConfig();
        config.setConfigId(configId);
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }

    @Test
    void selectConfigById_buildsQueryAndDelegates()
    {
        SysConfig expected = config(1L, "k", "v");
        when(configMapper.selectConfig(any(SysConfig.class))).thenReturn(expected);

        assertSame(expected, service.selectConfigById(1L));
    }

    @Test
    void selectConfigByKey_cacheHit_returnsWithoutQuery()
    {
        when(redisCache.getCacheObject("sys_config:k")).thenReturn("cached");

        assertEquals("cached", service.selectConfigByKey("k"));
        verify(configMapper, never()).selectConfig(any());
    }

    @Test
    void selectConfigByKey_cacheMiss_queriesAndCaches()
    {
        when(redisCache.getCacheObject("sys_config:k")).thenReturn(null);
        when(configMapper.selectConfig(any(SysConfig.class))).thenReturn(config(1L, "k", "dbValue"));

        assertEquals("dbValue", service.selectConfigByKey("k"));
        verify(redisCache).setCacheObject("sys_config:k", "dbValue");
    }

    @Test
    void selectConfigByKey_notFound_returnsEmpty()
    {
        when(redisCache.getCacheObject("sys_config:missing")).thenReturn(null);
        when(configMapper.selectConfig(any(SysConfig.class))).thenReturn(null);

        assertEquals("", service.selectConfigByKey("missing"));
        verify(redisCache, never()).setCacheObject(anyString(), any());
    }

    @Test
    void selectCaptchaEnabled_missing_returnsTrue()
    {
        when(redisCache.getCacheObject("sys_config:sys.account.captchaEnabled")).thenReturn(null);
        when(configMapper.selectConfig(any(SysConfig.class))).thenReturn(null);

        assertTrue(service.selectCaptchaEnabled());
    }

    @Test
    void selectCaptchaEnabled_false_returnsFalse()
    {
        when(redisCache.getCacheObject("sys_config:sys.account.captchaEnabled")).thenReturn("false");

        assertFalse(service.selectCaptchaEnabled());
    }

    @Test
    void selectCaptchaEnabled_true_returnsTrue()
    {
        when(redisCache.getCacheObject("sys_config:sys.account.captchaEnabled")).thenReturn("true");

        assertTrue(service.selectCaptchaEnabled());
    }

    @Test
    void selectConfigList_delegates()
    {
        SysConfig query = new SysConfig();
        List<SysConfig> list = Collections.singletonList(config(1L, "k", "v"));
        when(configMapper.selectConfigList(query)).thenReturn(list);

        assertSame(list, service.selectConfigList(query));
    }

    @Test
    void insertConfig_success_cachesValue()
    {
        SysConfig config = config(1L, "k", "v");
        when(configMapper.insertConfig(config)).thenReturn(1);

        assertEquals(1, service.insertConfig(config));
        verify(redisCache).setCacheObject("sys_config:k", "v");
    }

    @Test
    void insertConfig_failure_doesNotCache()
    {
        SysConfig config = config(1L, "k", "v");
        when(configMapper.insertConfig(config)).thenReturn(0);

        assertEquals(0, service.insertConfig(config));
        verify(redisCache, never()).setCacheObject(anyString(), any());
    }

    @Test
    void updateConfig_keyChanged_deletesOldAndCachesNew()
    {
        SysConfig oldConfig = config(1L, "oldKey", "oldValue");
        SysConfig config = config(1L, "newKey", "newValue");
        when(configMapper.selectConfigById(1L)).thenReturn(oldConfig);
        when(configMapper.updateConfig(config)).thenReturn(1);

        assertEquals(1, service.updateConfig(config));

        verify(redisCache).deleteObject("sys_config:oldKey");
        verify(redisCache).setCacheObject("sys_config:newKey", "newValue");
    }

    @Test
    void updateConfig_keyUnchanged_keepsCacheKey()
    {
        SysConfig oldConfig = config(1L, "k", "oldValue");
        SysConfig config = config(1L, "k", "newValue");
        when(configMapper.selectConfigById(1L)).thenReturn(oldConfig);
        when(configMapper.updateConfig(config)).thenReturn(1);

        assertEquals(1, service.updateConfig(config));

        verify(redisCache, never()).deleteObject(anyString());
        verify(redisCache).setCacheObject("sys_config:k", "newValue");
    }

    @Test
    void updateConfig_failure_doesNotCache()
    {
        SysConfig oldConfig = config(1L, "k", "oldValue");
        SysConfig config = config(1L, "k", "newValue");
        when(configMapper.selectConfigById(1L)).thenReturn(oldConfig);
        when(configMapper.updateConfig(config)).thenReturn(0);

        assertEquals(0, service.updateConfig(config));

        verify(redisCache, never()).setCacheObject(anyString(), any());
    }

    @Test
    void deleteConfigByIds_builtin_throws()
    {
        SysConfig builtin = config(1L, "sys.builtin", "v");
        builtin.setConfigType("Y");
        when(configMapper.selectConfig(any(SysConfig.class))).thenReturn(builtin);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deleteConfigByIds(new Long[] { 1L }));
        assertEquals("内置参数【sys.builtin】不能删除 ", ex.getMessage());
        verify(configMapper, never()).deleteConfigById(any());
    }

    @Test
    void deleteConfigByIds_normal_deletesAndEvicts()
    {
        SysConfig normal = config(2L, "k", "v");
        normal.setConfigType("N");
        when(configMapper.selectConfig(any(SysConfig.class))).thenReturn(normal);

        service.deleteConfigByIds(new Long[] { 2L });

        verify(configMapper).deleteConfigById(2L);
        verify(redisCache).deleteObject("sys_config:k");
    }

    @Test
    void loadingConfigCache_cachesAllConfigs()
    {
        SysConfig c1 = config(1L, "k1", "v1");
        SysConfig c2 = config(2L, "k2", "v2");
        when(configMapper.selectConfigList(any(SysConfig.class))).thenReturn(Arrays.asList(c1, c2));

        service.loadingConfigCache();

        verify(redisCache).setCacheObject("sys_config:k1", "v1");
        verify(redisCache).setCacheObject("sys_config:k2", "v2");
    }

    @Test
    void clearConfigCache_deletesMatchedKeys()
    {
        List<String> keys = Arrays.asList("sys_config:k1", "sys_config:k2");
        when(redisCache.keys("sys_config:*")).thenReturn(keys);

        service.clearConfigCache();

        verify(redisCache).deleteObject(keys);
    }

    @Test
    void resetConfigCache_clearsThenLoads()
    {
        List<String> keys = Collections.singletonList("sys_config:k1");
        when(redisCache.keys("sys_config:*")).thenReturn(keys);
        when(configMapper.selectConfigList(any(SysConfig.class)))
            .thenReturn(Collections.singletonList(config(1L, "k1", "v1")));

        service.resetConfigCache();

        verify(redisCache).deleteObject(keys);
        verify(redisCache).setCacheObject("sys_config:k1", "v1");
    }

    @Test
    void checkConfigKeyUnique_whenNotExists_returnsUnique()
    {
        when(configMapper.checkConfigKeyUnique("k")).thenReturn(null);

        assertTrue(service.checkConfigKeyUnique(config(null, "k", "v")));
    }

    @Test
    void checkConfigKeyUnique_whenSameId_returnsUnique()
    {
        when(configMapper.checkConfigKeyUnique("k")).thenReturn(config(1L, "k", "v"));

        assertTrue(service.checkConfigKeyUnique(config(1L, "k", "v")));
    }

    @Test
    void checkConfigKeyUnique_whenOtherId_returnsNotUnique()
    {
        when(configMapper.checkConfigKeyUnique("k")).thenReturn(config(2L, "k", "v"));

        assertFalse(service.checkConfigKeyUnique(config(1L, "k", "v")));
    }
}
