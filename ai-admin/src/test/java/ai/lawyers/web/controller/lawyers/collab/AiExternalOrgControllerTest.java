package ai.lawyers.web.controller.lawyers.collab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiExternalOrg;
import ai.lawyers.system.service.lawyers.IAiExternalOrgService;

/**
 * 协同外部机构台账接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiExternalOrgControllerTest
{
    @Mock
    private IAiExternalOrgService externalOrgService;

    @InjectMocks
    private AiExternalOrgController controller;

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
        AiExternalOrg query = new AiExternalOrg();
        List<AiExternalOrg> orgs = new ArrayList<AiExternalOrg>();
        orgs.add(new AiExternalOrg());
        when(externalOrgService.selectAiExternalOrgList(query)).thenReturn(orgs);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(orgs, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        AiExternalOrg org = new AiExternalOrg();
        org.setOrgName("某调解中心");
        when(externalOrgService.selectAiExternalOrgList(any(AiExternalOrg.class)))
                .thenReturn(List.of(org));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiExternalOrg());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiExternalOrg org = new AiExternalOrg();
        org.setOrgId(6L);
        when(externalOrgService.selectAiExternalOrgByOrgId(6L)).thenReturn(org);

        AjaxResult result = controller.getInfo(6L);

        assertSame(org, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiExternalOrg org = new AiExternalOrg();
        when(externalOrgService.insertAiExternalOrg(any(AiExternalOrg.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(org);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", org.getCreateBy());
        verify(externalOrgService).insertAiExternalOrg(org);
    }

    @Test
    void 修改写入更新人()
    {
        AiExternalOrg org = new AiExternalOrg();
        org.setOrgId(8L);
        when(externalOrgService.updateAiExternalOrg(any(AiExternalOrg.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            AjaxResult result = controller.edit(org);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", org.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] orgIds = new Long[] { 1L, 2L };
        when(externalOrgService.deleteAiExternalOrgByOrgIds(orgIds)).thenReturn(2);

        AjaxResult result = controller.remove(orgIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(externalOrgService).deleteAiExternalOrgByOrgIds(orgIds);
    }
}
