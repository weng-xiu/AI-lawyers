package ai.lawyers.web.controller.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import ai.lawyers.framework.web.service.SysPasswordService;
import ai.lawyers.system.domain.SysLogininfor;
import ai.lawyers.system.service.ISysLogininforService;

/**
 * 登录日志接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysLogininforControllerTest
{
    @Mock
    private ISysLogininforService logininforService;

    @Mock
    private SysPasswordService passwordService;

    @InjectMocks
    private SysLogininforController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysLogininfor query = new SysLogininfor();
        List<SysLogininfor> list = new ArrayList<SysLogininfor>();
        list.add(new SysLogininfor());
        when(logininforService.selectLogininforList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        SysLogininfor info = new SysLogininfor();
        info.setUserName("admin");
        when(logininforService.selectLogininforList(any(SysLogininfor.class)))
                .thenReturn(Arrays.asList(info));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysLogininfor());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] infoIds = new Long[] { 1L, 2L };
        when(logininforService.deleteLogininforByIds(infoIds)).thenReturn(2);

        AjaxResult result = controller.remove(infoIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(logininforService).deleteLogininforByIds(infoIds);
    }

    @Test
    void 清空日志与解锁用户()
    {
        AjaxResult clean = controller.clean();
        AjaxResult unlock = controller.unlock("admin");

        assertEquals(200, clean.get(AjaxResult.CODE_TAG));
        assertEquals(200, unlock.get(AjaxResult.CODE_TAG));
        verify(logininforService).cleanLogininfor();
        verify(passwordService).clearLoginRecordCache("admin");
    }
}
