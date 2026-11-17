package ai.lawyers.common.core.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis 缓存工具测试：基于内存模板验证各类数据结构的读写与删除。
 *
 * @author ai-lawyers
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class RedisCacheTest
{
    private FakeRedisTemplate template;

    private RedisCache cache;

    @BeforeEach
    void setUp()
    {
        template = new FakeRedisTemplate();
        cache = new RedisCache();
        cache.redisTemplate = template;
    }

    @Test
    void 对象缓存读写()
    {
        cache.setCacheObject("user:1", "张三");

        assertEquals("张三", cache.getCacheObject("user:1"));
        assertNull(cache.getCacheObject("unknown"));
    }

    @Test
    void 对象缓存带过期时间()
    {
        cache.setCacheObject("token", "abc", 30, TimeUnit.MINUTES);

        assertEquals("abc", cache.getCacheObject("token"));
        assertEquals(1, template.valueSetCalls.size());
        Object[] call = template.valueSetCalls.get(0);
        assertEquals(30L, call[2]);
        assertEquals(TimeUnit.MINUTES, call[3]);
    }

    @Test
    void 过期时间读写与键存在判断()
    {
        assertTrue(cache.expire("key", 60));
        assertTrue(cache.expire("key", 5, TimeUnit.MINUTES));
        assertEquals(2, template.expireCalls.size());
        assertEquals(5L, template.expireCalls.get(1)[1]);

        assertEquals(60L, cache.getExpire("key"));

        cache.setCacheObject("exists", 1);
        assertTrue(cache.hasKey("exists"));
        assertFalse(cache.hasKey("missing"));
    }

    @Test
    void 删除单个对象与集合对象()
    {
        cache.setCacheObject("a", 1);
        cache.setCacheObject("b", 2);
        cache.setCacheObject("c", 3);

        assertTrue(cache.deleteObject("a"));
        assertFalse(cache.deleteObject("a"));

        assertTrue(cache.deleteObject(List.of("b", "c")));
        assertFalse(cache.deleteObject(List.of("b", "c")));
    }

    @Test
    void List缓存读写()
    {
        long count = cache.setCacheList("queue", List.of("a", "b", "c"));

        assertEquals(3, count);
        assertEquals(List.of("a", "b", "c"), cache.getCacheList("queue"));
    }

    @Test
    void Set缓存读写()
    {
        BoundSetOperations<String, Object> operations = cache.setCacheSet("tags", Set.of("x", "y"));

        assertNotNull(operations);
        assertEquals(Set.of("x", "y"), cache.getCacheSet("tags"));
    }

    @Test
    void Map缓存读写()
    {
        cache.setCacheMap("config", Map.of("k1", "v1", "k2", "v2"));
        assertEquals("v1", cache.getCacheMap("config").get("k1"));

        // null 不写入
        cache.setCacheMap("nullMap", null);
        assertTrue(cache.getCacheMap("nullMap").isEmpty());

        cache.setCacheMapValue("config", "k3", "v3");
        assertEquals("v3", cache.getCacheMapValue("config", "k3"));

        assertEquals(List.of("v1", "v3"), cache.getMultiCacheMapValue("config", List.of("k1", "k3")));

        assertTrue(cache.deleteCacheMapValue("config", "k3"));
        assertFalse(cache.deleteCacheMapValue("config", "k3"));
    }

    @Test
    void keys按模式匹配()
    {
        cache.setCacheObject("sys_dict:user_sex", "x");
        cache.setCacheObject("sys_dict:user_status", "y");
        cache.setCacheObject("sys_config:skin", "z");

        Collection<String> matched = cache.keys("sys_dict:*");

        assertEquals(2, matched.size());
        assertTrue(matched.contains("sys_dict:user_sex"));
        assertTrue(matched.contains("sys_dict:user_status"));
    }

    /**
     * 内存版 RedisTemplate，仅实现工具类用到的方法。
     */
    private static class FakeRedisTemplate extends RedisTemplate<String, Object>
    {
        private final Map<String, Object> values = new HashMap<>();

        private final Map<String, List<Object>> lists = new HashMap<>();

        private final Map<String, Set<Object>> sets = new HashMap<>();

        private final Map<String, Map<String, Object>> hashes = new HashMap<>();

        private final List<Object[]> valueSetCalls = new ArrayList<>();

        private final List<Object[]> expireCalls = new ArrayList<>();

        @Override
        public ValueOperations<String, Object> opsForValue()
        {
            return (ValueOperations<String, Object>) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { ValueOperations.class }, (proxy, method, args) -> {
                        if ("set".equals(method.getName()))
                        {
                            if (args.length == 4)
                            {
                                valueSetCalls.add(args);
                            }
                            values.put((String) args[0], args[1]);
                            return null;
                        }
                        if ("get".equals(method.getName()))
                        {
                            return values.get(args[0]);
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public ListOperations<String, Object> opsForList()
        {
            return (ListOperations<String, Object>) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { ListOperations.class }, (proxy, method, args) -> {
                        if ("rightPushAll".equals(method.getName()))
                        {
                            List<Object> target = lists.computeIfAbsent((String) args[0], k -> new ArrayList<>());
                            target.addAll((Collection<Object>) args[1]);
                            return (long) target.size();
                        }
                        if ("range".equals(method.getName()))
                        {
                            return new ArrayList<>(lists.getOrDefault(args[0], List.of()));
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public SetOperations<String, Object> opsForSet()
        {
            return (SetOperations<String, Object>) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { SetOperations.class }, (proxy, method, args) -> {
                        if ("members".equals(method.getName()))
                        {
                            return new LinkedHashSet<>(sets.getOrDefault(args[0], Set.of()));
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public BoundSetOperations<String, Object> boundSetOps(String key)
        {
            return (BoundSetOperations<String, Object>) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { BoundSetOperations.class }, (proxy, method, args) -> {
                        if ("add".equals(method.getName()))
                        {
                            Set<Object> target = sets.computeIfAbsent(key, k -> new LinkedHashSet<>());
                            Object[] elements = args[0] instanceof Object[] ? (Object[]) args[0] : new Object[] { args[0] };
                            target.addAll(List.of(elements));
                            return (long) target.size();
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public HashOperations<String, String, Object> opsForHash()
        {
            return (HashOperations<String, String, Object>) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { HashOperations.class }, (proxy, method, args) -> {
                        String key = (String) args[0];
                        Map<String, Object> hash = hashes.computeIfAbsent(key, k -> new HashMap<>());
                        switch (method.getName())
                        {
                            case "putAll":
                                hash.putAll((Map<String, Object>) args[1]);
                                return null;
                            case "put":
                                hash.put((String) args[1], args[2]);
                                return null;
                            case "get":
                                return hash.get(args[1]);
                            case "entries":
                                return new HashMap<>(hash);
                            case "multiGet":
                                List<Object> result = new ArrayList<>();
                                for (Object hKey : (Collection<Object>) args[1])
                                {
                                    result.add(hash.get(hKey));
                                }
                                return result;
                            case "delete":
                                long removed = 0;
                                for (Object hKey : (Object[]) args[1])
                                {
                                    removed += hash.remove(hKey) != null ? 1 : 0;
                                }
                                return removed;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit)
        {
            expireCalls.add(new Object[] { key, timeout, unit });
            return true;
        }

        @Override
        public Long getExpire(String key)
        {
            return 60L;
        }

        @Override
        public Boolean hasKey(String key)
        {
            return values.containsKey(key) || lists.containsKey(key) || sets.containsKey(key)
                    || hashes.containsKey(key);
        }

        @Override
        public Boolean delete(String key)
        {
            boolean removed = values.remove(key) != null;
            lists.remove(key);
            sets.remove(key);
            hashes.remove(key);
            return removed;
        }

        @Override
        public Long delete(Collection<String> keys)
        {
            long count = 0;
            for (String key : keys)
            {
                count += Boolean.TRUE.equals(delete(key)) ? 1 : 0;
            }
            return count;
        }

        @Override
        public Set<String> keys(String pattern)
        {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            Set<String> all = new LinkedHashSet<>();
            all.addAll(values.keySet());
            all.addAll(lists.keySet());
            all.addAll(sets.keySet());
            all.addAll(hashes.keySet());

            Set<String> matched = new LinkedHashSet<>();
            for (String key : all)
            {
                if (key.startsWith(prefix))
                {
                    matched.add(key);
                }
            }
            return matched;
        }
    }
}
