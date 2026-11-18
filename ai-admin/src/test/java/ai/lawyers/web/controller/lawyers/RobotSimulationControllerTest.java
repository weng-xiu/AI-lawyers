package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.service.lawyers.voice.AbstractSimulationTaskManager.Task;
import ai.lawyers.system.service.lawyers.voice.robot.RobotSimulationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 机器人仿真回归接口测试：任务提交与进度查询。
 *
 * @author ai-lawyers
 */
class RobotSimulationControllerTest
{
    private RobotSimulationController controller;

    private RobotSimulationManager manager;

    @BeforeEach
    void setUp()
    {
        manager = mock(RobotSimulationManager.class);
        controller = new RobotSimulationController();
        ReflectionTestUtils.setField(controller, "robotSimulationManager", manager);
    }

    @Test
    void 提交任务返回任务ID()
    {
        when(manager.submit(null)).thenReturn("task-002");

        AjaxResult result = controller.submit(null);

        assertTrue(result.isSuccess());
        assertEquals("task-002", result.get("taskId"));
        assertEquals("机器人评测任务已提交", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 查询进度透传任务对象()
    {
        Task task = new Task();
        task.taskId = "task-002";
        task.status = Task.RUNNING;
        when(manager.getTask("task-002")).thenReturn(task);

        AjaxResult result = controller.progress("task-002");

        assertSame(task, result.get(AjaxResult.DATA_TAG));
    }
}
