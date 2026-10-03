package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.voice.copilot.IAiCopilotFeedbackService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link CopilotController} 代建工单权限校验——未登录 401、权限不足 403、
 * 持有 lawyers:call:ticket:add 放行且强制 status=0 落库由 Service 执行。
 */
public class CopilotControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCopilotFeedbackService feedbackService;

    @Mock
    private AiLegalKnowledgeChunkMapper chunkMapper;

    @Mock
    private IAiCallTicketService ticketService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(CopilotController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("feedbackService", feedbackService);
        registerMock("chunkMapper", chunkMapper);
        registerMock("ticketService", ticketService);
    }

    @Test
    void createTicket_unauthenticated_401() throws Exception
    {
        mockMvc.perform(post("/lawyers/copilot/actions/createTicket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTicket_wrongPermission_403() throws Exception
    {
        mockMvc.perform(post("/lawyers/copilot/actions/createTicket")
                        .with(loginAs("lawyers:nope"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTicket_withPermission_delegatesService() throws Exception
    {
        mockMvc.perform(post("/lawyers/copilot/actions/createTicket")
                        .with(loginAs("lawyers:call:ticket:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"咨询工单\",\"content\":\"需要跟进\"}"))
                .andExpect(status().isOk());

        verify(ticketService).insertAiCallTicket(any());
    }

    @Test
    void queryTicket_unauthenticated_401() throws Exception
    {
        mockMvc.perform(get("/lawyers/copilot/actions/queryTicket/9"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void queryTicket_wrongPermission_403() throws Exception
    {
        mockMvc.perform(get("/lawyers/copilot/actions/queryTicket/9")
                        .with(loginAs("lawyers:nope")))
                .andExpect(status().isForbidden());
    }

    @Test
    void queryTicket_withPermission_delegatesService() throws Exception
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(9L);
        ticket.setTicketNo("GD20261003001");
        ticket.setTitle("历史咨询");
        ticket.setStatus("1");
        when(ticketService.selectAiCallTicketByTicketId(9L)).thenReturn(ticket);

        mockMvc.perform(get("/lawyers/copilot/actions/queryTicket/9")
                        .with(loginAs("lawyers:call:ticket:query")))
                .andExpect(status().isOk());

        verify(ticketService).selectAiCallTicketByTicketId(eq(9L));
    }
}
