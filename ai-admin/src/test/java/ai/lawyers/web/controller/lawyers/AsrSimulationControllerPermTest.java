package ai.lawyers.web.controller.lawyers;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ai.lawyers.system.service.lawyers.voice.asr.AsrQualitySimulationManager;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link AsrSimulationController} 权限校验：未登录 401、权限不足 403、
 * 持 lawyers:modelConfig:test 放行并委托 Manager。
 */
public class AsrSimulationControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private AsrQualitySimulationManager asrQualitySimulationManager;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(AsrSimulationController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("asrQualitySimulationManager", asrQualitySimulationManager);
    }

    @Test
    void submit_unauthenticated_401() throws Exception
    {
        mockMvc.perform(post("/lawyers/asr/simulation/submit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submit_wrongPermission_403() throws Exception
    {
        mockMvc.perform(post("/lawyers/asr/simulation/submit").with(loginAs("lawyers:nope")))
                .andExpect(status().isForbidden());
    }

    @Test
    void submit_withPermission_delegatesManager() throws Exception
    {
        mockMvc.perform(post("/lawyers/asr/simulation/submit")
                        .with(loginAs("lawyers:modelConfig:test")))
                .andExpect(status().isOk());

        verify(asrQualitySimulationManager).submit(null);
    }

    @Test
    void progress_withPermission_delegatesManager() throws Exception
    {
        mockMvc.perform(get("/lawyers/asr/simulation/progress").param("taskId", "t1")
                        .with(loginAs("lawyers:modelConfig:test")))
                .andExpect(status().isOk());

        verify(asrQualitySimulationManager).getTask("t1");
    }
}
