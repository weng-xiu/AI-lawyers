package ai.lawyers.web.controller.lawyers.collab;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link AiExternalTicketCallbackController} 回调/来单端点权限校验。
 *
 * <p>callback、receive 均为 {@code @Anonymous} 且无 {@code @PreAuthorize}：生产由
 * RuoYi PermitAllUrlProperties 匿名放行，改由 HMAC 签名/X-Collab-Token 兜底 + 频控收口，
 * 本就不依赖登录态。本迷你安全链未模拟该匿名放行（anyRequest authenticated 会给匿名
 * 请求 401，与生产语义不符），故不断言 401/403；仅按既有 autoLogout 范式验证「已登录
 * 请求（任意权限）直达业务返回 200」。默认未配置签名/令牌时 verify 告警放行，且
 * RedisCache 为可选依赖（缺失即跳过频控），可稳定走到 Service。</p>
 */
public class AiExternalTicketCallbackControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiTicketTransferService ticketTransferService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        AiTicketTransfer transfer = new AiTicketTransfer();
        transfer.setTicketNo("T202610070001");
        when(ticketTransferService.handleCallback(anyString(), any(), any(), any(), any()))
                .thenReturn(transfer);
        when(ticketTransferService.receiveInbound(any(), any(), any(), any(), any(), any(),
                any(), any(), any())).thenReturn(transfer);
        startMvc(AiExternalTicketCallbackController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("ticketTransferService", ticketTransferService);
    }

    @Test
    void callback_loggedIn_anyPermission_reachesController() throws Exception
    {
        MockHttpServletRequestBuilder request = post("/lawyers/external/ticket/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotentKey\":\"idem-1\",\"externalStatus\":\"ACCEPTED\"}");
        mockMvc.perform(request.with(loginAs("lawyers:unrelated:perm")))
                .andExpect(status().isOk());
    }

    @Test
    void receive_loggedIn_anyPermission_reachesController() throws Exception
    {
        MockHttpServletRequestBuilder request = post("/lawyers/external/ticket/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalType\":\"12345\",\"externalTicketNo\":\"EXT-001\"}");
        mockMvc.perform(request.with(loginAs("lawyers:unrelated:perm")))
                .andExpect(status().isOk());
    }
}
