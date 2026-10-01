package ai.lawyers.web.controller.lawyers;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ai.lawyers.system.service.lawyers.voice.robot.RobotSimulationService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link RobotSimulationController} 权限校验——未登录 401、权限不足 403、
 * 持有 lawyers:modelConfig:test 放行并委托 Service。
 */
public class RobotSimulationControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private RobotSimulationService robotSimulationService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(RobotSimulationController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("robotSimulationService", robotSimulationService);
    }

    @Test
    void run_unauthenticated_401() throws Exception
    {
        mockMvc.perform(post("/lawyers/robot/simulation/run"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void run_wrongPermission_403() throws Exception
    {
        mockMvc.perform(post("/lawyers/robot/simulation/run").with(loginAs("lawyers:nope")))
                .andExpect(status().isForbidden());
    }

    @Test
    void run_withPermission_delegatesService() throws Exception
    {
        mockMvc.perform(post("/lawyers/robot/simulation/run")
                        .with(loginAs("lawyers:modelConfig:test")))
                .andExpect(status().isOk());

        verify(robotSimulationService).run(null);
    }
}
