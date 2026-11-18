package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiCallTransfer;
import ai.lawyers.system.service.lawyers.IAiCallTransferService;

/**
 * 呼叫转接接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallTransferControllerTest
{
    @Mock
    private IAiCallTransferService aiCallTransferService;

    @InjectMocks
    private AiCallTransferController controller;

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

    private Map<String, Object> transferParams()
    {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("recordId", 100L);
        params.put("fromAgentId", 11L);
        params.put("fromAgentName", "张三");
        params.put("toAgentId", 22L);
        params.put("toAgentName", "李四");
        return params;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallTransfer query = new AiCallTransfer();
        List<AiCallTransfer> transfers = new ArrayList<AiCallTransfer>();
        transfers.add(new AiCallTransfer());
        when(aiCallTransferService.selectAiCallTransferList(query)).thenReturn(transfers);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(transfers, table.getRows());
    }

    @Test
    void 详情透传服务结果()
    {
        AiCallTransfer transfer = new AiCallTransfer();
        transfer.setTransferId(7L);
        when(aiCallTransferService.selectAiCallTransferByTransferId(7L)).thenReturn(transfer);

        AjaxResult result = controller.getInfo(7L);

        assertSame(transfer, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiCallTransfer transfer = new AiCallTransfer();
        when(aiCallTransferService.insertAiCallTransfer(any(AiCallTransfer.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult result = controller.add(transfer);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("agent", transfer.getCreateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] transferIds = new Long[] { 3L, 4L };
        when(aiCallTransferService.deleteAiCallTransferByTransferIds(transferIds)).thenReturn(2);

        AjaxResult result = controller.remove(transferIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiCallTransferService).deleteAiCallTransferByTransferIds(transferIds);
    }

    @Test
    void 执行转接成功返回提示()
    {
        Map<String, Object> params = transferParams();
        params.put("reason", "专业分工");
        when(aiCallTransferService.transferCall(100L, 11L, "张三", 22L, "李四", "专业分工")).thenReturn(1);

        AjaxResult result = controller.doTransfer(params);

        assertEquals("转接成功", result.get(AjaxResult.MSG_TAG));
        verify(aiCallTransferService).transferCall(100L, 11L, "张三", 22L, "李四", "专业分工");
    }

    @Test
    void 执行转接失败返回错误()
    {
        when(aiCallTransferService.transferCall(100L, 11L, "张三", 22L, "李四", "")).thenReturn(0);

        AjaxResult result = controller.doTransfer(transferParams());

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("转接失败", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 按通话记录查询转接轨迹()
    {
        List<AiCallTransfer> transfers = new ArrayList<AiCallTransfer>();
        transfers.add(new AiCallTransfer());
        when(aiCallTransferService.selectAiCallTransferByRecordId(100L)).thenReturn(transfers);

        AjaxResult result = controller.getTransfersByRecordId(100L);

        assertSame(transfers, result.get(AjaxResult.DATA_TAG));
    }
}
