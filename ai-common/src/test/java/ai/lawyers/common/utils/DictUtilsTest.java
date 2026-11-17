package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;

import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.core.domain.entity.SysDictData;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.spring.SpringUtils;

/**
 * 字典工具测试：缓存读写、标签与值互转、多值拼接及缓存清理。
 *
 * @author ai-lawyers
 */
class DictUtilsTest
{
    private static final String DICT_TYPE = "sys_user_sex";

    private FakeRedisCache redisCache;

    private Object oldBeanFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        redisCache = new FakeRedisCache();
        oldBeanFactory = getBeanFactoryField().get(null);

        ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { ConfigurableListableBeanFactory.class },
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] == RedisCache.class)
                    {
                        return redisCache;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        getBeanFactoryField().set(null, beanFactory);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        getBeanFactoryField().set(null, oldBeanFactory);
    }

    @Test
    void getCacheKey_拼接字典前缀()
    {
        assertEquals(CacheConstants.SYS_DICT_KEY + DICT_TYPE, DictUtils.getCacheKey(DICT_TYPE));
    }

    @Test
    void setAndGetDictCache_写入后可读取()
    {
        List<SysDictData> datas = Arrays.asList(dict("0", "男"), dict("1", "女"));

        DictUtils.setDictCache(DICT_TYPE, datas);

        assertTrue(redisCache.containsKey(DictUtils.getCacheKey(DICT_TYPE)));
        List<SysDictData> cached = DictUtils.getDictCache(DICT_TYPE);
        assertNotNull(cached);
        assertEquals(2, cached.size());
        assertEquals("男", cached.get(0).getDictLabel());
        assertEquals("1", cached.get(1).getDictValue());

        // 未写入的类型返回 null
        assertNull(DictUtils.getDictCache("unknown"));
    }

    @Test
    void getDictLabel_空字典值直接返回空串()
    {
        assertEquals("", DictUtils.getDictLabel(DICT_TYPE, ""));
        assertEquals("", DictUtils.getDictLabel(DICT_TYPE, null));
    }

    @Test
    void getDictLabel_精确匹配与多值匹配()
    {
        seed();

        assertEquals("男", DictUtils.getDictLabel(DICT_TYPE, "0"));
        assertEquals("男,女", DictUtils.getDictLabel(DICT_TYPE, "0,1"));
        // 未匹配的字典值返回空串
        assertEquals("", DictUtils.getDictLabel(DICT_TYPE, "9"));
        // 自定义分隔符
        assertEquals("男;女", DictUtils.getDictLabel(DICT_TYPE, "0;1", ";"));
    }

    @Test
    void getDictValue_精确匹配与多值匹配()
    {
        seed();

        assertEquals("0", DictUtils.getDictValue(DICT_TYPE, "男"));
        assertEquals("0,1", DictUtils.getDictValue(DICT_TYPE, "男,女"));
        assertEquals("", DictUtils.getDictValue(DICT_TYPE, "未知性别"));
        assertEquals("0;2", DictUtils.getDictValue(DICT_TYPE, "男;未知", ";"));
    }

    @Test
    void getDictValues与getDictLabels_返回全部并去除末尾分隔符()
    {
        seed();

        assertEquals("0,1,2", DictUtils.getDictValues(DICT_TYPE));
        assertEquals("男,女,未知", DictUtils.getDictLabels(DICT_TYPE));
    }

    @Test
    void 无缓存时查询返回空串()
    {
        assertEquals("", DictUtils.getDictLabel(DICT_TYPE, "0"));
        assertEquals("", DictUtils.getDictValue(DICT_TYPE, "男"));
        assertEquals("", DictUtils.getDictValues(DICT_TYPE));
        assertEquals("", DictUtils.getDictLabels(DICT_TYPE));
    }

    @Test
    void removeDictCache_删除指定缓存()
    {
        seed();

        DictUtils.removeDictCache(DICT_TYPE);

        assertFalse(redisCache.containsKey(DictUtils.getCacheKey(DICT_TYPE)));
        assertNull(DictUtils.getDictCache(DICT_TYPE));
    }

    @Test
    void clearDictCache_删除全部字典缓存()
    {
        seed();
        DictUtils.setDictCache("other_dict", List.of(dict("0", "其他")));

        DictUtils.clearDictCache();

        assertEquals(0, redisCache.size());
    }

    private void seed()
    {
        DictUtils.setDictCache(DICT_TYPE, Arrays.asList(dict("0", "男"), dict("1", "女"), dict("2", "未知")));
    }

    private static SysDictData dict(String value, String label)
    {
        SysDictData data = new SysDictData();
        data.setDictType(DICT_TYPE);
        data.setDictValue(value);
        data.setDictLabel(label);
        return data;
    }

    private static Field getBeanFactoryField() throws NoSuchFieldException
    {
        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        return field;
    }

    /**
     * 使用内存 Map 模拟 Redis 缓存，读写时按真实序列化行为转换为 JSONArray。
     */
    private static class FakeRedisCache extends RedisCache
    {
        private final Map<String, Object> store = new HashMap<>();

        @Override
        public <T> void setCacheObject(String key, T value)
        {
            store.put(key, JSONArray.parseArray(JSON.toJSONString(value)));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObject(String key)
        {
            return (T) store.get(key);
        }

        @Override
        public boolean deleteObject(String key)
        {
            return store.remove(key) != null;
        }

        @Override
        public boolean deleteObject(Collection collection)
        {
            boolean removed = false;
            for (Object key : new ArrayList<>(collection))
            {
                removed |= store.remove(String.valueOf(key)) != null;
            }
            return removed;
        }

        @Override
        public Collection<String> keys(String pattern)
        {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            List<String> matched = new ArrayList<>();
            for (String key : store.keySet())
            {
                if (key.startsWith(prefix))
                {
                    matched.add(key);
                }
            }
            return matched;
        }

        boolean containsKey(String key)
        {
            return store.containsKey(key);
        }

        int size()
        {
            return store.size();
        }
    }
}
