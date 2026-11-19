package ai.lawyers.web.controller.lawyers.ivr;

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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;

/**
 * IVR 流程接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiIvrFlowControllerTest
{
    @Mock
    private IAiIvrFlowService aiIvrFlowService;

    @InjectMocks
    private AiIvrFlowController controller;

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
        AiIvrFlow query = new AiIvrFlow();
        List<AiIvrFlow> list = new ArrayList<AiIvrFlow>();
        list.add(new AiIvrFlow());
        when(aiIvrFlowService.selectAiIvrFlowList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiIvrFlowService.selectAiIvrFlowList(any(AiIvrFlow.class)))
                .thenReturn(List.of(new AiIvrFlow()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiIvrFlow());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情与发布状态查询透传服务结果()
    {
        AiIvrFlow flow = new AiIvrFlow();
        flow.setFlowId(3L);
        List<AiIvrFlow> published = new ArrayList<AiIvrFlow>();
        when(aiIvrFlowService.selectAiIvrFlowByFlowId(3L)).thenReturn(flow);
        when(aiIvrFlowService.selectPublishedFlows()).thenReturn(published);
        when(aiIvrFlowService.selectDefaultFlow()).thenReturn(flow);

        assertSame(flow, controller.getInfo(3L).get(AjaxResult.DATA_TAG));
        assertSame(published, controller.getPublishedFlows().get(AjaxResult.DATA_TAG));
        assertSame(flow, controller.getDefaultFlow().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiIvrFlow flow = new AiIvrFlow();
        when(aiIvrFlowService.insertAiIvrFlow(any(AiIvrFlow.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(flow);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", flow.getCreateBy());
    }

    @Test
    void 修改写入更新人()
    {
        AiIvrFlow flow = new AiIvrFlow();
        when(aiIvrFlowService.updateAiIvrFlow(any(AiIvrFlow.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("designer"));
            AjaxResult result = controller.edit(flow);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("designer", flow.getUpdateBy());
    }

    @Test
    void 发布流程透传影响行数()
    {
        when(aiIvrFlowService.publishFlow(3L)).thenReturn(1);

        AjaxResult result = controller.publish(3L);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiIvrFlowService).publishFlow(3L);
    }

    @Test
    void 保存设计时拆分节点连线与画布数据()
    {
        IvrFlowDesignRequest request = new IvrFlowDesignRequest();
        request.setFlowId(3L);
        request.setFlowData("{\"nodes\":[]}");
        List<AiIvrNode> nodes = new ArrayList<AiIvrNode>();
        List<AiIvrEdge> edges = new ArrayList<AiIvrEdge>();
        request.setNodes(nodes);
        request.setEdges(edges);
        when(aiIvrFlowService.saveFlowDesign(3L, "{\"nodes\":[]}", nodes, edges)).thenReturn(1);

        AjaxResult result = controller.saveDesign(request);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiIvrFlowService).saveFlowDesign(3L, "{\"nodes\":[]}", nodes, edges);
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] flowIds = new Long[] { 1L, 2L };
        when(aiIvrFlowService.deleteAiIvrFlowByFlowIds(flowIds)).thenReturn(2);

        AjaxResult result = controller.remove(flowIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiIvrFlowService).deleteAiIvrFlowByFlowIds(flowIds);
    }
}
