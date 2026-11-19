package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
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
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.service.lawyers.IAiCallLedgerService;

/**
 * 咨询台账接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallLedgerControllerTest
{
    @Mock
    private IAiCallLedgerService aiCallLedgerService;

    @InjectMocks
    private AiCallLedgerController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallLedger query = new AiCallLedger();
        List<AiCallLedger> list = new ArrayList<AiCallLedger>();
        list.add(new AiCallLedger());
        when(aiCallLedgerService.selectAiCallLedgerList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiCallLedgerService.selectAiCallLedgerList(any(AiCallLedger.class)))
                .thenReturn(List.of(new AiCallLedger()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiCallLedger());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情与编号生成透传服务结果()
    {
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerId(8L);
        when(aiCallLedgerService.selectAiCallLedgerByLedgerId(8L)).thenReturn(ledger);
        when(aiCallLedgerService.generateLedgerNo()).thenReturn("LZ20260101001");

        assertSame(ledger, controller.getInfo(8L).get(AjaxResult.DATA_TAG));
        assertEquals("LZ20260101001", controller.generateLedgerNo().get(AjaxResult.MSG_TAG));
    }

    @Test
    void 新增与修改写入操作人()
    {
        AiCallLedger ledger = new AiCallLedger();
        when(aiCallLedgerService.insertAiCallLedger(ledger)).thenReturn(1);
        when(aiCallLedgerService.updateAiCallLedger(ledger)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("lawyer"));
            assertEquals(200, controller.add(ledger).get(AjaxResult.CODE_TAG));
            ledger.setCreateBy(null);
            controller.edit(ledger);
        }
        assertEquals("lawyer", ledger.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] ledgerIds = new Long[] { 1L, 2L };
        when(aiCallLedgerService.deleteAiCallLedgerByLedgerIds(ledgerIds)).thenReturn(2);

        assertEquals(200, controller.remove(ledgerIds).get(AjaxResult.CODE_TAG));
        verify(aiCallLedgerService).deleteAiCallLedgerByLedgerIds(ledgerIds);
    }

    @Test
    void 模板与自动填充透传服务结果()
    {
        List<Map<String, Object>> templates = new ArrayList<Map<String, Object>>();
        AiCallLedger filled = new AiCallLedger();
        when(aiCallLedgerService.selectLedgerTemplates()).thenReturn(templates);
        when(aiCallLedgerService.autoFillByRecordId(100L)).thenReturn(filled);

        assertSame(templates, controller.templates().get(AjaxResult.DATA_TAG));
        assertSame(filled, controller.autoFill(100L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 转工单成功返回工单ID()
    {
        when(aiCallLedgerService.transferToTicket(8L, "lawyer")).thenReturn(66L);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("lawyer"));
            AjaxResult result = controller.transferTicket(8L);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            assertEquals(66L, data.get("ticketId"));
        }
    }

    @Test
    void 转工单失败返回错误提示()
    {
        when(aiCallLedgerService.transferToTicket(9L, "lawyer")).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("lawyer"));
            AjaxResult result = controller.transferTicket(9L);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            assertEquals("台账转工单失败，未找到对应台账记录", result.get(AjaxResult.MSG_TAG));
        }
    }
}
