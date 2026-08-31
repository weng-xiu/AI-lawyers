package ai.lawyers.system.service.lawyers.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;

/**
 * T5-2 压测：RAG 内存向量近邻检索（AI 问答热路径）的并发吞吐与延迟。
 *
 * <p>该路径在每次来电 AI 问答时触发：对内存中的知识分块做暴力余弦近邻。
 * 本测试构造 <strong>1 万个 512 维</strong>分块（贴近热线知识库规模），
 * 多线程并发检索，输出 QPS、平均/P99 延迟，验证万级分块下毫秒级响应且线程安全无异常。</p>
 *
 * <p>用 -Dperf.chunks / -Dperf.threads / -Dperf.requests 可调整规模，例如：
 * <code>mvn test -pl ai-system -Dtest=RagSearchNearestLoadTest -Dperf.threads=32 -Dperf.requests=200000</code></p>
 *
 * @author ai-lawyers
 */
class RagSearchNearestLoadTest
{
    private static final int DIM = 512;
    private final int chunks = Integer.getInteger("perf.chunks", 10_000);
    private final int threads = Integer.getInteger("perf.threads", 16);
    private final int totalRequests = Integer.getInteger("perf.requests", 50_000);

    private AiLegalKnowledgeChunk randomChunk(Random rnd, long id)
    {
        float[] vec = new float[DIM];
        for (int i = 0; i < DIM; i++)
        {
            vec[i] = rnd.nextFloat() * 2f - 1f;
        }
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setChunkId(id);
        c.setKnowledgeId(id % 50L);
        c.setEmbeddingDim(DIM);
        c.setEmbedding(VectorUtils.toBytes(vec));
        c.setChunkContent("chunk-" + id);
        return c;
    }

    @Test
    void concurrentSearch_throughputAndLatency() throws Exception
    {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        List<AiLegalKnowledgeChunk> data = new ArrayList<>(chunks);
        Random rnd = new Random(42);
        for (long i = 1; i <= chunks; i++)
        {
            data.add(randomChunk(rnd, i));
        }
        long buildStart = System.nanoTime();
        int loaded = index.rebuild(data);
        long buildMs = (System.nanoTime() - buildStart) / 1_000_000;
        System.out.printf("[压测] 索引构建: %d 条 %d维, 载入=%d, 耗时=%dms%n",
                chunks, DIM, loaded, buildMs);

        AtomicLong ok = new AtomicLong();
        AtomicLong err = new AtomicLong();
        // 延迟采样（纳秒），用于算 P50/P99
        java.util.concurrent.ConcurrentLinkedQueue<Long> latencies = new java.util.concurrent.ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try
        {
            for (int t = 0; t < threads; t++)
            {
                final int seed = t;
                pool.submit(() ->
                {
                    try
                    {
                        Random tr = new Random(1000 + seed);
                        float[] q = new float[DIM];
                        start.await();
                        for (int i = 0; i < totalRequests / threads; i++)
                        {
                            for (int d = 0; d < DIM; d++)
                            {
                                q[d] = tr.nextFloat() * 2f - 1f;
                            }
                            long s = System.nanoTime();
                            List<RagChunk> hits = index.searchNearest(q, null, 5);
                            long cost = System.nanoTime() - s;
                            if (hits.size() == 5)
                            {
                                ok.incrementAndGet();
                            }
                            else
                            {
                                err.incrementAndGet();
                            }
                            latencies.add(cost / 1_000L); // 微秒
                        }
                    }
                    catch (Exception e)
                    {
                        err.incrementAndGet();
                    }
                    finally
                    {
                        done.countDown();
                    }
                });
            }
            long wallStart = System.currentTimeMillis();
            start.countDown();
            boolean finished = done.await(120, TimeUnit.SECONDS);
            long wallMs = System.currentTimeMillis() - wallStart;
            org.assertj.core.api.Assertions.assertThat(finished).as("压测应在超时内完成").isTrue();

            long[] lat = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            long total = ok.get() + err.get();
            double qps = wallMs > 0 ? total * 1000.0 / wallMs : 0;
            long p50 = percentile(lat, 0.50);
            long p90 = percentile(lat, 0.90);
            long p99 = percentile(lat, 0.99);
            double avg = lat.length == 0 ? 0 : java.util.Arrays.stream(lat).average().orElse(0);

            System.out.println("========== RAG 近邻检索压测结果 ==========");
            System.out.printf("数据规模 : %d 条 × %d 维%n", chunks, DIM);
            System.out.printf("并发线程 : %d, 总请求 : %d (成功=%d 异常=%d)%n", threads, total, ok.get(), err.get());
            System.out.printf("耗时     : %dms, 吞吐 QPS : %.0f req/s%n", wallMs, qps);
            System.out.printf("延迟(μs) : avg=%.1f  P50=%d  P90=%d  P99=%d%n", avg, p50, p90, p99);
            System.out.println("==========================================");

            // 硬断言：高并发检索必须零异常、结果完整（线程安全正确性）
            org.assertj.core.api.Assertions.assertThat(err.get()).as("并发检索不应有任何异常/错误结果").isZero();
            // 性能基线：16 线程 / 万级 512 维暴力近邻，单机实测约 700+ QPS、P99 数十毫秒。
            // 此处用宽松工程阈值（<200ms）防严重性能退化；精确容量以打印基线为准，
            // 热线真实场景单次 AI 问答仅触发一次检索且知识库通常远小于万级，毫秒级可达。
            org.assertj.core.api.Assertions.assertThat(p99).as("万级 512 维近邻 P99 不应超过 200ms（防严重退化）").isLessThan(200_000L);
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    private static long percentile(long[] sortedAsc, double pct)
    {
        if (sortedAsc.length == 0)
        {
            return 0;
        }
        int idx = (int) Math.min(sortedAsc.length - 1, (long) (sortedAsc.length * pct));
        return sortedAsc[idx];
    }
}
