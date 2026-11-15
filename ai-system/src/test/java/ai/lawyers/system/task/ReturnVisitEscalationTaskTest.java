package ai.lawyers.system.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.domain.lawyers.AiReturnVisitTask;
import ai.lawyers.system.mapper.lawyers.AiReturnVisitTaskMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 逾期回访逐级升级任务测试：L1/L2/L3 分级与目标角色、计划时间为空跳过、
 * Redis 去重标记跳过、标记写入、自定义角色链覆盖、单任务异常隔离、
 * 未取得集群锁不扫描、事件发布者缺席降级。
 *
 * @author ai-lawyers
 */
class ReturnVisitEscalationTaskTest
{
    private static final long MINUTE = 60_000L;

    private AiReturnVisitTaskMapper taskMapper;

    private RedisLeaderLock leaderLock;

    private CallEventPublisher publisher;

    private RedisCache redisCache;

    private ReturnVisitEscalationTask task;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(AiReturnVisitTaskMapper.class);
        leaderLock = mock(RedisLeaderLock.class);
        publisher = mock(CallEventPublisher.class);
        redisCache = mock(RedisCache.class);
        task = new ReturnVisitEscalationTask();
        ReflectionTestUtils.setField(task, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "callEventPublisher", publisher);
        ReflectionTestUtils.setField(task, "redisCache", redisCache);
        ReflectionTestUtils.setField(task, "escalateRoles", "agent,teamleader,leader");
        // 默认：取得集群锁并同步执行扫描体
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class)))
                .thenAnswer(invocation ->
                {
                    invocation.getArgument(2, Runnable.class).run();
                    return true;
                });
    }

    private AiReturnVisitTask overdueTask(long taskId, long overdueMinutes)
    {
        AiReturnVisitTask t = new AiReturnVisitTask();
        t.setTaskId(taskId);
        t.setTaskNo("RV" + taskId);
        t.setCallerName("张三");
        t.setCallerNumber("13800001234");
        t.setPriority("high");
        t.setAssignee("李四");
        t.setAssigneeId(9L);
        t.setPlanTime(new Date(System.currentTimeMillis() - overdueMinutes * MINUTE));
        return t;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureBroadcastData()
    {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).broadcast(eq("RETURN_VISIT_ESCALATE"), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    void 无逾期任务时不广播()
    {
        when(taskMapper.selectOverdueTasksForEscalate(200)).thenReturn(Collections.emptyList());

        task.scanEscalations();

        verifyNoInteractions(publisher);
        verifyNoInteractions(redisCache);
    }

    @Test
    void 刚逾期升级受理人本人L1()
    {
        when(taskMapper.selectOverdueTasksForEscalate(200))
                .thenReturn(Collections.singletonList(overdueTask(1L, 10L)));

        task.scanEscalations();

        Map<String, Object> data = captureBroadcastData();
        assertThat(data.get("level")).isEqualTo(1);
        assertThat(data.get("escalateRole")).isEqualTo("agent");
        assertThat(data.get("taskNo")).isEqualTo("RV1");
        assertThat(data.get("assignee")).isEqualTo("李四");
        assertThat((Long) data.get("overdueMinutes")).isEqualTo(10L);
        verify(redisCache).setCacheObject("rv:escalated:1:1", "agent", 7, TimeUnit.DAYS);
    }

    @Test
    void 逾期满一天升级班组长L2()
    {
        when(taskMapper.selectOverdueTasksForEscalate(200))
                .thenReturn(Collections.singletonList(overdueTask(2L, 24 * 60L)));

        task.scanEscalations();

        Map<String, Object> data = captureBroadcastData();
        assertThat(data.get("level")).isEqualTo(2);
        assertThat(data.get("escalateRole")).isEqualTo("teamleader");
        verify(redisCache).setCacheObject("rv:escalated:2:2", "teamleader", 7, TimeUnit.DAYS);
    }

    @Test
    void 逾期满三天升级分管领导L3()
    {
        when(taskMapper.selectOverdueTasksForEscalate(200))
                .thenReturn(Collections.singletonList(overdueTask(3L, 72 * 60L + 1L)));

        task.scanEscalations();

        Map<String, Object> data = captureBroadcastData();
        assertThat(data.get("level")).isEqualTo(3);
        assertThat(data.get("escalateRole")).isEqualTo("leader");
    }

    @Test
    void 计划时间为空跳过()
    {
        AiReturnVisitTask t = overdueTask(4L, 60L);
        t.setPlanTime(null);
        when(taskMapper.selectOverdueTasksForEscalate(200)).thenReturn(Collections.singletonList(t));

        task.scanEscalations();

        verifyNoInteractions(publisher);
        verifyNoInteractions(redisCache);
    }

    @Test
    void 已有升级标记时跳过()
    {
        when(taskMapper.selectOverdueTasksForEscalate(200))
                .thenReturn(Collections.singletonList(overdueTask(5L, 30L)));
        when(redisCache.hasKey("rv:escalated:5:1")).thenReturn(Boolean.TRUE);

        task.scanEscalations();

        verifyNoInteractions(publisher);
        verify(redisCache, never()).setCacheObject(anyString(), any(), any(Integer.class), any(TimeUnit.class));
    }

    @Test
    void 自定义角色链不足时高等级不升级()
    {
        ReflectionTestUtils.setField(task, "escalateRoles", "agent");
        when(taskMapper.selectOverdueTasksForEscalate(200))
                .thenReturn(Collections.singletonList(overdueTask(6L, 2 * 24 * 60L)));

        task.scanEscalations();

        // 逾期 2 天应为 L2，但角色链只有 L1 → 不升级
        verifyNoInteractions(publisher);
    }

    @Test
    void 自定义角色链空白项不升级()
    {
        ReflectionTestUtils.setField(task, "escalateRoles", "agent,,leader");
        when(taskMapper.selectOverdueTasksForEscalate(200))
                .thenReturn(Collections.singletonList(overdueTask(7L, 2 * 24 * 60L)));

        task.scanEscalations();

        verifyNoInteractions(publisher);
    }

    @Test
    void 单任务广播异常不影响后续任务()
    {
        when(taskMapper.selectOverdueTasksForEscalate(200))
                .thenReturn(java.util.Arrays.asList(overdueTask(8L, 5L), overdueTask(9L, 6L)));
        doAnswer(invocation ->
        {
            throw new IllegalStateException("推送失败");
        }).doNothing().when(publisher).broadcast(anyString(), any());

        task.scanEscalations();

        verify(publisher, times(2)).broadcast(eq("RETURN_VISIT_ESCALATE"), any());
        // 第二个任务仍写入去重标记
        verify(redisCache).setCacheObject("rv:escalated:9:1", "agent", 7, TimeUnit.DAYS);
    }

    @Test
    void 未取得集群锁时不扫描()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenReturn(false);

        task.scanEscalations();

        verify(taskMapper, never()).selectOverdueTasksForEscalate(200);
        verifyNoInteractions(publisher);
    }

    @Test
    void 事件发布者缺席时仍写入去重标记()
    {
        ReflectionTestUtils.setField(task, "callEventPublisher", null);
        when(taskMapper.selectOverdueTasksForEscalate(200))
                .thenReturn(Collections.singletonList(overdueTask(10L, 5L)));

        task.scanEscalations();

        verify(redisCache).setCacheObject("rv:escalated:10:1", "agent", 7, TimeUnit.DAYS);
    }
}
