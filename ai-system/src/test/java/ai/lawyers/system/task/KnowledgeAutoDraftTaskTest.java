package ai.lawyers.system.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.mapper.SysUserMapper;
import ai.lawyers.system.service.lawyers.KnowledgeDraftService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.queue.MessageNotifyDispatcher;

/**
 * P1-10：{@link KnowledgeAutoDraftTask} 定时扫描测试——
 * 开关关闭空转；抢锁成功后按配置参数扫描，新增草稿才给管理员发提醒；扫描异常不外抛。
 *
 * @author ai-lawyers
 */
class KnowledgeAutoDraftTaskTest
{
    private KnowledgeAutoDraftTask task;
    private KnowledgeDraftService draftService;
    private RedisLeaderLock leaderLock;
    private MessageNotifyDispatcher dispatcher;
    private SysUserMapper sysUserMapper;

    @BeforeEach
    void setUp()
    {
        task = new KnowledgeAutoDraftTask();
        draftService = mock(KnowledgeDraftService.class);
        leaderLock = mock(RedisLeaderLock.class);
        dispatcher = mock(MessageNotifyDispatcher.class);
        sysUserMapper = mock(SysUserMapper.class);

        ReflectionTestUtils.setField(task, "knowledgeDraftService", draftService);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
        ReflectionTestUtils.setField(task, "messageNotifyDispatcher", dispatcher);
        ReflectionTestUtils.setField(task, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "days", 7);
        ReflectionTestUtils.setField(task, "minScore", 90.0);
        ReflectionTestUtils.setField(task, "limit", 20);

        // 抢锁成功：透传执行 Runnable
        doAnswer(inv ->
        {
            ((Runnable) inv.getArgument(2)).run();
            return true;
        }).when(leaderLock).tryRun(anyString(), any(), any(Runnable.class));
    }

    @Test
    void scan_disabled_doesNothing()
    {
        ReflectionTestUtils.setField(task, "enabled", false);

        task.scan();

        verify(leaderLock, never()).tryRun(anyString(), any(), any(Runnable.class));
        verify(draftService, never()).scanQualityCalls(any(), any(), any());
    }

    @Test
    void scan_createsDrafts_notifiesAdminsWithConfigParams()
    {
        when(draftService.scanQualityCalls(eq(7), eq(BigDecimal.valueOf(90.0)), eq(20)))
                .thenReturn(3);
        when(sysUserMapper.selectUserIdsByRoleKey("admin")).thenReturn(Arrays.asList(1L, 2L));

        task.scan();

        verify(draftService).scanQualityCalls(eq(7), eq(BigDecimal.valueOf(90.0)), eq(20));
        verify(dispatcher).notify(eq(1L), eq("1"), anyString(), anyString(),
                eq("knowledge"), eq(null), eq("system"));
        verify(dispatcher).notify(eq(2L), eq("1"), anyString(), anyString(),
                eq("knowledge"), eq(null), eq("system"));
    }

    @Test
    void scan_zeroCreated_skipsNotify()
    {
        when(draftService.scanQualityCalls(any(), any(), any())).thenReturn(0);

        task.scan();

        verify(dispatcher, never()).notify(any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void scan_serviceThrows_doesNotPropagate()
    {
        when(draftService.scanQualityCalls(any(), any(), any()))
                .thenThrow(new RuntimeException("llm unavailable"));

        // 异常被任务内吞掉，不影响下一轮调度
        task.scan();
    }
}
