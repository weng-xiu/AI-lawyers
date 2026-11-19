package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;

/**
 * 通话黑白名单接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallBlacklistControllerTest
{
    @Mock
    private IAiCallBlacklistService blacklistService;

    @InjectMocks
    private AiCallBlacklistController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallBlacklist query = new AiCallBlacklist();
        List<AiCallBlacklist> list = new ArrayList<AiCallBlacklist>();
        list.add(new AiCallBlacklist());
        when(blacklistService.selectList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 详情透传服务结果()
    {
        AiCallBlacklist entity = new AiCallBlacklist();
        entity.setId(5L);
        when(blacklistService.selectById(5L)).thenReturn(entity);

        AjaxResult result = controller.getInfo(5L);

        assertSame(entity, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiCallBlacklist entity = new AiCallBlacklist();
        when(blacklistService.insert(any(AiCallBlacklist.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            AjaxResult result = controller.add(entity);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", entity.getCreateBy());
    }

    @Test
    void 修改写入更新人()
    {
        AiCallBlacklist entity = new AiCallBlacklist();
        when(blacklistService.update(any(AiCallBlacklist.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("auditor");
            AjaxResult result = controller.edit(entity);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", entity.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(blacklistService.deleteByIds(ids)).thenReturn(2);

        AjaxResult result = controller.remove(ids);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(blacklistService).deleteByIds(ids);
    }

    @Test
    void 黑名单号码优先返回blacklisted()
    {
        when(blacklistService.isBlacklisted("13800000000")).thenReturn(true);

        AjaxResult result = controller.check("13800000000");

        assertEquals("blacklisted", result.get("status"));
        assertEquals("13800000000", result.get("phoneNumber"));
        verify(blacklistService, never()).isWhitelisted("13800000000");
    }

    @Test
    void 非黑名单号码依次判断白名单()
    {
        when(blacklistService.isBlacklisted("13900000000")).thenReturn(false);
        when(blacklistService.isWhitelisted("13900000000")).thenReturn(true);
        when(blacklistService.isBlacklisted("13700000000")).thenReturn(false);
        when(blacklistService.isWhitelisted("13700000000")).thenReturn(false);

        assertEquals("whitelisted", controller.check("13900000000").get("status"));
        assertEquals("normal", controller.check("13700000000").get("status"));
    }

    @Test
    void 空号码直接返回normal()
    {
        AjaxResult result = controller.check("");

        assertEquals("normal", result.get("status"));
        verify(blacklistService, never()).isBlacklisted(any());
    }
}
