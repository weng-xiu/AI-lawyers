package ai.lawyers.web.controller.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrExecutionLog;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrExecutionLogService;

/**
 * IVR 执行日志接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiIvrExecutionLogControllerTest
{
    @Mock
    private IAiIvrExecutionLogService aiIvrExecutionLogService;

    @InjectMocks
    private AiIvrExecutionLogController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiIvrExecutionLog buildLog(Long execId)
    {
        AiIvrExecutionLog log = new AiIvrExecutionLog();
        log.setExecId(execId);
        log.setSessionId("sess-001");
        return log;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiIvrExecutionLog query = new AiIvrExecutionLog();
        List<AiIvrExecutionLog> logs = new ArrayList<AiIvrExecutionLog>();
        logs.add(buildLog(1L));
        logs.add(buildLog(2L));
        when(aiIvrExecutionLogService.selectAiIvrExecutionLogList(query)).thenReturn(logs);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(2, table.getTotal());
        assertEquals(logs, table.getRows());
    }

    @Test
    void 按主键与会话分别查询()
    {
        AiIvrExecutionLog log = buildLog(6L);
        when(aiIvrExecutionLogService.selectAiIvrExecutionLogByExecId(6L)).thenReturn(log);
        when(aiIvrExecutionLogService.selectAiIvrExecutionLogBySessionId("sess-001")).thenReturn(log);

        assertSame(log, controller.getInfo(6L).get(AjaxResult.DATA_TAG));
        assertSame(log, controller.getBySessionId("sess-001").get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        SysUser user = new SysUser();
        user.setUserName("ivr-admin");
        AiIvrExecutionLog log = buildLog(null);
        when(aiIvrExecutionLogService.insertAiIvrExecutionLog(any(AiIvrExecutionLog.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(new LoginUser(3L, 4L, user, null));
            AjaxResult result = controller.add(log);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("ivr-admin", log.getCreateBy());
        verify(aiIvrExecutionLogService).insertAiIvrExecutionLog(log);
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiIvrExecutionLogService.selectAiIvrExecutionLogList(any(AiIvrExecutionLog.class)))
                .thenReturn(Arrays.asList(buildLog(9L)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiIvrExecutionLog());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
}
