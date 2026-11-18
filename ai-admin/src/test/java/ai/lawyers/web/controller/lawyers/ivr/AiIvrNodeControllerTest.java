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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;

/**
 * IVR 节点接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiIvrNodeControllerTest
{
    @Mock
    private IAiIvrNodeService aiIvrNodeService;

    @InjectMocks
    private AiIvrNodeController controller;

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
        AiIvrNode query = new AiIvrNode();
        List<AiIvrNode> nodes = new ArrayList<AiIvrNode>();
        nodes.add(new AiIvrNode());
        when(aiIvrNodeService.selectAiIvrNodeList(query)).thenReturn(nodes);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(nodes, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiIvrNodeService.selectAiIvrNodeList(any(AiIvrNode.class)))
                .thenReturn(List.of(new AiIvrNode()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiIvrNode());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(6L);
        when(aiIvrNodeService.selectAiIvrNodeByNodeId(6L)).thenReturn(node);

        AjaxResult result = controller.getInfo(6L);

        assertSame(node, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 按流程查询节点透传服务结果()
    {
        List<AiIvrNode> nodes = new ArrayList<AiIvrNode>();
        when(aiIvrNodeService.selectAiIvrNodeByFlowId(3L)).thenReturn(nodes);

        AjaxResult result = controller.getNodesByFlowId(3L);

        assertSame(nodes, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 修改写入更新人()
    {
        AiIvrNode node = new AiIvrNode();
        when(aiIvrNodeService.updateAiIvrNode(any(AiIvrNode.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("designer"));
            AjaxResult result = controller.edit(node);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("designer", node.getUpdateBy());
    }

    @Test
    void 批量保存按流程覆盖()
    {
        List<AiIvrNode> nodes = new ArrayList<AiIvrNode>();
        when(aiIvrNodeService.saveFlowNodes(3L, nodes)).thenReturn(5);

        AjaxResult result = controller.batchAdd(3L, nodes);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiIvrNodeService).saveFlowNodes(3L, nodes);
    }

    @Test
    void 按流程删除节点()
    {
        when(aiIvrNodeService.deleteAiIvrNodeByFlowId(3L)).thenReturn(4);

        AjaxResult result = controller.removeByFlowId(3L);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiIvrNodeService).deleteAiIvrNodeByFlowId(3L);
    }
}
