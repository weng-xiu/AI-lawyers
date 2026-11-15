package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.service.ISysUserService;
import ai.lawyers.system.service.lawyers.cluster.InstanceDrainState;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.compliance.ComplianceGuard;
import ai.lawyers.system.service.lawyers.concurrent.ExecutorRegistry;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;

/**
 * 智能外呼执行引擎门面单测：任务校验/批量拨号编排、完成判定与站内信、
 * 定时扫描的锁与合规/排空前置条件、卡单对账兜底、事件路由与停机。
 *
 * @author ai-lawyers
 */
class OutboundExecutionServiceImplTest
{
    private static final Long TASK_ID = 100L;

    private OutboundExecutionServiceImpl service;
    private IAiOutboundTaskService taskService;
    private AiOutboundTaskMapper taskMapper;
    private AiOutboundCalleeMapper calleeMapper;
    private Environment environment;
    private MessageNotifyDispatcher messageNotifyDispatcher;
    private InstanceDrainState drainState;
    private ComplianceGuard complianceGuard;
    private OutboundDialer dialer;
    private OutboundCallFinalizer finalizer;
    private OutboundEventCallback eventCallback;
    private ISysUserService userService;
    private RedisLeaderLock leaderLock;
    private ExecutorRegistry executorRegistry;

    @BeforeEach
    void setUp()
    {
        service = new OutboundExecutionServiceImpl();
        taskService = mock(IAiOutboundTaskService.class);
        taskMapper = mock(AiOutboundTaskMapper.class);
        calleeMapper = mock(AiOutboundCalleeMapper.class);
        environment = mock(Environment.class);
        messageNotifyDispatcher = mock(MessageNotifyDispatcher.class);
        drainState = mock(InstanceDrainState.class);
        complianceGuard = mock(ComplianceGuard.class);
        dialer = mock(OutboundDialer.class);
        finalizer = mock(OutboundCallFinalizer.class);
        eventCallback = mock(OutboundEventCallback.class);
        userService = mock(ISysUserService.class);
        leaderLock = mock(RedisLeaderLock.class);
        executorRegistry = mock(ExecutorRegistry.class);

        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "calleeMapper", calleeMapper);
        ReflectionTestUtils.setField(service, "environment", environment);
        ReflectionTestUtils.setField(service, "messageNotifyDispatcher", messageNotifyDispatcher);
        ReflectionTestUtils.setField(service, "drainState", drainState);
        ReflectionTestUtils.setField(service, "complianceGuard", complianceGuard);
        ReflectionTestUtils.setField(service, "dialer", dialer);
        ReflectionTestUtils.setField(service, "finalizer", finalizer);
        ReflectionTestUtils.setField(service, "eventCallback", eventCallback);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(service, "executorRegistry", executorRegistry);

        ReflectionTestUtils.setField(service, "scanEnabled", true);
        ReflectionTestUtils.setField(service, "dialConcurrent", 1);
        ReflectionTestUtils.setField(service, "scanLockSeconds", 30);
        ReflectionTestUtils.setField(service, "retryBackoffBaseMs", 15000L);
        ReflectionTestUtils.setField(service, "staleMinutes", 10);

        when(calleeMapper.selectDialableCallees(anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());
        // 默认仍有未终态号码，避免误触发任务完成与站内信
        when(calleeMapper.countUnfinishedByTaskId(anyLong())).thenReturn(1);
        when(complianceGuard.isCallingAllowed()).thenReturn(true);
        when(drainState.isDraining()).thenReturn(false);
        when(leaderLock.tryRun(anyString(), any(Duration.class), any())).thenReturn(true);
    }

    @Test
    void executeTask_taskMissing()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(null);

        assertThat(service.executeTask(TASK_ID)).isZero();
        verify(calleeMapper, never()).selectDialableCallees(anyLong(), anyInt());
    }

    @Test
    void executeTask_taskNotRunning()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task("0", 10));

        assertThat(service.executeTask(TASK_ID)).isZero();
        verify(calleeMapper, never()).selectDialableCallees(anyLong(), anyInt());
    }

    @Test
    void executeTask_noPending_stillChecksCompletion()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task("1", null));

        assertThat(service.executeTask(TASK_ID)).isZero();

        // maxConcurrent 为空 → 默认批次 10
        verify(calleeMapper).selectDialableCallees(TASK_ID, 10);
        verify(calleeMapper).countUnfinishedByTaskId(TASK_ID);
        verify(taskService, never()).updateAiOutboundTask(any());
    }

    @Test
    void executeTask_batchSizeCappedAt100()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task("1", 500));

        service.executeTask(TASK_ID);

        verify(calleeMapper).selectDialableCallees(TASK_ID, 100);
    }

    @Test
    void executeTask_claimsAndDials()
    {
        AiOutboundTask task = task("1", 10);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);
        AiOutboundCallee callee = callee(1L, TASK_ID);
        when(calleeMapper.selectDialableCallees(TASK_ID, 10))
                .thenReturn(Collections.singletonList(callee));
        when(calleeMapper.claimCallee(1L, "outbound-executor")).thenReturn(1);

        assertThat(service.executeTask(TASK_ID)).isEqualTo(1);

        verify(dialer).dialCallee(task, callee);
        assertThat(callee.getCallStatus()).isEqualTo("1");
        assertThat(callee.getCallTime()).isNotNull();
    }

    @Test
    void executeTask_claimLost_skipsDial()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task("1", 10));
        when(calleeMapper.selectDialableCallees(TASK_ID, 10))
                .thenReturn(Collections.singletonList(callee(1L, TASK_ID)));
        when(calleeMapper.claimCallee(1L, "outbound-executor")).thenReturn(0);

        assertThat(service.executeTask(TASK_ID)).isEqualTo(1);

        verify(dialer, never()).dialCallee(any(), any());
    }

    @Test
    void executeTask_dialException_marksFailed()
    {
        AiOutboundTask task = task("1", 10);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);
        AiOutboundCallee callee = callee(1L, TASK_ID);
        when(calleeMapper.selectDialableCallees(TASK_ID, 10))
                .thenReturn(Collections.singletonList(callee));
        when(calleeMapper.claimCallee(1L, "outbound-executor")).thenReturn(1);
        doThrow(new RuntimeException("网关超时")).when(dialer).dialCallee(task, callee);

        service.executeTask(TASK_ID);

        verify(finalizer).markCalleeFailed(eq(task), eq(callee), contains("执行异常：网关超时"));
    }

    @Test
    void executeTask_concurrentPool_runsAllJobs()
    {
        ReflectionTestUtils.setField(service, "dialConcurrent", 2);
        ReflectionTestUtils.setField(service, "dialPool", new DirectExecutorService());
        AiOutboundTask task = task("1", 10);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);
        AiOutboundCallee a = callee(1L, TASK_ID);
        AiOutboundCallee b = callee(2L, TASK_ID);
        when(calleeMapper.selectDialableCallees(TASK_ID, 10)).thenReturn(Arrays.asList(a, b));
        when(calleeMapper.claimCallee(anyLong(), anyString())).thenReturn(1);

        assertThat(service.executeTask(TASK_ID)).isEqualTo(2);

        verify(dialer).dialCallee(task, a);
        verify(dialer).dialCallee(task, b);
    }

    @Test
    void checkTaskCompletion_completesAndNotifies()
    {
        AiOutboundTask task = task("1", null);
        task.setTaskName("批量外呼");
        task.setCreateBy("alice");
        task.setTotalCount(100);
        task.setAnsweredCount(50);
        task.setNoAnswerCount(30);
        task.setFailedCount(20);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);
        when(calleeMapper.countUnfinishedByTaskId(TASK_ID)).thenReturn(0);
        SysUser creator = new SysUser();
        creator.setUserId(9L);
        when(userService.selectUserByUserName("alice")).thenReturn(creator);

        service.executeTask(TASK_ID);

        ArgumentCaptor<AiOutboundTask> captor = ArgumentCaptor.forClass(AiOutboundTask.class);
        verify(taskService).updateAiOutboundTask(captor.capture());
        assertThat(captor.getValue().getTaskId()).isEqualTo(TASK_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo("2");
        assertThat(captor.getValue().getUpdateBy()).isEqualTo("outbound-executor");

        verify(messageNotifyDispatcher).notify(eq(9L), eq("1"),
                eq("外呼任务已完成：批量外呼"), contains("计划 100 个号码"),
                eq("outbound"), eq(TASK_ID), eq("system"));
    }

    @Test
    void checkTaskCompletion_skipsWhenUnfinished()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task("1", null));
        when(calleeMapper.countUnfinishedByTaskId(TASK_ID)).thenReturn(2);

        service.executeTask(TASK_ID);

        verify(taskService, never()).updateAiOutboundTask(any());
        verify(messageNotifyDispatcher, never()).notify(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void checkTaskCompletion_taskStoppedBetweenCalls()
    {
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID))
                .thenReturn(task("1", null), task("0", null));
        when(calleeMapper.countUnfinishedByTaskId(TASK_ID)).thenReturn(0);

        service.executeTask(TASK_ID);

        verify(taskService, never()).updateAiOutboundTask(any());
    }

    @Test
    void notify_skippedWhenNoCreateBy()
    {
        AiOutboundTask task = task("1", null);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);
        when(calleeMapper.countUnfinishedByTaskId(TASK_ID)).thenReturn(0);

        service.executeTask(TASK_ID);

        verify(taskService).updateAiOutboundTask(any());
        verify(userService, never()).selectUserByUserName(anyString());
        verify(messageNotifyDispatcher, never()).notify(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notify_skippedWhenCreatorMissing()
    {
        AiOutboundTask task = task("1", null);
        task.setCreateBy("ghost");
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);
        when(calleeMapper.countUnfinishedByTaskId(TASK_ID)).thenReturn(0);
        when(userService.selectUserByUserName("ghost")).thenReturn(null);

        service.executeTask(TASK_ID);

        verify(messageNotifyDispatcher, never()).notify(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notify_failureSwallowed()
    {
        AiOutboundTask task = task("1", null);
        task.setCreateBy("alice");
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);
        when(calleeMapper.countUnfinishedByTaskId(TASK_ID)).thenReturn(0);
        SysUser creator = new SysUser();
        creator.setUserId(9L);
        when(userService.selectUserByUserName("alice")).thenReturn(creator);
        doThrow(new RuntimeException("队列不可用")).when(messageNotifyDispatcher)
                .notify(any(), any(), any(), any(), any(), any(), any());

        service.executeTask(TASK_ID);

        verify(taskService).updateAiOutboundTask(any());
    }

    @Test
    void scan_disabled_skipsLock()
    {
        ReflectionTestUtils.setField(service, "scanEnabled", false);

        service.scanRunningTasks();

        verify(leaderLock, never()).tryRun(anyString(), any(Duration.class), any());
    }

    @Test
    void scan_runsTasksUnderLock()
    {
        when(taskMapper.selectRunningTasks()).thenReturn(Arrays.asList(taskById(100L), taskById(200L)));

        service.scanRunningTasks();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(leaderLock).tryRun(eq("job:outbound-scan"), eq(Duration.ofSeconds(30)), captor.capture());
        captor.getValue().run();

        verify(taskService).selectAiOutboundTaskByTaskId(100L);
        verify(taskService).selectAiOutboundTaskByTaskId(200L);
    }

    @Test
    void scan_skipsWhenComplianceDisallows()
    {
        when(complianceGuard.isCallingAllowed()).thenReturn(false);

        runScan();

        verify(taskMapper, never()).selectRunningTasks();
        verify(taskService, never()).selectAiOutboundTaskByTaskId(any());
    }

    @Test
    void scan_skipsWhenDraining()
    {
        when(drainState.isDraining()).thenReturn(true);

        runScan();

        verify(taskMapper, never()).selectRunningTasks();
    }

    @Test
    void scan_exceptionSwallowed()
    {
        when(taskMapper.selectRunningTasks()).thenThrow(new RuntimeException("db down"));

        runScan();

        verify(taskService, never()).selectAiOutboundTaskByTaskId(any());
    }

    @Test
    void reconcile_disabled_skipsLock()
    {
        ReflectionTestUtils.setField(service, "scanEnabled", false);

        service.reconcileStaleCalls();

        verify(leaderLock, never()).tryRun(anyString(), any(Duration.class), any());
    }

    @Test
    void reconcile_noStale_noop()
    {
        when(calleeMapper.selectStaleCallingCallees(10)).thenReturn(Collections.emptyList());

        runReconcile();

        verify(finalizer, never()).finalizeCallee(anyLong(), any(), eq(true), anyInt(), anyString());
        verify(calleeMapper, never()).updateNextRetryTime(anyLong(), any(), anyString());
    }

    @Test
    void reconcile_connectedCallee_finalizes()
    {
        AiOutboundCallee stale = callee(1L, TASK_ID);
        stale.setRecordId(66L);
        when(calleeMapper.selectStaleCallingCallees(10)).thenReturn(Collections.singletonList(stale));
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task("1", 10));

        runReconcile();

        verify(finalizer).finalizeCallee(eq(TASK_ID), eq(stale), eq(true), eq(0), eq("RECONCILE"));
    }

    @Test
    void reconcile_staleWithRetryRemaining_backoff()
    {
        AiOutboundCallee stale = callee(1L, TASK_ID);
        stale.setRetryTimes(0);
        when(calleeMapper.selectStaleCallingCallees(10)).thenReturn(Collections.singletonList(stale));
        AiOutboundTask task = task("1", 10);
        task.setRetryCount(2);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        runReconcile();

        ArgumentCaptor<AiOutboundCallee> captor = ArgumentCaptor.forClass(AiOutboundCallee.class);
        verify(calleeMapper).updateCalleeStatus(captor.capture());
        AiOutboundCallee update = captor.getValue();
        assertThat(update.getCalleeId()).isEqualTo(1L);
        assertThat(update.getCallStatus()).isEqualTo("0");
        assertThat(update.getRetryTimes()).isEqualTo(1);
        assertThat(update.getLastRetryTime()).isNotNull();
        assertThat(update.getFailReason()).contains("对账触发重试");
        assertThat(update.getUpdateBy()).isEqualTo("outbound-reconcile");
        verify(calleeMapper).updateNextRetryTime(eq(1L), any(Date.class), eq("outbound-reconcile"));
    }

    @Test
    void reconcile_retryExhausted_marksFailed()
    {
        AiOutboundCallee stale = callee(1L, TASK_ID);
        stale.setRetryTimes(2);
        when(calleeMapper.selectStaleCallingCallees(10)).thenReturn(Collections.singletonList(stale));
        AiOutboundTask task = task("1", 10);
        task.setRetryCount(2);
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(task);

        runReconcile();

        verify(finalizer).markCalleeFailed(eq(task), eq(stale), contains("对账判定失败"));
        verify(calleeMapper, never()).updateNextRetryTime(anyLong(), any(), anyString());
    }

    @Test
    void reconcile_taskMissing_skips()
    {
        when(calleeMapper.selectStaleCallingCallees(10))
                .thenReturn(Collections.singletonList(callee(1L, TASK_ID)));
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID)).thenReturn(null);

        runReconcile();

        verify(calleeMapper, never()).updateCalleeStatus(any());
    }

    @Test
    void reconcile_calleeExceptionSwallowed()
    {
        when(calleeMapper.selectStaleCallingCallees(10))
                .thenReturn(Collections.singletonList(callee(1L, TASK_ID)));
        when(taskService.selectAiOutboundTaskByTaskId(TASK_ID))
                .thenThrow(new RuntimeException("任务表异常"));

        runReconcile();

        verify(calleeMapper, never()).updateCalleeStatus(any());
    }

    @Test
    void onCallEvent_delegatesToCallback()
    {
        Map<String, Object> params = Collections.singletonMap("callId", "c-1");

        service.onCallEvent("uuid-1", "ANSWERED", params);

        verify(eventCallback).handle("uuid-1", "ANSWERED", params);
    }

    @Test
    void shutdown_nullPoolSafe()
    {
        service.shutdown();
    }

    @Test
    void shutdown_shutsPoolDown()
    {
        DirectExecutorService pool = new DirectExecutorService();
        ReflectionTestUtils.setField(service, "dialPool", pool);

        service.shutdown();

        assertThat(pool.isShutdown()).isTrue();
    }

    @Test
    void checkSimulateSwitch_createsPoolAndSkipsProfileCheckWhenDisabled()
    {
        ReflectionTestUtils.setField(service, "simulateAnswer", false);
        ReflectionTestUtils.setField(service, "dialConcurrent", 80);
        when(executorRegistry.create("outbound-dial", 64, 64, 1000))
                .thenReturn(new DirectExecutorService());

        service.checkSimulateSwitch();

        verify(executorRegistry).create("outbound-dial", 64, 64, 1000);
        verify(environment, never()).getActiveProfiles();
    }

    @Test
    void checkSimulateSwitch_prodProfileWarnsButDoesNotThrow()
    {
        ReflectionTestUtils.setField(service, "simulateAnswer", true);
        ReflectionTestUtils.setField(service, "dialConcurrent", 4);
        when(environment.getActiveProfiles()).thenReturn(new String[] { "prod" });
        when(executorRegistry.create(anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new DirectExecutorService());

        service.checkSimulateSwitch();

        verify(executorRegistry).create("outbound-dial", 4, 4, 1000);
        verify(environment).getActiveProfiles();
    }

    private void runScan()
    {
        service.scanRunningTasks();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(leaderLock).tryRun(eq("job:outbound-scan"), eq(Duration.ofSeconds(30)), captor.capture());
        captor.getValue().run();
    }

    private void runReconcile()
    {
        service.reconcileStaleCalls();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(leaderLock).tryRun(eq("job:outbound-reconcile"), eq(Duration.ofMinutes(10)), captor.capture());
        captor.getValue().run();
    }

    private AiOutboundTask task(String status, Integer maxConcurrent)
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(TASK_ID);
        task.setStatus(status);
        task.setMaxConcurrent(maxConcurrent);
        return task;
    }

    private AiOutboundTask taskById(Long taskId)
    {
        AiOutboundTask task = new AiOutboundTask();
        task.setTaskId(taskId);
        task.setStatus("0");
        return task;
    }

    private AiOutboundCallee callee(Long calleeId, Long taskId)
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        callee.setCalleeId(calleeId);
        callee.setTaskId(taskId);
        callee.setCallStatus("0");
        return callee;
    }

    /** 同步执行器：submit 即当前线程执行，保证断言时序确定 */
    private static class DirectExecutorService extends AbstractExecutorService
    {
        private volatile boolean shutdown;

        @Override
        public void shutdown()
        {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow()
        {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown()
        {
            return shutdown;
        }

        @Override
        public boolean isTerminated()
        {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return true;
        }

        @Override
        public void execute(Runnable command)
        {
            command.run();
        }
    }
}
