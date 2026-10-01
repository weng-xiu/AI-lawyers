package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ai.lawyers.system.service.lawyers.TicketFlowService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link TicketFlowController} 权限校验——未登录 401、权限不足 403、
 * 持有 lawyers:call:ticket:list 放行并按角色查询可执行动作。
 */
public class TicketFlowControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private TicketFlowService ticketFlowService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(TicketFlowController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("ticketFlowService", ticketFlowService);
    }

    @Test
    void actions_unauthenticated_401() throws Exception
    {
        mockMvc.perform(get("/lawyers/ticket-flow/actions/HOTLINE/0"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actions_wrongPermission_403() throws Exception
    {
        mockMvc.perform(get("/lawyers/ticket-flow/actions/HOTLINE/0")
                        .with(loginAs("lawyers:nope")))
                .andExpect(status().isForbidden());
    }

    @Test
    void actions_withPermission_delegatesService() throws Exception
    {
        when(ticketFlowService.listAllowedActions(eq("HOTLINE"), eq("0"), anyList()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/lawyers/ticket-flow/actions/HOTLINE/0")
                        .with(loginAs("lawyers:call:ticket:list")))
                .andExpect(status().isOk());

        verify(ticketFlowService).listAllowedActions(eq("HOTLINE"), eq("0"), anyList());
    }
}
