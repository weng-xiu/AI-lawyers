package ai.lawyers.web.controller.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntentionLog;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrIntentionLogService;

/**
 * IVR 意图识别日志接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiIvrIntentionLogControllerTest
{
    @Mock
    private IAiIvrIntentionLogService aiIvrIntentionLogService;

    @InjectMocks
    private AiIvrIntentionLogController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiIvrIntentionLog buildLog(Long logId)
    {
        AiIvrIntentionLog log = new AiIvrIntentionLog();
        log.setLogId(logId);
        log.setRecordId(100L);
        log.setMatchedIntention("consult");
        log.setConfidence(new BigDecimal("0.92"));
        return log;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiIvrIntentionLog query = new AiIvrIntentionLog();
        List<AiIvrIntentionLog> logs = new ArrayList<AiIvrIntentionLog>();
        logs.add(buildLog(1L));
        logs.add(buildLog(2L));
        when(aiIvrIntentionLogService.selectAiIvrIntentionLogList(query)).thenReturn(logs);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(2, table.getTotal());
        assertEquals(logs, table.getRows());
    }

    @Test
    void 按通话记录查询返回日志列表()
    {
        List<AiIvrIntentionLog> logs = Arrays.asList(buildLog(3L));
        when(aiIvrIntentionLogService.selectAiIvrIntentionLogByRecordId(300L)).thenReturn(logs);

        AjaxResult result = controller.getByRecordId(300L);

        assertSame(logs, result.get(AjaxResult.DATA_TAG));
        verify(aiIvrIntentionLogService).selectAiIvrIntentionLogByRecordId(300L);
    }

    @Test
    void 按主键查询单条日志()
    {
        AiIvrIntentionLog log = buildLog(8L);
        when(aiIvrIntentionLogService.selectAiIvrIntentionLogByLogId(8L)).thenReturn(log);

        AjaxResult result = controller.getInfo(8L);

        assertSame(log, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增日志写入操作人()
    {
        SysUser sysUser = new SysUser();
        sysUser.setUserName("tester");
        LoginUser loginUser = new LoginUser(1L, 2L, sysUser, null);
        AiIvrIntentionLog log = buildLog(null);
        when(aiIvrIntentionLogService.insertAiIvrIntentionLog(any(AiIvrIntentionLog.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
            AjaxResult result = controller.add(log);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("tester", log.getCreateBy());
        verify(aiIvrIntentionLogService).insertAiIvrIntentionLog(log);
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiIvrIntentionLogService.selectAiIvrIntentionLogList(any(AiIvrIntentionLog.class)))
                .thenReturn(Arrays.asList(buildLog(9L)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiIvrIntentionLog());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
}
