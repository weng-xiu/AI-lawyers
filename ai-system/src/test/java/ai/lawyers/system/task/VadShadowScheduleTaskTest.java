package ai.lawyers.system.task;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.voice.vad.SileroVadShadowService;

/**
 * Silero VAD 影子日统计任务测试：正常触发日报、异常吞掉不影响次日调度。
 *
 * @author ai-lawyers
 */
class VadShadowScheduleTaskTest
{
    private SileroVadShadowService vadShadowService;

    private VadShadowScheduleTask task;

    @BeforeEach
    void setUp()
    {
        vadShadowService = mock(SileroVadShadowService.class);
        task = new VadShadowScheduleTask();
        ReflectionTestUtils.setField(task, "vadShadowService", vadShadowService);
    }

    @Test
    void 触发影子日报输出()
    {
        task.reportDaily();

        verify(vadShadowService).dailyReport();
    }

    @Test
    void 日报异常不外抛()
    {
        doThrow(new IllegalStateException("落库失败")).when(vadShadowService).dailyReport();

        assertThatCode(() -> task.reportDaily()).doesNotThrowAnyException();
        verify(vadShadowService).dailyReport();
    }
}
