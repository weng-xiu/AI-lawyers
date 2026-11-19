package ai.lawyers.web.controller.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.sign.CallbackSignUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

/**
 * 外呼调度接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class CallDispatchControllerTest
{
    @Mock
    private ICallDispatchService callDispatchService;

    @Mock
    private AiCallDialLogMapper dialLogMapper;

    @InjectMocks
    private CallDispatchController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private Map<String, Object> event(String uuid, String event)
    {
        Map<String, Object> param = new LinkedHashMap<String, Object>();
        param.put("callUuid", uuid);
        param.put("event", event);
        return param;
    }

    @Test
    void 外呼与直呼返回结果并写操作人()
    {
        DialRequest queueRequest = new DialRequest();
        DialRequest queueFail = new DialRequest();
        DialRequest directRequest = new DialRequest();
        DialRequest directFail = new DialRequest();
        DialResult queued = DialResult.ok("uuid-1");
        when(callDispatchService.dialWithQueue(queueRequest)).thenReturn(queued);
        when(callDispatchService.dialWithQueue(queueFail)).thenReturn(DialResult.fail("NO_TRUNK", "无可用线路"));
        when(callDispatchService.dial(directRequest)).thenReturn(DialResult.ok("uuid-2"));
        when(callDispatchService.dial(directFail)).thenReturn(DialResult.fail(null, "线路故障"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("agent");
            AjaxResult ok = controller.dial(queueRequest);
            assertEquals(200, ok.get(AjaxResult.CODE_TAG));
            assertEquals("外呼已发起", ok.get(AjaxResult.MSG_TAG));
            assertSame(queued, ok.get(AjaxResult.DATA_TAG));

            AjaxResult queueError = controller.dial(queueFail);
            assertEquals(500, queueError.get(AjaxResult.CODE_TAG));
            assertEquals("无可用线路", queueError.get(AjaxResult.MSG_TAG));

            AjaxResult directOk = controller.dialDirect(directRequest);
            assertEquals(200, directOk.get(AjaxResult.CODE_TAG));
            assertEquals("外呼已发起", directOk.get(AjaxResult.MSG_TAG));

            AjaxResult directError = controller.dialDirect(directFail);
            assertEquals(500, directError.get(AjaxResult.CODE_TAG));
            assertEquals("线路故障", directError.get(AjaxResult.MSG_TAG));
        }
        assertEquals("agent", queueRequest.getCreateBy());
        assertEquals("agent", directRequest.getCreateBy());
    }

    @Test
    void 挂断校验与结果()
    {
        assertEquals("callUuid 不能为空", controller.hangup(new LinkedHashMap<String, Object>())
                .get(AjaxResult.MSG_TAG));
        when(callDispatchService.hangup("uuid-1")).thenReturn(true);
        when(callDispatchService.hangup("uuid-2")).thenReturn(false);

        Map<String, Object> param = new LinkedHashMap<String, Object>();
        param.put("callUuid", "uuid-1");
        AjaxResult ok = controller.hangup(param);
        assertEquals(200, ok.get(AjaxResult.CODE_TAG));
        assertEquals("已挂断", ok.get(AjaxResult.MSG_TAG));

        Map<String, Object> failParam = new LinkedHashMap<String, Object>();
        failParam.put("callUuid", "uuid-2");
        assertEquals("挂断失败", controller.hangup(failParam).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 调度器状态返回队列与并发()
    {
        when(callDispatchService.getQueueSize()).thenReturn(3);
        when(callDispatchService.getGlobalConcurrent()).thenReturn(7);

        AjaxResult result = controller.dispatchStatus();

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals(3, result.get("queueSize"));
        assertEquals(7, result.get("globalConcurrent"));
    }

    @Test
    void 网关事件回调参数校验与透传()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals("callUuid 与 event 不能为空", controller.event(new LinkedHashMap<String, Object>(), request)
                .get(AjaxResult.MSG_TAG));
        assertEquals("callUuid 与 event 不能为空", controller.event(event(null, "ANSWERED"), request)
                .get(AjaxResult.MSG_TAG));

        Map<String, Object> param = event("uuid-1", "ANSWERED");
        assertEquals(200, controller.event(param, request).get(AjaxResult.CODE_TAG));
        verify(callDispatchService).onCallEvent("uuid-1", "ANSWERED", param);
    }

    @Test
    void 网关事件回调签名校验()
    {
        ReflectionTestUtils.setField(controller, "callbackSignEnabled", true);
        ReflectionTestUtils.setField(controller, "callbackSignSecret", "secret-key");
        Map<String, Object> param = event("uuid-1", "HANGUP");

        AjaxResult rejected = controller.event(param, new MockHttpServletRequest());
        assertEquals(403, rejected.get(AjaxResult.CODE_TAG));
        assertEquals("回调签名校验失败", rejected.get(AjaxResult.MSG_TAG));

        String timestamp = String.valueOf(System.currentTimeMillis());
        MockHttpServletRequest signed = new MockHttpServletRequest();
        signed.addHeader("X-Callback-Timestamp", timestamp);
        signed.addHeader("X-Callback-Sign", CallbackSignUtils.sign("secret-key", "uuid-1|HANGUP", timestamp));

        assertEquals(200, controller.event(param, signed).get(AjaxResult.CODE_TAG));
        verify(callDispatchService).onCallEvent("uuid-1", "HANGUP", param);
    }

    @Test
    void 拨号日志列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallDialLog query = new AiCallDialLog();
        List<AiCallDialLog> list = new ArrayList<AiCallDialLog>();
        list.add(new AiCallDialLog());
        when(dialLogMapper.selectAiCallDialLogList(query)).thenReturn(list);

        TableDataInfo table = controller.dialLogList(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 拨号日志导出Excel()
    {
        when(dialLogMapper.selectAiCallDialLogList(any(AiCallDialLog.class)))
                .thenReturn(List.of(new AiCallDialLog()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.dialLogExport(response, new AiCallDialLog());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
}
