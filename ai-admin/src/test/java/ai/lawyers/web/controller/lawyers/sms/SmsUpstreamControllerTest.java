package ai.lawyers.web.controller.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;

/**
 * 短信上行退订回调接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SmsUpstreamControllerTest
{
    @Mock
    private IAiCallBlacklistService blacklistService;

    @Mock
    private RedisCache redisCache;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private SmsUpstreamController controller;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(controller, "environment", new MockEnvironment());
        ReflectionTestUtils.setField(controller, "upstreamEnabled", true);
        ReflectionTestUtils.setField(controller, "upstreamToken", "");
        ReflectionTestUtils.setField(controller, "signEnabled", false);
        ReflectionTestUtils.setField(controller, "signSecret", "");
        ReflectionTestUtils.setField(controller, "rateLimitPerMin", 0);
        ReflectionTestUtils.setField(redisCache, "redisTemplate", redisTemplate);
    }

    private Map<String, Object> body(String... pairs)
    {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2)
        {
            body.put(pairs[i], pairs[i + 1]);
        }
        return body;
    }

    @Test
    void 开关关闭时静默成功()
    {
        ReflectionTestUtils.setField(controller, "upstreamEnabled", false);

        AjaxResult result = controller.receive(body("phone_number", "13800138000", "content", "T"),
                new MockHttpServletRequest());

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(blacklistService, never()).addUnsubscribe(any(), any());
    }

    @Test
    void 空请求体与缺少字段被拒绝()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals("请求体不能为空", controller.receive(null, request).get(AjaxResult.MSG_TAG));
        assertEquals("请求体不能为空",
                controller.receive(new LinkedHashMap<String, Object>(), request).get(AjaxResult.MSG_TAG));
        assertEquals("缺少 phone/content 字段",
                controller.receive(body("phone_number", "13800138000"), request).get(AjaxResult.MSG_TAG));
        assertEquals("缺少 phone/content 字段",
                controller.receive(body("content", "T"), request).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 配置令牌但未携带时拒绝()
    {
        ReflectionTestUtils.setField(controller, "upstreamToken", "secret-token");

        AjaxResult result = controller.receive(body("phone_number", "13800138000", "content", "T"),
                new MockHttpServletRequest());

        assertEquals(403, result.get(AjaxResult.CODE_TAG));
        assertEquals("上行回调校验失败", result.get(AjaxResult.MSG_TAG));
        verify(blacklistService, never()).addUnsubscribe(any(), any());
    }

    @Test
    void 查询参数令牌校验通过并写入退订名单()
    {
        ReflectionTestUtils.setField(controller, "upstreamToken", "secret-token");
        ReflectionTestUtils.setField(controller, "rateLimitPerMin", 10);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", "secret-token");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("sms:unsub:rl:13800138000")).thenReturn(1L);

        AjaxResult result = controller.receive(body("phone_number", "13800138000", "content", "T"), request);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(blacklistService).addUnsubscribe("13800138000", "短信回复T退订");
        verify(redisCache).expire("sms:unsub:rl:13800138000", 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void 非退订内容直接忽略()
    {
        AjaxResult result = controller.receive(body("phone_number", "13800138000", "content", "查询进度"),
                new MockHttpServletRequest());

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(blacklistService, never()).addUnsubscribe(any(), any());
    }

    @Test
    void 腾讯云字段与TD退订命中()
    {
        AjaxResult result = controller.receive(body("mobile", "13900139000", "text", "TD"),
                new MockHttpServletRequest());

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(blacklistService).addUnsubscribe("13900139000", "短信回复T退订");
    }

    @Test
    void 同号码退订频控超限()
    {
        ReflectionTestUtils.setField(controller, "rateLimitPerMin", 1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("sms:unsub:rl:13800138000")).thenReturn(2L);

        AjaxResult result = controller.receive(body("phone_number", "13800138000", "content", "退订"),
                new MockHttpServletRequest());

        assertEquals(429, result.get(AjaxResult.CODE_TAG));
        assertEquals("退订频率超限", result.get(AjaxResult.MSG_TAG));
        verify(blacklistService, never()).addUnsubscribe(any(), any());
    }

    @Test
    void 频控Redis异常时放行()
    {
        ReflectionTestUtils.setField(controller, "rateLimitPerMin", 10);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        AjaxResult result = controller.receive(body("phone_number", "13800138000", "content", "T"),
                new MockHttpServletRequest());

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(blacklistService).addUnsubscribe("13800138000", "短信回复T退订");
    }

    @Test
    void 黑名单服务缺失时仍返回成功()
    {
        ReflectionTestUtils.setField(controller, "blacklistService", null);

        AjaxResult result = controller.receive(body("phone_number", "13800138000", "content", "T"),
                new MockHttpServletRequest());

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
    }
}
