package ai.lawyers.system.service.lawyers.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5-2 并发抢占不变量测试。
 *
 * <p>生产中 ACD 坐席抢占（{@code AiCallAgentStatusMapper.occupyAgentIfFree}：
 * {@code update ai_call_agent_status set call_status='1' where agent_id=? and call_status='0'}）
 * 与外呼号码领取（{@code AiOutboundCalleeMapper.claimCallee}：
 * {@code update ai_outbound_callee set call_status='1' where callee_id=? and call_status='0'}）
 * 的原子性由数据库行锁 + 条件更新保证，Mapper 返回影响行数 1=抢占成功、0=已被抢占。</p>
 *
 * <p>单元测试无法真实连 MySQL 验证 InnoDB 行锁（属集成/压测范畴），此处用
 * {@link AtomicInteger#compareAndSet} 精确复刻 CAS 语义（期望旧值 0 才置 1），
 * 高并发下验证业务最关键不变量：<strong>同一资源无论多少线程同时抢，最多成功一次</strong>。
 * 这与线上 SQL 的 {@code where status='0'} 条件更新是同一并发模型。</p>
 *
 * @author ai-lawyers
 */
class CasDispatchingConcurrencyTest
{
    /** 复刻 occupyAgentIfFree/claimCallee：CAS 0->1，成功返回 1，已被抢占返回 0 */
    private static int casOccupy(AtomicInteger state)
    {
        return state.compareAndSet(0, 1) ? 1 : 0;
    }

    /**
     * 模拟 ACD：N 个来电同时抢同一个空闲坐席，断言只有一个来电接通、其余全部抢占失败。
     */
    @Test
    void acd_oneAgent_manyCalls_onlyOneOccupies() throws InterruptedException
    {
        int callers = 200;
        AtomicInteger agentCallStatus = new AtomicInteger(0); // 单个坐席，0=空闲
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(callers);
        ExecutorService pool = Executors.newFixedThreadPool(32);
        try
        {
            for (int i = 0; i < callers; i++)
            {
                pool.submit(() ->
                {
                    try
                    {
                        start.await(); // 所有线程同时释放，最大化竞争
                        if (casOccupy(agentCallStatus) == 1)
                        {
                            success.incrementAndGet();
                        }
                        else
                        {
                            fail.incrementAndGet(); // 上层据此换下一位候选坐席
                        }
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                    finally
                    {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
        finally
        {
            pool.shutdownNow();
        }

        assertThat(success.get()).as("同一空闲坐席在 %d 个并发来电下最多被接通一次", callers).isEqualTo(1);
        assertThat(fail.get()).isEqualTo(callers - 1);
        assertThat(agentCallStatus.get()).isEqualTo(1);
    }

    /**
     * 模拟外呼：M 个待拨号码，T 个线程并发领取（每号码 N 次重复领取尝试），
     * 断言每个号码只被领取一次（无重复拨号）。
     */
    @Test
    void outbound_manyCallees_concurrentClaim_noDuplicate() throws InterruptedException
    {
        int calleeCount = 50;
        int threads = 16;
        int claimAttemptsPerCallee = 8; // 每号码被多实例/多线程重复尝试领取
        AtomicInteger[] callees = new AtomicInteger[calleeCount];
        for (int i = 0; i < calleeCount; i++)
        {
            callees[i] = new AtomicInteger(0);
        }
        AtomicInteger claimed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try
        {
            for (int t = 0; t < threads; t++)
            {
                pool.submit(() ->
                {
                    try
                    {
                        start.await();
                        for (int round = 0; round < claimAttemptsPerCallee; round++)
                        {
                            for (int c = 0; c < calleeCount; c++)
                            {
                                if (casOccupy(callees[c]) == 1)
                                {
                                    claimed.incrementAndGet();
                                }
                                else
                                {
                                    skipped.incrementAndGet();
                                }
                            }
                        }
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                    finally
                    {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        }
        finally
        {
            pool.shutdownNow();
        }

        assertThat(claimed.get()).as("每个号码只应被成功领取一次").isEqualTo(calleeCount);
        assertThat(skipped.get()).isEqualTo(threads * claimAttemptsPerCallee * calleeCount - calleeCount);
        for (AtomicInteger c : callees)
        {
            assertThat(c.get()).as("号码终态必须为 1（呼叫中）").isEqualTo(1);
        }
    }

    /**
     * 模拟坐席挂断后释放（call_status 1->0），下一来电可再次抢占成功，验证状态可回收复用。
     */
    @Test
    void acd_afterHangup_agentReusable()
    {
        AtomicInteger agent = new AtomicInteger(0);
        assertThat(casOccupy(agent)).isEqualTo(1);   // 第一通接通
        assertThat(casOccupy(agent)).isEqualTo(0);   // 通话中再来电被拒
        agent.set(0);                                 // 挂断 + 话后完成，释放回空闲
        assertThat(casOccupy(agent)).isEqualTo(1);   // 下一通可再次接通
    }
}
