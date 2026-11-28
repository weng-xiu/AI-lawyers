package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.service.lawyers.voice.AbstractSimulationTaskManager.Task;
import ai.lawyers.system.service.lawyers.voice.asr.AsrQualitySimulationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ASR 转写质量评测接口测试：任务提交与进度查询。
 *
 * @author ai-lawyers
 */
class AsrSimulationControllerTest
{
    private AsrSimulationController controller;

    private AsrQualitySimulationManager manager;

    @BeforeEach
    void setUp()
    {
        manager = mock(AsrQualitySimulationManager.class);
        controller = new AsrSimulationController();
        ReflectionTestUtils.setField(controller, "asrQualitySimulationManager", manager);
    }

    @Test
    void 提交任务返回任务ID()
    {
        when(manager.submit(null)).thenReturn("task-asr-1");

        AjaxResult result = controller.submit(null);

        assertTrue(result.isSuccess());
        assertEquals("task-asr-1", result.get("taskId"));
        assertEquals("ASR 转写质量评测任务已提交", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 查询进度透传任务对象()
    {
        Task task = new Task();
        task.taskId = "task-asr-1";
        task.status = Task.RUNNING;
        when(manager.getTask("task-asr-1")).thenReturn(task);

        AjaxResult result = controller.progress("task-asr-1");

        assertSame(task, result.get(AjaxResult.DATA_TAG));
    }
}
