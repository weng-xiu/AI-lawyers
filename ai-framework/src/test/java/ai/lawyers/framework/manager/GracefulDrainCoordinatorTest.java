package ai.lawyers.framework.manager;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.cluster.InstanceDrainState;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-C6：优雅停机排空协调器单测。
 * 关闭 WS 通知（静态门面无法在单测中验证）、ESL bridge 留空，
 * 只编排：排空态 → 等待在途通话 → 归零/超时/异常均不卡死停机流程。
 */
public class GracefulDrainCoordinatorTest
{
    private InstanceDrainState drainState;

    private AiCallDialLogMapper dialLogMapper;

    private GracefulDrainCoordinator coordinator;

    private ContextClosedEvent closedEvent;

    @BeforeEach
    public void setUp()
    {
        drainState = mock(InstanceDrainState.class);
        dialLogMapper = mock(AiCallDialLogMapper.class);
        coordinator = new GracefulDrainCoordinator();
        ReflectionTestUtils.setField(coordinator, "drainState", drainState);
        ReflectionTestUtils.setField(coordinator, "dialLogMapper", dialLogMapper);
        ReflectionTestUtils.setField(coordinator, "eslBridge", null);
        ReflectionTestUtils.setField(coordinator, "notifyWebsocket", false);
        ReflectionTestUtils.setField(coordinator, "settleMs", 10);
        ApplicationContext ctx = mock(ApplicationContext.class);
        closedEvent = new ContextClosedEvent(ctx);
    }

    /** 无在途通话：立即排空完成，进入排空态恰好一次 */
    @Test
    public void drainZeroActiveCompletesImmediately()
    {
        when(dialLogMapper.countActiveDials()).thenReturn(0);

        coordinator.onApplicationEvent(closedEvent);

        verify(drainState).beginDrain();
        verify(dialLogMapper).countActiveDials();
    }

    /** 先有 2 通、一轮后轮询归零：等待并正常完成 */
    @Test
    public void drainWaitsUntilActiveZero()
    {
        when(dialLogMapper.countActiveDials()).thenReturn(2).thenReturn(0);
        ReflectionTestUtils.setField(coordinator, "maxWaitSeconds", 3);
        ReflectionTestUtils.setField(coordinator, "pollIntervalSeconds", 1);

        long t0 = System.currentTimeMillis();
        coordinator.onApplicationEvent(closedEvent);
        long elapsed = System.currentTimeMillis() - t0;

        verify(drainState).beginDrain();
        verify(dialLogMapper, atLeastOnce()).countActiveDials();
        assertTrue(elapsed >= 900, "应至少等待一个轮询周期");
    }

    /** 持续有在途通话：到 maxWait 超时强制退出，不阻塞停机 */
    @Test
    public void drainTimeoutDoesNotHangShutdown()
    {
        when(dialLogMapper.countActiveDials()).thenReturn(3);
        ReflectionTestUtils.setField(coordinator, "maxWaitSeconds", 1);
        ReflectionTestUtils.setField(coordinator, "pollIntervalSeconds", 1);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            long t0 = System.currentTimeMillis();
            coordinator.onApplicationEvent(closedEvent);
            long elapsed = System.currentTimeMillis() - t0;

            verify(drainState).beginDrain();
            assertTrue(elapsed >= 900, "应等到超时窗口");
            assertTrue(elapsed < 3000, "不应超过 maxWait 太多");
        });
    }

    /** 统计查询异常：放弃等待但仍进入排空态，停机不中断 */
    @Test
    public void countErrorAbortsWaitingButKeepsDraining()
    {
        when(dialLogMapper.countActiveDials()).thenThrow(new RuntimeException("datasource closed"));

        coordinator.onApplicationEvent(closedEvent);

        verify(drainState).beginDrain();
    }

    /** 事件重复投递（父子上下文等）：排空只执行一次 */
    @Test
    public void duplicateEventExecutesOnce()
    {
        when(dialLogMapper.countActiveDials()).thenReturn(0);

        coordinator.onApplicationEvent(closedEvent);
        coordinator.onApplicationEvent(closedEvent);

        verify(drainState, times(1)).beginDrain();
    }

    /** 停机排空路径不应产生重置副作用 */
    @Test
    public void resetNeverCalledOnShutdownDrain()
    {
        when(dialLogMapper.countActiveDials()).thenReturn(0);

        coordinator.onApplicationEvent(closedEvent);

        verify(drainState, never()).reset();
    }
}
