package ai.lawyers.web.controller.lawyers.collab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.sign.CallbackSignUtils;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;

/**
 * 跨域转办对外回调接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiExternalTicketCallbackControllerTest
{
    @Mock
    private IAiTicketTransferService ticketTransferService;

    @Mock
    private RedisCache redisCache;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AiExternalTicketCallbackController controller;

    private Map<String, Object> callbackBody()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("idempotentKey", "fp-1");
        body.put("externalType", "court");
        body.put("externalTicketNo", "EXT-1");
        body.put("externalStatus", "accepted");
        return body;
    }

    private AiTicketTransfer transfer()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTicketNo("TK-9");
        return transfer;
    }

    @Test
    void 回调空请求体被拒绝()
    {
        assertEquals("请求体不能为空",
                controller.callback(null, new MockHttpServletRequest()).get(AjaxResult.MSG_TAG));
        assertEquals("请求体不能为空",
                controller.callback(new HashMap<String, Object>(), new MockHttpServletRequest())
                        .get(AjaxResult.MSG_TAG));
    }

    @Test
    void 回调缺少状态或幂等键被拒绝()
    {
        Map<String, Object> body = callbackBody();
        body.remove("externalStatus");
        assertEquals("缺少 externalStatus 字段",
                controller.callback(body, new MockHttpServletRequest()).get(AjaxResult.MSG_TAG));

        body.put("externalStatus", "accepted");
        body.remove("idempotentKey");
        body.remove("externalType");
        assertEquals("缺少幂等键或条线+外部工单号",
                controller.callback(body, new MockHttpServletRequest()).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 回调签名校验失败返回403()
    {
        ReflectionTestUtils.setField(controller, "signEnabled", true);
        ReflectionTestUtils.setField(controller, "signSecret", "secret");

        try (MockedStatic<CallbackSignUtils> sign = mockStatic(CallbackSignUtils.class))
        {
            sign.when(() -> CallbackSignUtils.verify(any(), any(), any(), any())).thenReturn(false);
            AjaxResult result = controller.callback(callbackBody(), new MockHttpServletRequest());

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            assertEquals("回调校验失败", result.get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 回调令牌兜底校验通过()
    {
        ReflectionTestUtils.setField(controller, "signEnabled", true);
        ReflectionTestUtils.setField(controller, "signSecret", "secret");
        ReflectionTestUtils.setField(controller, "collabToken", "tok-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Collab-Token", "tok-1");
        when(ticketTransferService.handleCallback(any(), any(), any(), any(), anyString()))
                .thenReturn(transfer());

        try (MockedStatic<CallbackSignUtils> sign = mockStatic(CallbackSignUtils.class))
        {
            sign.when(() -> CallbackSignUtils.verify(any(), any(), any(), any())).thenReturn(false);
            assertEquals(200, controller.callback(callbackBody(), request).get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 回调频控超限返回429()
    {
        ReflectionTestUtils.setField(controller, "redisCache", redisCache);
        ReflectionTestUtils.setField(redisCache, "redisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("collab:cb:rl:fp-1")).thenReturn(31L);

        AjaxResult result = controller.callback(callbackBody(), new MockHttpServletRequest());

        assertEquals(429, result.get(AjaxResult.CODE_TAG));
        assertEquals("请求频率超限", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 回调频控首次计数设置过期时间()
    {
        ReflectionTestUtils.setField(controller, "redisCache", redisCache);
        ReflectionTestUtils.setField(redisCache, "redisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("collab:cb:rl:fp-1")).thenReturn(1L);
        when(ticketTransferService.handleCallback(any(), any(), any(), any(), anyString()))
                .thenReturn(transfer());

        assertEquals(200, controller.callback(callbackBody(), new MockHttpServletRequest())
                .get(AjaxResult.CODE_TAG));
        verify(redisCache).expire(eq("collab:cb:rl:fp-1"), eq(60L), any());
    }

    @Test
    void 回调Redis异常时放行()
    {
        ReflectionTestUtils.setField(controller, "redisCache", redisCache);
        ReflectionTestUtils.setField(redisCache, "redisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        when(ticketTransferService.handleCallback(any(), any(), any(), any(), anyString()))
                .thenReturn(transfer());

        assertEquals(200, controller.callback(callbackBody(), new MockHttpServletRequest())
                .get(AjaxResult.CODE_TAG));
    }

    @Test
    void 回调未找到转办记录()
    {
        when(ticketTransferService.handleCallback(any(), any(), any(), any(), anyString())).thenReturn(null);

        assertEquals("未找到对应转办记录",
                controller.callback(callbackBody(), new MockHttpServletRequest()).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 来单缺少必填字段被拒绝()
    {
        assertEquals("请求体不能为空",
                controller.receive(null, new MockHttpServletRequest()).get(AjaxResult.MSG_TAG));

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("externalType", "12345");
        assertEquals("缺少 externalType/externalTicketNo 字段",
                controller.receive(body, new MockHttpServletRequest()).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 来单校验失败返回403()
    {
        ReflectionTestUtils.setField(controller, "collabToken", "tok-1");
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("externalType", "12345");
        body.put("externalTicketNo", "X-1");

        assertEquals(403, controller.receive(body, new MockHttpServletRequest()).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 来单成功返回工单号()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("externalType", "12345");
        body.put("externalTicketNo", "X-1");
        body.put("callerNumber", "13800000000");
        body.put("callerName", "张三");
        body.put("title", "劳动纠纷咨询");
        body.put("content", "被拖欠工资");
        body.put("priority", "2");
        when(ticketTransferService.receiveInbound(any(), any(), any(), nullable(String.class),
                any(), any(), any(), any(), anyString())).thenReturn(transfer());

        AjaxResult result = controller.receive(body, new MockHttpServletRequest());

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals("来单已受理", result.get(AjaxResult.MSG_TAG));
        assertEquals("TK-9", result.get(AjaxResult.DATA_TAG));
    }
}
