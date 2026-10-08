package ai.lawyers.web.controller.lawyers.outbound;

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
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.outbound.IOutboundExecutionService;
import ai.lawyers.web.support.AbstractPermMvcTest;
import ai.lawyers.web.support.PermMvcValidationConfig;

/**
 * P2-14：{@link AiOutboundTaskController} 请求体 Bean Validation 校验路径——
 * add/edit 任务名称 {@code @NotBlank} 违约 400、合法体放行 200。
 */
public class AiOutboundTaskControllerValidationTest extends AbstractPermMvcTest
{
    @Mock
    private IAiOutboundTaskService aiOutboundTaskService;

    @Mock
    private IOutboundExecutionService outboundExecutionService;

    @Override
    protected Class<?> webMvcConfig()
    {
        return PermMvcValidationConfig.class;
    }

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AiOutboundTaskController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiOutboundTaskService", aiOutboundTaskService);
        registerMock("outboundExecutionService", outboundExecutionService);
    }

    @Test
    void add_blankTaskName_400() throws Exception
    {
        mockMvc.perform(post("/lawyers/outbound/task")
                        .with(loginAs("lawyers:outbound:task:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void add_validBody_200() throws Exception
    {
        when(aiOutboundTaskService.insertAiOutboundTask(any())).thenReturn(1);

        mockMvc.perform(post("/lawyers/outbound/task")
                        .with(loginAs("lawyers:outbound:task:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"外呼任务\"}"))
                .andExpect(status().isOk());

        verify(aiOutboundTaskService).insertAiOutboundTask(any());
    }

    @Test
    void edit_blankTaskName_400() throws Exception
    {
        mockMvc.perform(put("/lawyers/outbound/task")
                        .with(loginAs("lawyers:outbound:task:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":1,\"taskName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void edit_validBody_200() throws Exception
    {
        when(aiOutboundTaskService.updateAiOutboundTask(any())).thenReturn(1);

        mockMvc.perform(put("/lawyers/outbound/task")
                        .with(loginAs("lawyers:outbound:task:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":1,\"taskName\":\"外呼任务\"}"))
                .andExpect(status().isOk());

        verify(aiOutboundTaskService).updateAiOutboundTask(any());
    }
}
