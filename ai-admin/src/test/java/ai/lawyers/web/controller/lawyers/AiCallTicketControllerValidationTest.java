package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.web.support.AbstractPermMvcTest;
import ai.lawyers.web.support.PermMvcValidationConfig;

/**
 * P2-14：{@link AiCallTicketController} 请求体 Bean Validation 校验路径——
 * add/edit 工单标题 {@code @NotBlank} 违约 400、合法体放行 200。
 */
public class AiCallTicketControllerValidationTest extends AbstractPermMvcTest
{
    @Mock
    private IAiCallTicketService aiCallTicketService;

    @Override
    protected Class<?> webMvcConfig()
    {
        return PermMvcValidationConfig.class;
    }

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AiCallTicketController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiCallTicketService", aiCallTicketService);
    }

    @Test
    void add_blankTitle_400() throws Exception
    {
        mockMvc.perform(post("/lawyers/call/ticket")
                        .with(loginAs("lawyers:call:ticket:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void add_validBody_200() throws Exception
    {
        when(aiCallTicketService.insertAiCallTicket(any())).thenReturn(1);

        mockMvc.perform(post("/lawyers/call/ticket")
                        .with(loginAs("lawyers:call:ticket:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"工单标题\"}"))
                .andExpect(status().isOk());

        verify(aiCallTicketService).insertAiCallTicket(any());
    }

    @Test
    void edit_blankTitle_400() throws Exception
    {
        mockMvc.perform(put("/lawyers/call/ticket")
                        .with(loginAs("lawyers:call:ticket:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":1,\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void edit_validBody_200() throws Exception
    {
        when(aiCallTicketService.updateAiCallTicket(any())).thenReturn(1);

        mockMvc.perform(put("/lawyers/call/ticket")
                        .with(loginAs("lawyers:call:ticket:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":1,\"title\":\"工单标题\"}"))
                .andExpect(status().isOk());

        verify(aiCallTicketService).updateAiCallTicket(any());
    }
}
