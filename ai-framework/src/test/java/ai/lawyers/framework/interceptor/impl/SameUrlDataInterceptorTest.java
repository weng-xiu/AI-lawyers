package ai.lawyers.framework.interceptor.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSON;

import ai.lawyers.common.annotation.RepeatSubmit;
import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.core.redis.RedisCache;

/**
 * 重复提交拦截测试：首次放行、参数相同且未超间隔判定重复、
 * 参数变化/超时放行，以及请求头参与缓存 key。
 *
 * @author ai-lawyers
 */
class SameUrlDataInterceptorTest
{
    private static final String URL = "/system/user/edit";

    private SameUrlDataInterceptor interceptor;

    private RedisCache redisCache;

    private RepeatSubmit annotation;

    @BeforeEach
    void setUp()
    {
        interceptor = new SameUrlDataInterceptor();
        redisCache = mock(RedisCache.class);
        ReflectionTestUtils.setField(interceptor, "redisCache", redisCache);
        ReflectionTestUtils.setField(interceptor, "header", "Authorization");

        annotation = mock(RepeatSubmit.class);
        when(annotation.interval()).thenReturn(5000);
    }

    @Test
    void isRepeatSubmit_首次提交放行并写入缓存()
    {
        MockHttpServletRequest request = request("name", "张三");
        when(redisCache.getCacheObject(anyString())).thenReturn(null);

        assertFalse(interceptor.isRepeatSubmit(request, annotation));

        verify(redisCache).setCacheObject(eq(CacheConstants.REPEAT_SUBMIT_KEY + URL), anyMap(), eq(5000),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void isRepeatSubmit_参数相同且未超间隔判定重复()
    {
        MockHttpServletRequest request = request("name", "张三");
        when(redisCache.getCacheObject(anyString()))
                .thenReturn(session(URL, JSON.toJSONString(request.getParameterMap()), System.currentTimeMillis() - 1000));

        assertTrue(interceptor.isRepeatSubmit(request, annotation));
    }

    @Test
    void isRepeatSubmit_参数不同放行()
    {
        MockHttpServletRequest request = request("name", "张三");
        MockHttpServletRequest previous = request("name", "李四");
        when(redisCache.getCacheObject(anyString()))
                .thenReturn(session(URL, JSON.toJSONString(previous.getParameterMap()), System.currentTimeMillis() - 1000));

        assertFalse(interceptor.isRepeatSubmit(request, annotation));
    }

    @Test
    void isRepeatSubmit_超过间隔时间放行()
    {
        MockHttpServletRequest request = request("name", "张三");
        when(redisCache.getCacheObject(anyString()))
                .thenReturn(session(URL, JSON.toJSONString(request.getParameterMap()), System.currentTimeMillis() - 6000));

        assertFalse(interceptor.isRepeatSubmit(request, annotation));
    }

    @Test
    void isRepeatSubmit_请求头参与缓存key()
    {
        MockHttpServletRequest request = request("name", "张三");
        request.addHeader("Authorization", "token-abc");
        when(redisCache.getCacheObject(anyString())).thenReturn(null);

        assertFalse(interceptor.isRepeatSubmit(request, annotation));

        verify(redisCache).setCacheObject(eq(CacheConstants.REPEAT_SUBMIT_KEY + URL + "token-abc"), anyMap(), eq(5000),
                eq(TimeUnit.MILLISECONDS));
    }

    /** 构造带单个参数的 POST 请求 */
    private MockHttpServletRequest request(String name, String value)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", URL);
        request.addParameter(name, value);
        return request;
    }

    /** 构造上次提交的缓存结构：url -> {repeatParams, repeatTime} */
    private Map<String, Object> session(String url, String params, long repeatTime)
    {
        Map<String, Object> data = new HashMap<>();
        data.put("repeatParams", params);
        data.put("repeatTime", repeatTime);
        Map<String, Object> session = new HashMap<>();
        session.put(url, data);
        return session;
    }
}
