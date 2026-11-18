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
import ai.lawyers.system.service.lawyers.ivr.IAiIvrEdgeService;

/**
 * IVR 连线接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiIvrEdgeControllerTest
{
    @Mock
    private IAiIvrEdgeService aiIvrEdgeService;

    @InjectMocks
    private AiIvrEdgeController controller;

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
        AiIvrEdge query = new AiIvrEdge();
        List<AiIvrEdge> edges = new ArrayList<AiIvrEdge>();
        edges.add(new AiIvrEdge());
        when(aiIvrEdgeService.selectAiIvrEdgeList(query)).thenReturn(edges);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(edges, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiIvrEdgeService.selectAiIvrEdgeList(any(AiIvrEdge.class)))
                .thenReturn(List.of(new AiIvrEdge()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiIvrEdge());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 按流程查询连线透传服务结果()
    {
        List<AiIvrEdge> edges = new ArrayList<AiIvrEdge>();
        when(aiIvrEdgeService.selectAiIvrEdgeByFlowId(3L)).thenReturn(edges);

        AjaxResult result = controller.getEdgesByFlowId(3L);

        assertSame(edges, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiIvrEdge edge = new AiIvrEdge();
        when(aiIvrEdgeService.insertAiIvrEdge(any(AiIvrEdge.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(edge);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", edge.getCreateBy());
    }

    @Test
    void 批量保存按流程覆盖()
    {
        List<AiIvrEdge> edges = new ArrayList<AiIvrEdge>();
        edges.add(new AiIvrEdge());
        when(aiIvrEdgeService.saveFlowEdges(3L, edges)).thenReturn(1);

        AjaxResult result = controller.batchAdd(3L, edges);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiIvrEdgeService).saveFlowEdges(3L, edges);
    }

    @Test
    void 按流程删除连线()
    {
        when(aiIvrEdgeService.deleteAiIvrEdgeByFlowId(3L)).thenReturn(2);

        AjaxResult result = controller.removeByFlowId(3L);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiIvrEdgeService).deleteAiIvrEdgeByFlowId(3L);
    }
}
