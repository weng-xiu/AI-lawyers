package ai.lawyers.system.service.lawyers.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 热线系统统一指标门面（T5-1）。
 *
 * <p>集中定义话务 / ACD / AI / 外呼 / 异步队列等业务指标，屏蔽 Micrometer 细节，
 * 业务代码只调用语义化方法。Micrometer 的 MeterRegistry 在引入
 * micrometer-registry-prometheus 后由 Spring Boot 自动装配；本类对 registry 做
 * 可空保护，未引入时所有埋点静默跳过，不影响业务。</p>
 *
 * <p>指标命名遵循 Prometheus 惯例（snake_case，单位后缀 _seconds / _total）：</p>
 * <ul>
 *   <li>hotline_call_total{result}            话务计数（inbound/answered/abandoned/queued）</li>
 *   <li>hotline_acd_assign_seconds            ACD 分配耗时</li>
 *   <li>hotline_acd_occupy_fail_total         ACD 坐席抢占失败</li>
 *   <li>hotline_ai_inflight                   AI 在途并发（gauge，由 Semaphore 上报）</li>
 *   <li>hotline_ai_call_total{kind,result}    AI 调用计数（chat/embed/rerank/asr × success/fail/reject/fallback）</li>
 *   <li>hotline_ai_first_response_seconds     AI 首响耗时</li>
 *   <li>hotline_rag_hit_total{route}          RAG 命中（vector/keyword/fused/none）</li>
 *   <li>hotline_outbound_total{result}        外呼计数（dial/answer/fail/retry）</li>
 *   <li>hotline_queue_size{queue}             Stream 队列积压（gauge）</li>
 *   <li>hotline_queue_dead_total{queue}       死信计数</li>
 *   <li>hotline_quality_total{result}         质检计数（success/fail）</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@Component
public class HotlineMetrics
{
    private static final String PREFIX = "hotline";

    @Autowired(required = false)
    @Qualifier("prometheusMeterRegistry")
    private MeterRegistry registry;

    /** 话务结果计数：result ∈ inbound/answered/abandoned/queued */
    public void incrementCall(String result)
    {
        counter("call.total", "result", result).increment();
    }

    /** ACD 分配耗时（毫秒入参，内部转秒） */
    public void recordAcdAssign(long elapsedMs)
    {
        timer("acd.assign.seconds").record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    /** ACD 坐席抢占失败（并发来电抢同一坐席仅一个成功，其余计此指标） */
    public void incrementAcdOccupyFail()
    {
        counter("acd.occupy.fail.total").increment();
    }

    /** AI 调用计数：kind ∈ chat/embed/rerank/asr；result ∈ success/fail/reject/fallback */
    public void incrementAi(String kind, String result)
    {
        counter("ai.call.total", "kind", kind, "result", result).increment();
    }

    /** AI 首响耗时（毫秒） */
    public void recordAiFirstResponse(long elapsedMs)
    {
        timer("ai.first.response.seconds").record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    /** RAG 命中：route ∈ vector/keyword/fused/none */
    public void incrementRagHit(String route)
    {
        counter("rag.hit.total", "route", route).increment();
    }

    /** 外呼计数：result ∈ dial/answer/fail/retry */
    public void incrementOutbound(String result)
    {
        counter("outbound.total", "result", result).increment();
    }

    /** 质检计数：result ∈ success/fail */
    public void incrementQuality(String result)
    {
        counter("quality.total", "result", result).increment();
    }

    /** 队列死信计数 */
    public void incrementQueueDead(String queue)
    {
        counter("queue.dead.total", "queue", queue).increment();
    }

    /** 注册队列积压 gauge（valueFunction 返回当前积压量，如 Stream pendingCount） */
    public void gaugeQueueSize(String queue, Object obj, ToDoubleFunction<Object> valueFunction)
    {
        if (registry == null)
        {
            return;
        }
        registry.gauge(PREFIX + "_queue_size", Tags.of("queue", queue), obj, valueFunction);
    }

    /** 注册 AI 在途并发 gauge（valueFunction 返回当前在途许可占用数） */
    public void gaugeAiInflight(Object obj, ToDoubleFunction<Object> valueFunction)
    {
        if (registry == null)
        {
            return;
        }
        registry.gauge(PREFIX + "_ai_inflight", Tags.empty(), obj, valueFunction);
    }

    private Counter counter(String name, String... kv)
    {
        if (registry == null)
        {
            return NoopCounters.NOOP;
        }
        return Counter.builder(PREFIX + "_" + name)
                .tags(kv == null ? Tags.empty() : Tags.of(kv))
                .register(registry);
    }

    private Timer timer(String name)
    {
        if (registry == null)
        {
            return NoopCounters.NOOP_TIMER;
        }
        return Timer.builder(PREFIX + "_" + name)
                .register(registry);
    }

    /** 未引入 MeterRegistry 时的空实现，保证埋点永不抛异常 */
    private static final class NoopCounters
    {
        static final io.micrometer.core.instrument.simple.SimpleMeterRegistry NOOP_REGISTRY =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        static final Counter NOOP = Counter.builder("hotline_noop").register(NOOP_REGISTRY);
        static final Timer NOOP_TIMER = Timer.builder("hotline_noop_timer").register(NOOP_REGISTRY);
    }
}
