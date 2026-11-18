package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppressLog;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;

/**
 * 高频置底管理接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiHotspotSuppressControllerTest
{
    @Mock
    private IAiHotspotSuppressService hotspotSuppressService;

    @InjectMocks
    private AiHotspotSuppressController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 规则列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiHotspotSuppress query = new AiHotspotSuppress();
        List<AiHotspotSuppress> rules = new ArrayList<AiHotspotSuppress>();
        rules.add(new AiHotspotSuppress());
        when(hotspotSuppressService.selectSuppressList(query)).thenReturn(rules);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(rules, table.getRows());
    }

    @Test
    void 命中日志列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiHotspotSuppressLog query = new AiHotspotSuppressLog();
        List<AiHotspotSuppressLog> logs = new ArrayList<AiHotspotSuppressLog>();
        logs.add(new AiHotspotSuppressLog());
        when(hotspotSuppressService.selectLogList(query)).thenReturn(logs);

        TableDataInfo table = controller.logList(query);

        assertEquals(1, table.getTotal());
        assertEquals(logs, table.getRows());
    }

    @Test
    void 规则详情透传服务结果()
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setSuppressId(5L);
        when(hotspotSuppressService.selectSuppressById(5L)).thenReturn(rule);

        AjaxResult result = controller.getInfo(5L);

        assertSame(rule, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增规则写入操作人()
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        when(hotspotSuppressService.insertSuppress(any(AiHotspotSuppress.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            AjaxResult result = controller.add(rule);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", rule.getCreateBy());
    }

    @Test
    void 修改规则写入更新人()
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setSuppressId(8L);
        when(hotspotSuppressService.updateSuppress(any(AiHotspotSuppress.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("auditor");
            AjaxResult result = controller.edit(rule);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", rule.getUpdateBy());
    }

    @Test
    void 批量删除规则返回影响行数()
    {
        Long[] suppressIds = new Long[] { 1L, 2L };
        when(hotspotSuppressService.deleteSuppressByIds(suppressIds)).thenReturn(2);

        AjaxResult result = controller.remove(suppressIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(hotspotSuppressService).deleteSuppressByIds(suppressIds);
    }
}
