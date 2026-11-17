package ai.lawyers.common.constant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 任务调度常量测试：错过执行策略与状态枚举。
 *
 * @author ai-lawyers
 */
class ScheduleConstantsTest
{
    @Test
    void 错过执行策略常量()
    {
        assertEquals("0", ScheduleConstants.MISFIRE_DEFAULT);
        assertEquals("1", ScheduleConstants.MISFIRE_IGNORE_MISFIRES);
        assertEquals("2", ScheduleConstants.MISFIRE_FIRE_AND_PROCEED);
        assertEquals("3", ScheduleConstants.MISFIRE_DO_NOTHING);
        assertEquals("TASK_CLASS_NAME", ScheduleConstants.TASK_CLASS_NAME);
        assertEquals("TASK_PROPERTIES", ScheduleConstants.TASK_PROPERTIES);
    }

    @Test
    void 状态枚举()
    {
        assertEquals("0", ScheduleConstants.Status.NORMAL.getValue());
        assertEquals("1", ScheduleConstants.Status.PAUSE.getValue());
        assertEquals(ScheduleConstants.Status.PAUSE, ScheduleConstants.Status.valueOf("PAUSE"));
        assertEquals(2, ScheduleConstants.Status.values().length);
    }
}
