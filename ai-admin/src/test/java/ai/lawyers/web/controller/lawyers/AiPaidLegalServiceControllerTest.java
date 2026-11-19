package ai.lawyers.web.controller.lawyers;

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
import ai.lawyers.system.domain.lawyers.AiPaidLegalService;
import ai.lawyers.system.service.lawyers.IAiPaidLegalService;

/**
 * 有偿法律服务接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiPaidLegalServiceControllerTest
{
    @Mock
    private IAiPaidLegalService paidService;

    @InjectMocks
    private AiPaidLegalServiceController controller;

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
        AiPaidLegalService query = new AiPaidLegalService();
        List<AiPaidLegalService> services = new ArrayList<AiPaidLegalService>();
        services.add(new AiPaidLegalService());
        when(paidService.selectAiPaidLegalServiceList(query)).thenReturn(services);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(services, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(paidService.selectAiPaidLegalServiceList(any(AiPaidLegalService.class)))
                .thenReturn(List.of(new AiPaidLegalService()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiPaidLegalService());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiPaidLegalService service = new AiPaidLegalService();
        service.setPaidId(5L);
        when(paidService.selectAiPaidLegalServiceByPaidId(5L)).thenReturn(service);

        AjaxResult result = controller.getInfo(5L);

        assertSame(service, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiPaidLegalService service = new AiPaidLegalService();
        when(paidService.insertAiPaidLegalService(any(AiPaidLegalService.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(service);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", service.getCreateBy());
    }

    @Test
    void 修改写入更新人()
    {
        AiPaidLegalService service = new AiPaidLegalService();
        when(paidService.updateAiPaidLegalService(any(AiPaidLegalService.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            AjaxResult result = controller.edit(service);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", service.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] paidIds = new Long[] { 1L, 2L };
        when(paidService.deleteAiPaidLegalServiceByPaidIds(paidIds)).thenReturn(2);

        AjaxResult result = controller.remove(paidIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(paidService).deleteAiPaidLegalServiceByPaidIds(paidIds);
    }
}
