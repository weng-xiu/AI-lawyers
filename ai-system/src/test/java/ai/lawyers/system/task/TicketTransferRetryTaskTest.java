package ai.lawyers.system.task;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.service.lawyers.IAiTicketTransferService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 转办推送重试任务测试：取得集群锁后处理重试队列、未取得锁不处理、
 * 处理异常在任务内吞掉不外抛。
 *
 * @author ai-lawyers
 */
class TicketTransferRetryTaskTest
{
    private IAiTicketTransferService ticketTransferService;

    private RedisLeaderLock leaderLock;

    private TicketTransferRetryTask task;

    @BeforeEach
    void setUp()
    {
        ticketTransferService = mock(IAiTicketTransferService.class);
        leaderLock = mock(RedisLeaderLock.class);
        task = new TicketTransferRetryTask();
        ReflectionTestUtils.setField(task, "ticketTransferService", ticketTransferService);
        ReflectionTestUtils.setField(task, "leaderLock", leaderLock);
    }

    @Test
    void 取得锁后处理重试队列()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenAnswer(invocation ->
        {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });
        when(ticketTransferService.processRetryQueue()).thenReturn(3);

        task.retryPendingTransfers();

        verify(ticketTransferService).processRetryQueue();
    }

    @Test
    void 未取得锁时不处理()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenReturn(false);

        task.retryPendingTransfers();

        verify(ticketTransferService, never()).processRetryQueue();
    }

    @Test
    void 处理异常被吞掉不外抛()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenAnswer(invocation ->
        {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });
        when(ticketTransferService.processRetryQueue()).thenThrow(new IllegalStateException("推送失败"));

        assertThatCode(() -> task.retryPendingTransfers()).doesNotThrowAnyException();
        verify(ticketTransferService).processRetryQueue();
    }

    @Test
    void 空队列不抛异常()
    {
        when(leaderLock.tryRun(anyString(), any(Duration.class), any(Runnable.class))).thenAnswer(invocation ->
        {
            invocation.getArgument(2, Runnable.class).run();
            return true;
        });
        when(ticketTransferService.processRetryQueue()).thenReturn(0);

        assertThatCode(() -> task.retryPendingTransfers()).doesNotThrowAnyException();
    }
}
