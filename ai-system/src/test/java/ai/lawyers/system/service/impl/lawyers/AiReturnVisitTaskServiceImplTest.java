package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiReturnVisitTask;
import ai.lawyers.system.mapper.lawyers.AiReturnVisitTaskMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 回访任务服务单测。
 *
 * <p>覆盖回访任务查询与增删改委托、统计查询，以及逾期扫描在主从锁下执行标记并吞掉异常。</p>
 *
 * @author ai-lawyers
 */
class AiReturnVisitTaskServiceImplTest
{
    private AiReturnVisitTaskServiceImpl service;

    @Mock private AiReturnVisitTaskMapper aiReturnVisitTaskMapper;
    @Mock private RedisLeaderLock leaderLock;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiReturnVisitTaskServiceImpl();
        ReflectionTestUtils.setField(service, "aiReturnVisitTaskMapper", aiReturnVisitTaskMapper);
        ReflectionTestUtils.setField(service, "leaderLock", leaderLock);
    }

    @Test
    void selectAiReturnVisitTaskByTaskId_delegates()
    {
        AiReturnVisitTask expected = new AiReturnVisitTask();
        when(aiReturnVisitTaskMapper.selectAiReturnVisitTaskByTaskId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiReturnVisitTaskByTaskId(1L));
    }

    @Test
    void selectAiReturnVisitTaskList_delegates()
    {
        AiReturnVisitTask query = new AiReturnVisitTask();
        List<AiReturnVisitTask> list = Collections.singletonList(new AiReturnVisitTask());
        when(aiReturnVisitTaskMapper.selectAiReturnVisitTaskList(query)).thenReturn(list);

        assertSame(list, service.selectAiReturnVisitTaskList(query));
    }

    @Test
    void insertAiReturnVisitTask_delegates()
    {
        AiReturnVisitTask task = new AiReturnVisitTask();
        when(aiReturnVisitTaskMapper.insertAiReturnVisitTask(task)).thenReturn(1);

        assertEquals(1, service.insertAiReturnVisitTask(task));
    }

    @Test
    void updateAiReturnVisitTask_delegates()
    {
        AiReturnVisitTask task = new AiReturnVisitTask();
        when(aiReturnVisitTaskMapper.updateAiReturnVisitTask(task)).thenReturn(1);

        assertEquals(1, service.updateAiReturnVisitTask(task));
    }

    @Test
    void deleteAiReturnVisitTaskByTaskId_delegates()
    {
        when(aiReturnVisitTaskMapper.deleteAiReturnVisitTaskByTaskId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiReturnVisitTaskByTaskId(1L));
    }

    @Test
    void deleteAiReturnVisitTaskByTaskIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiReturnVisitTaskMapper.deleteAiReturnVisitTaskByTaskIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiReturnVisitTaskByTaskIds(ids));
    }

    @Test
    void selectReturnVisitTaskStats_delegates()
    {
        Map<String, Object> stats = new HashMap<>();
        when(aiReturnVisitTaskMapper.selectReturnVisitTaskStats()).thenReturn(stats);

        assertSame(stats, service.selectReturnVisitTaskStats());
    }

    @Test
    @SuppressWarnings("unchecked")
    void scanOverdueTasks_runsMarkUnderLeaderLock()
    {
        when(aiReturnVisitTaskMapper.markOverdueTasks(any())).thenReturn(3);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doAnswer(invocation -> {
            taskCaptor.getValue().run();
            return true;
        }).when(leaderLock).tryRun(eq("job:return-visit-overdue"), any(Duration.class), taskCaptor.capture());

        service.scanOverdueTasks();

        verify(aiReturnVisitTaskMapper).markOverdueTasks(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void scanOverdueTasks_mapperThrows_doesNotPropagate()
    {
        when(aiReturnVisitTaskMapper.markOverdueTasks(any()))
            .thenThrow(new IllegalStateException("db down"));
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        doAnswer(invocation -> {
            taskCaptor.getValue().run();
            return true;
        }).when(leaderLock).tryRun(eq("job:return-visit-overdue"), any(Duration.class), taskCaptor.capture());

        service.scanOverdueTasks();

        verify(aiReturnVisitTaskMapper).markOverdueTasks(any());
        assertFalse(taskCaptor.getValue() == null);
    }
}
