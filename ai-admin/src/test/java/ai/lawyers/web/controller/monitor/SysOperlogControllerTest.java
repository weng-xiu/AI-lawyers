package ai.lawyers.web.controller.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.SysOperLog;
import ai.lawyers.system.service.ISysOperLogService;

/**
 * 操作日志接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysOperlogControllerTest
{
    @Mock
    private ISysOperLogService operLogService;

    @InjectMocks
    private SysOperlogController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysOperLog query = new SysOperLog();
        List<SysOperLog> logs = new ArrayList<SysOperLog>();
        logs.add(new SysOperLog());
        logs.add(new SysOperLog());
        when(operLogService.selectOperLogList(query)).thenReturn(logs);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(2, table.getTotal());
        assertEquals(logs, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        SysOperLog log = new SysOperLog();
        log.setTitle("用户登录");
        when(operLogService.selectOperLogList(any(SysOperLog.class)))
                .thenReturn(Arrays.asList(log));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysOperLog());

        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] operIds = new Long[] { 1L, 2L };
        when(operLogService.deleteOperLogByIds(operIds)).thenReturn(2);

        AjaxResult result = controller.remove(operIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(operLogService).deleteOperLogByIds(operIds);
    }

    @Test
    void 清空操作日志成功()
    {
        AjaxResult result = controller.clean();

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(operLogService).cleanOperLog();
    }
}
