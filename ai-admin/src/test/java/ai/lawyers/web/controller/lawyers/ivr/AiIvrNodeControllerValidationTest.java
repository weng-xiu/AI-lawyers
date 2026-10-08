package ai.lawyers.web.controller.lawyers.ivr;

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
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;
import ai.lawyers.web.support.AbstractPermMvcTest;
import ai.lawyers.web.support.PermMvcValidationConfig;

/**
 * P2-14：{@link AiIvrNodeController} 请求体 Bean Validation 校验路径——
 * add/edit 所属流程 {@code @NotNull} 违约 400、合法体放行 200。
 */
public class AiIvrNodeControllerValidationTest extends AbstractPermMvcTest
{
    @Mock
    private IAiIvrNodeService aiIvrNodeService;

    @Override
    protected Class<?> webMvcConfig()
    {
        return PermMvcValidationConfig.class;
    }

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AiIvrNodeController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("aiIvrNodeService", aiIvrNodeService);
    }

    @Test
    void add_nullFlowId_400() throws Exception
    {
        mockMvc.perform(post("/lawyers/ivr/node")
                        .with(loginAs("lawyers:ivr:node:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeName\":\"节点\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void add_validBody_200() throws Exception
    {
        when(aiIvrNodeService.insertAiIvrNode(any())).thenReturn(1);

        mockMvc.perform(post("/lawyers/ivr/node")
                        .with(loginAs("lawyers:ivr:node:add"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowId\":10,\"nodeName\":\"节点\"}"))
                .andExpect(status().isOk());

        verify(aiIvrNodeService).insertAiIvrNode(any());
    }

    @Test
    void edit_nullFlowId_400() throws Exception
    {
        mockMvc.perform(put("/lawyers/ivr/node")
                        .with(loginAs("lawyers:ivr:node:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":1,\"nodeName\":\"节点\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void edit_validBody_200() throws Exception
    {
        when(aiIvrNodeService.updateAiIvrNode(any())).thenReturn(1);

        mockMvc.perform(put("/lawyers/ivr/node")
                        .with(loginAs("lawyers:ivr:node:edit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":1,\"flowId\":10,\"nodeName\":\"节点\"}"))
                .andExpect(status().isOk());

        verify(aiIvrNodeService).updateAiIvrNode(any());
    }
}
