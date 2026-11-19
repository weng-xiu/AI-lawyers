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
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.domain.lawyers.AiRiskWarning;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.service.lawyers.IAiRiskWarningService;

/**
 * 风险预警记录接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiRiskWarningControllerTest
{
    @Mock
    private IAiRiskWarningService aiRiskWarningService;

    @InjectMocks
    private AiRiskWarningController controller;

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
        AiRiskWarning query = new AiRiskWarning();
        List<AiRiskWarning> list = new ArrayList<AiRiskWarning>();
        list.add(new AiRiskWarning());
        when(aiRiskWarningService.selectAiRiskWarningList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiRiskWarningService.selectAiRiskWarningList(any(AiRiskWarning.class)))
                .thenReturn(List.of(new AiRiskWarning()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiRiskWarning());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiRiskWarning warning = new AiRiskWarning();
        warning.setWarningId(6L);
        when(aiRiskWarningService.selectAiRiskWarningByWarningId(6L)).thenReturn(warning);

        AjaxResult result = controller.getInfo(6L);

        assertSame(warning, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增与修改返回影响行数()
    {
        AiRiskWarning warning = new AiRiskWarning();
        when(aiRiskWarningService.insertAiRiskWarning(warning)).thenReturn(1);
        when(aiRiskWarningService.updateAiRiskWarning(warning)).thenReturn(0);

        assertEquals(200, controller.add(warning).get(AjaxResult.CODE_TAG));
        assertEquals(500, controller.edit(warning).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] warningIds = new Long[] { 1L, 2L };
        when(aiRiskWarningService.deleteAiRiskWarningByWarningIds(warningIds)).thenReturn(2);

        AjaxResult result = controller.remove(warningIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiRiskWarningService).deleteAiRiskWarningByWarningIds(warningIds);
    }

    @Test
    void 按建议条线查询可转办机构()
    {
        List<AiExternalOrg> orgs = new ArrayList<AiExternalOrg>();
        when(aiRiskWarningService.selectTransferOrgs("MEDIATION")).thenReturn(orgs);

        AjaxResult result = controller.transferOrgs("MEDIATION");

        assertSame(orgs, result.get(AjaxResult.DATA_TAG));
        verify(aiRiskWarningService).selectTransferOrgs("MEDIATION");
    }

    @Test
    void 一键转办解析参数并携带操作人()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("warningId", 6);
        body.put("orgId", "8");
        body.put("remark", "建议转调解");
        AiTicketTransfer transfer = new AiTicketTransfer();
        when(aiRiskWarningService.transferByWarning(6L, 8L, "建议转调解", "admin")).thenReturn(transfer);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.transfer(body);

            assertEquals("转办已发起", result.get(AjaxResult.MSG_TAG));
            assertSame(transfer, result.get(AjaxResult.DATA_TAG));
        }
        verify(aiRiskWarningService).transferByWarning(6L, 8L, "建议转调解", "admin");
    }

    @Test
    void 转办缺少字段时传空值()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("remark", "  ");
        when(aiRiskWarningService.transferByWarning(null, null, "  ", "admin")).thenReturn(null);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            controller.transfer(body);
        }
        verify(aiRiskWarningService).transferByWarning(null, null, "  ", "admin");
    }
}
