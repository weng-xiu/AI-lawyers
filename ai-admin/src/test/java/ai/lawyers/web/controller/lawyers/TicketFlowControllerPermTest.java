package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import ai.lawyers.system.service.lawyers.TicketFlowService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P1-6：{@link TicketFlowController} 权限校验——动作查询复用 lawyers:call:ticket:list；
 * 规则管理端 list/query/add/edit/validate 分别需 lawyers:ticketFlow:*。
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
    void actions_withTicketListPermission_ok() throws Exception
    {
        mockMvc.perform(get("/lawyers/ticket-flow/actions/HOTLINE/0")
                        .with(loginAs("lawyers:call:ticket:list")))
                .andExpect(status().isOk());
        verify(ticketFlowService).listAllowedActions(any(), any(), any());
    }

    @Test
    void validate_wrongPermission_403() throws Exception
    {
        mockMvc.perform(get("/lawyers/ticket-flow/validate/HOTLINE")
                        .with(loginAs("lawyers:call:ticket:list")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validate_withFlowListPermission_ok() throws Exception
    {
        mockMvc.perform(get("/lawyers/ticket-flow/validate/HOTLINE")
                        .with(loginAs("lawyers:ticketFlow:list")))
                .andExpect(status().isOk());
        verify(ticketFlowService).validateFlow("HOTLINE");
    }

    @Test
    void list_wrongPermission_403() throws Exception
    {
        mockMvc.perform(get("/lawyers/ticket-flow/list")
                        .with(loginAs("lawyers:call:ticket:list")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withFlowListPermission_ok() throws Exception
    {
        mockMvc.perform(get("/lawyers/ticket-flow/list")
                        .with(loginAs("lawyers:ticketFlow:list")))
                .andExpect(status().isOk());
        verify(ticketFlowService).listDefinitions(any());
    }

    @Test
    void getInfo_withQueryPermission_ok() throws Exception
    {
        mockMvc.perform(get("/lawyers/ticket-flow/1")
                        .with(loginAs("lawyers:ticketFlow:query")))
                .andExpect(status().isOk());
        verify(ticketFlowService).getDefinition(1L);
    }

    @Test
    void add_withoutPermission_403() throws Exception
    {
        mockMvc.perform(post("/lawyers/ticket-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(loginAs("lawyers:ticketFlow:list")))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_withAddPermission_ok() throws Exception
    {
        mockMvc.perform(post("/lawyers/ticket-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(loginAs("lawyers:ticketFlow:add")))
                .andExpect(status().isOk());
        verify(ticketFlowService).addDefinition(any());
    }

    @Test
    void edit_withEditPermission_ok() throws Exception
    {
        mockMvc.perform(put("/lawyers/ticket-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowId\":1}")
                        .with(loginAs("lawyers:ticketFlow:edit")))
                .andExpect(status().isOk());
        verify(ticketFlowService).updateDefinition(any());
    }
}
