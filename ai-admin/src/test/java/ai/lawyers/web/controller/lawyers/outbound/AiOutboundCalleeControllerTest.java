package ai.lawyers.web.controller.lawyers.outbound;

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
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundCalleeService;

/**
 * 外呼号码接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiOutboundCalleeControllerTest
{
    @Mock
    private IAiOutboundCalleeService aiOutboundCalleeService;

    @InjectMocks
    private AiOutboundCalleeController controller;

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
        AiOutboundCallee query = new AiOutboundCallee();
        List<AiOutboundCallee> callees = new ArrayList<AiOutboundCallee>();
        callees.add(new AiOutboundCallee());
        when(aiOutboundCalleeService.selectAiOutboundCalleeList(query)).thenReturn(callees);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(callees, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiOutboundCalleeService.selectAiOutboundCalleeList(any(AiOutboundCallee.class)))
                .thenReturn(List.of(new AiOutboundCallee()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiOutboundCallee());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情与按任务查询透传服务结果()
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        callee.setCalleeId(7L);
        List<AiOutboundCallee> callees = new ArrayList<AiOutboundCallee>();
        when(aiOutboundCalleeService.selectAiOutboundCalleeByCalleeId(7L)).thenReturn(callee);
        when(aiOutboundCalleeService.selectAiOutboundCalleeByTaskId(9L)).thenReturn(callees);

        assertSame(callee, controller.getInfo(7L).get(AjaxResult.DATA_TAG));
        assertSame(callees, controller.getCalleesByTaskId(9L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        when(aiOutboundCalleeService.insertAiOutboundCallee(any(AiOutboundCallee.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(callee);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", callee.getCreateBy());
    }

    @Test
    void 批量导入按任务写入()
    {
        List<AiOutboundCallee> callees = new ArrayList<AiOutboundCallee>();
        callees.add(new AiOutboundCallee());
        when(aiOutboundCalleeService.batchInsertCallees(9L, callees)).thenReturn(1);

        AjaxResult result = controller.batchAdd(9L, callees);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiOutboundCalleeService).batchInsertCallees(9L, callees);
    }

    @Test
    void 按任务删除外呼号码()
    {
        when(aiOutboundCalleeService.deleteAiOutboundCalleeByTaskId(9L)).thenReturn(3);

        AjaxResult result = controller.removeByTaskId(9L);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiOutboundCalleeService).deleteAiOutboundCalleeByTaskId(9L);
    }
}
