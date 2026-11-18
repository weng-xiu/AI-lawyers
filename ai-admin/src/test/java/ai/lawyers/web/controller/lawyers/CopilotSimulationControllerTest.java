package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.service.lawyers.voice.AbstractSimulationTaskManager.Task;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotSimulationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Copilot 仿真回归接口测试：任务提交返回任务ID与进度查询透传。
 *
 * @author ai-lawyers
 */
class CopilotSimulationControllerTest
{
    private CopilotSimulationController controller;

    private CopilotSimulationManager manager;

    @BeforeEach
    void setUp()
    {
        manager = mock(CopilotSimulationManager.class);
        controller = new CopilotSimulationController();
        ReflectionTestUtils.setField(controller, "copilotSimulationManager", manager);
    }

    @Test
    void 提交任务返回任务ID()
    {
        when(manager.submit("[{\"question\":\"q\"}]")).thenReturn("task-001");

        AjaxResult result = controller.submit("[{\"question\":\"q\"}]");

        assertTrue(result.isSuccess());
        assertEquals("task-001", result.get("taskId"));
        assertEquals("Copilot 评测任务已提交", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 查询进度透传任务对象()
    {
        Task task = new Task();
        task.taskId = "task-001";
        task.status = Task.SUCCESS;
        task.totalCases = 3;
        task.passedCases = 3;
        when(manager.getTask("task-001")).thenReturn(task);

        AjaxResult result = controller.progress("task-001");

        assertSame(task, result.get(AjaxResult.DATA_TAG));
    }
}
