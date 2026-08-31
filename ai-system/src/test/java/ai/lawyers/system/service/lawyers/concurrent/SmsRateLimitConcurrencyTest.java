package ai.lawyers.system.service.lawyers.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5-2 短信频控并发不变量测试。
 *
 * <p>生产实现见 {@code SmsServiceImpl.tryAcquireDailyQuota}：以 Redis
 * {@code INCR sms:limit:{phone}:{date}} 原子计数，{@code count <= dailyLimit} 才放行，
 * 首次计数设置当日 24 点过期。Redis INCR 本身原子，核心不变量是：
 * <strong>同一号码同一自然日，无论多少线程并发发，放行条数绝不超过 dailyLimit</strong>。</p>
 *
 * <p>此处用 {@link AtomicLong#incrementAndGet()} 复刻 Redis INCR 的原子自增语义，
 * 高并发验证频控判定逻辑（计数 ≤ 阈值放行）。真实 Redis 原子性属集成/压测范畴。</p>
 *
 * @author ai-lawyers
 */
class SmsRateLimitConcurrencyTest
{
    /** 复刻 tryAcquireDailyQuota 的放行判定：INCR 后计数 <= 日上限才放行 */
    private static boolean tryAcquire(AtomicLong counter, int dailyLimit)
    {
        long count = counter.incrementAndGet();
        return count <= dailyLimit;
    }

    @Test
    void dailyLimit_concurrentSend_neverExceedsQuota() throws InterruptedException
    {
        int dailyLimit = 5;          // 单号码日发上限
        int concurrentSends = 100;   // 并发发送请求
        AtomicLong counter = new AtomicLong(0);
        AtomicLong allowed = new AtomicLong();
        AtomicLong rejected = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrentSends);
        ExecutorService pool = Executors.newFixedThreadPool(32);
        try
        {
            for (int i = 0; i < concurrentSends; i++)
            {
                pool.submit(() ->
                {
                    try
                    {
                        start.await();
                        if (tryAcquire(counter, dailyLimit))
                        {
                            allowed.incrementAndGet();
                        }
                        else
                        {
                            rejected.incrementAndGet();
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

        assertThat(allowed.get()).as("高并发下放行条数必须严格等于日上限，绝不超额")
                .isEqualTo(dailyLimit);
        assertThat(rejected.get()).isEqualTo(concurrentSends - dailyLimit);
        assertThat(counter.get()).isEqualTo(concurrentSends);
    }

    @Test
    void dailyLimit_boundaryValues()
    {
        // 边界：第 N 条放行、第 N+1 条拒绝
        AtomicLong counter = new AtomicLong(0);
        int limit = 3;
        assertThat(tryAcquire(counter, limit)).isTrue();  // 1
        assertThat(tryAcquire(counter, limit)).isTrue();  // 2
        assertThat(tryAcquire(counter, limit)).isTrue();  // 3
        assertThat(tryAcquire(counter, limit)).isFalse(); // 4 超额
        assertThat(tryAcquire(counter, limit)).isFalse(); // 5
    }
}
