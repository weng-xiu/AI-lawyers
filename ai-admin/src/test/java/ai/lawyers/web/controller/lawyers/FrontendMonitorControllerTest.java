package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.monitor.FrontendReportPayload;
import ai.lawyers.system.service.lawyers.monitor.FrontendMonitorService;

/**
 * 前端可观测上报接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class FrontendMonitorControllerTest
{
    @Mock
    private FrontendMonitorService frontendMonitorService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FrontendMonitorController controller;

    @Test
    void 请求体超限返回413()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(64L * 1024L + 1L);

        AjaxResult result = controller.report("{}", request);

        assertEquals(413, result.get(AjaxResult.CODE_TAG));
        assertEquals("上报内容过大", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 空请求体返回400()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(0L);

        AjaxResult empty = controller.report(null, request);
        AjaxResult emptyText = controller.report("", request);

        assertEquals(400, empty.get(AjaxResult.CODE_TAG));
        assertEquals("上报内容为空", empty.get(AjaxResult.MSG_TAG));
        assertEquals(400, emptyText.get(AjaxResult.CODE_TAG));
    }

    @Test
    void 非法JSON返回400并丢弃()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(12L);
        when(request.getRemoteAddr()).thenReturn("10.0.0.8");
        try
        {
            when(objectMapper.readValue(anyString(), eq(FrontendReportPayload.class)))
                    .thenThrow(new JsonParseException("bad body"));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }

        AjaxResult result = controller.report("{bad json}", request);

        assertEquals(400, result.get(AjaxResult.CODE_TAG));
        assertEquals("上报内容非法", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 正常上报返回受理数量()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(30L);
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");
        FrontendReportPayload payload = new FrontendReportPayload();
        try
        {
            when(objectMapper.readValue(anyString(), eq(FrontendReportPayload.class))).thenReturn(payload);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
        when(frontendMonitorService.report(any(FrontendReportPayload.class), eq("10.0.0.9"))).thenReturn(3);

        AjaxResult result = controller.report("{\"events\":[]}", request);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals(3, result.get(AjaxResult.DATA_TAG));
        verify(frontendMonitorService).report(payload, "10.0.0.9");
    }
}
