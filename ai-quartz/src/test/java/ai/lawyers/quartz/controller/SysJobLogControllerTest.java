package ai.lawyers.quartz.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.quartz.domain.SysJobLog;
import ai.lawyers.quartz.service.ISysJobLogService;

/**
 * 定时任务调度日志接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysJobLogControllerTest
{
    @Mock
    private ISysJobLogService jobLogService;

    @InjectMocks
    private SysJobLogController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private SysJobLog jobLog(Long jobLogId)
    {
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobLogId(jobLogId);
        jobLog.setJobName("测试任务");
        jobLog.setJobGroup("DEFAULT");
        jobLog.setStatus("0");
        return jobLog;
    }

    @Test
    void 调度日志列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysJobLog query = new SysJobLog();
        List<SysJobLog> list = new ArrayList<SysJobLog>();
        list.add(jobLog(1L));
        when(jobLogService.selectJobLogList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 导出调度日志Excel()
    {
        when(jobLogService.selectJobLogList(any(SysJobLog.class))).thenReturn(List.of(jobLog(1L)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysJobLog());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 调度日志详情()
    {
        SysJobLog jobLog = jobLog(1L);
        when(jobLogService.selectJobLogById(1L)).thenReturn(jobLog);

        assertSame(jobLog, controller.getInfo(1L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 删除与清空调度日志()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(jobLogService.deleteJobLogByIds(ids)).thenReturn(2);

        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        assertEquals(200, controller.clean().get(AjaxResult.CODE_TAG));
        verify(jobLogService).cleanJobLog();
    }
}
