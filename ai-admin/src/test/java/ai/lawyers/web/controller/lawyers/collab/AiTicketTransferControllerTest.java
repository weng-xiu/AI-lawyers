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
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;

/**
 * 工单跨域转办接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiTicketTransferControllerTest
{
    @Mock
    private IAiTicketTransferService ticketTransferService;

    @InjectMocks
    private AiTicketTransferController controller;

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
        AiTicketTransfer query = new AiTicketTransfer();
        List<AiTicketTransfer> transfers = new ArrayList<AiTicketTransfer>();
        transfers.add(new AiTicketTransfer());
        when(ticketTransferService.selectTransferList(query)).thenReturn(transfers);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(transfers, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setExternalTicketNo("WQ-1001");
        when(ticketTransferService.selectTransferList(any(AiTicketTransfer.class)))
                .thenReturn(List.of(transfer));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiTicketTransfer());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(11L);
        when(ticketTransferService.selectTransferById(11L)).thenReturn(transfer);

        AjaxResult result = controller.getInfo(11L);

        assertSame(transfer, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 发起转办使用当前操作人()
    {
        AiTicketTransfer body = new AiTicketTransfer();
        body.setTicketId(100L);
        body.setOrgId(200L);
        body.setRemark("请协助调解");
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTransferId(12L);
        when(ticketTransferService.transferOut(100L, 200L, "请协助调解", "agent")).thenReturn(transfer);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            AjaxResult result = controller.transfer(body);

            assertEquals("转办已发起", result.get(AjaxResult.MSG_TAG));
            assertSame(transfer, result.get(AjaxResult.DATA_TAG));
        }
        verify(ticketTransferService).transferOut(100L, 200L, "请协助调解", "agent");
    }

    @Test
    void 重试推送返回完成提示()
    {
        when(ticketTransferService.retryTransfer(12L)).thenReturn(1);

        AjaxResult result = controller.retry(12L);

        assertEquals("重新推送完成", result.get(AjaxResult.MSG_TAG));
        verify(ticketTransferService).retryTransfer(12L);
    }
}
