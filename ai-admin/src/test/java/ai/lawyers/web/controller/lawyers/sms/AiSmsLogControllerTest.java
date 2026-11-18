package ai.lawyers.web.controller.lawyers.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.sms.AiSmsLog;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsLogMapper;
import ai.lawyers.system.service.lawyers.sms.ISmsService;

/**
 * 短信发送记录接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiSmsLogControllerTest
{
    @Mock
    private AiSmsLogMapper aiSmsLogMapper;

    @Mock
    private ISmsService smsService;

    @InjectMocks
    private AiSmsLogController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiSmsLog query = new AiSmsLog();
        List<AiSmsLog> logs = new ArrayList<AiSmsLog>();
        logs.add(new AiSmsLog());
        when(aiSmsLogMapper.selectAiSmsLogList(query)).thenReturn(logs);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(logs, table.getRows());
    }

    @Test
    void 调试发送成功返回结果对象()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("phone", "13800000000");
        body.put("templateId", 12);
        body.put("recordId", 33L);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("name", "张三");
        params.put("code", 8866);
        body.put("params", params);
        SmsResult smsResult = SmsResult.success("msg-1", "尊敬的张三您好");
        when(smsService.send(eq("13800000000"), eq(12L), anyMap(), isNull(), eq(33L)))
                .thenReturn(smsResult);

        AjaxResult result = controller.send(body);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertSame(smsResult, result.get(AjaxResult.DATA_TAG));
        verify(smsService).send(eq("13800000000"), eq(12L), anyMap(), isNull(), eq(33L));
    }

    @Test
    void 调试发送失败返回错误码与原因()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("phone", "13900000000");
        when(smsService.send(eq("13900000000"), isNull(), anyMap(), isNull(), isNull()))
                .thenReturn(SmsResult.fail("短信服务未启用"));

        AjaxResult result = controller.send(body);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("短信服务未启用", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] logIds = new Long[] { 5L, 6L };
        when(aiSmsLogMapper.deleteAiSmsLogByLogIds(logIds)).thenReturn(2);

        AjaxResult result = controller.remove(logIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiSmsLogMapper).deleteAiSmsLogByLogIds(logIds);
    }
}
