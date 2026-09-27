package ai.lawyers.system.service.lawyers.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *   <li>hotline_voice_vad_ms                  A5：VAD 起始判定耗时（预算 80ms，超预算 WARN）</li>
 *   <li>hotline_voice_asr_first_ms            A5：ASR 流式首包（start→首 partial，预算 280ms）</li>
 *   <li>hotline_voice_tts_first_ms            A5：TTS 首包（tts 帧→首音频分片，预算 200ms）</li>
 *   <li>hotline_voice_e2e_first_ms{source}    A5：端到端首响（start=会话起点≈1.2s；robot=E3 机器人回合 ASR final→首 TTS 分片）</li>
 *   <li>hotline_voice_bargein_stop_ms         A5：打断停止耗时（VAD/bargein→tts_end 入队，预算 300ms）</li>
 *   <li>hotline_voice_rag_ms                  E3：机器人 RAG 检索（预算 40ms）</li>
 *   <li>hotline_voice_llm_first_token_ms      E3：机器人 LLM 首 token（预算 600ms，非流式期口径=整段返回）</li>
 * </ul>
 *
 * <p>Prometheus 导出时 Timer 自动追加 {@code _seconds} 后缀（如
 * {@code hotline_voice_vad_ms_seconds}），上表为逻辑指标名。</p>
 *
 * @author ai-lawyers
 */
@Component
public class HotlineMetrics
{
    private static final Logger log = LoggerFactory.getLogger(HotlineMetrics.class);

    private static final String PREFIX = "hotline";

    /* ---- A5 语音链路延迟预算（毫秒），超预算记 WARN 供治理巡检 ---- */
    public static final long BUDGET_VOICE_VAD_MS = 80;
    public static final long BUDGET_VOICE_ASR_FIRST_MS = 280;
    public static final long BUDGET_VOICE_TTS_FIRST_MS = 200;
    public static final long BUDGET_VOICE_E2E_FIRST_MS = 1200;
    public static final long BUDGET_VOICE_BARGEIN_STOP_MS = 300;
    /** E3：Prompt 装配 + RAG 检索预算（走缓存/内存索引） */
    public static final long BUDGET_VOICE_RAG_MS = 40;
    /** E3：LLM 首 token 预算（当前为非流式 chat，口径=调用返回耗时，流式化后为真实首 token） */
    public static final long BUDGET_VOICE_LLM_FIRST_TOKEN_MS = 600;

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

    /* ================= A5：语音链路首响/打断延迟埋点（超预算 WARN 治理） ================= */

    /** VAD 起始判定耗时（预算 {@link #BUDGET_VOICE_VAD_MS}ms） */
    public void recordVoiceVadMs(long elapsedMs, String sessionId)
    {
        timer("voice.vad.ms").record(elapsedMs, TimeUnit.MILLISECONDS);
        if (elapsedMs > BUDGET_VOICE_VAD_MS)
        {
            log.warn("[A5预算超支] VAD 起始判定 {}ms > {}ms, sessionId={}", elapsedMs, BUDGET_VOICE_VAD_MS, sessionId);
        }
    }

    /** ASR 流式首包耗时（预算 {@link #BUDGET_VOICE_ASR_FIRST_MS}ms） */
    public void recordVoiceAsrFirstMs(long elapsedMs, String sessionId)
    {
        timer("voice.asr.first.ms").record(elapsedMs, TimeUnit.MILLISECONDS);
        if (elapsedMs > BUDGET_VOICE_ASR_FIRST_MS)
        {
            log.warn("[A5预算超支] ASR 首包 {}ms > {}ms, sessionId={}", elapsedMs, BUDGET_VOICE_ASR_FIRST_MS, sessionId);
        }
    }

    /** TTS 首包耗时（预算 {@link #BUDGET_VOICE_TTS_FIRST_MS}ms） */
    public void recordVoiceTtsFirstMs(long elapsedMs, String sessionId)
    {
        timer("voice.tts.first.ms").record(elapsedMs, TimeUnit.MILLISECONDS);
        if (elapsedMs > BUDGET_VOICE_TTS_FIRST_MS)
        {
            log.warn("[A5预算超支] TTS 首包 {}ms > {}ms, sessionId={}", elapsedMs, BUDGET_VOICE_TTS_FIRST_MS, sessionId);
        }
    }

    /** 端到端首响耗时（预算 {@link #BUDGET_VOICE_E2E_FIRST_MS}ms，Grafana 看 P50/P95/P99） */
    public void recordVoiceE2eFirstMs(long elapsedMs, String sessionId)
    {
        recordVoiceE2eFirstMs(elapsedMs, "start", sessionId);
    }

    /**
     * 端到端首响耗时（按来源区分）：source ∈ start（会话 start→首个 TTS 分片）/
     * robot（E3 机器人回合：ASR final 问题完整→应答首个 TTS 分片，对账 4.1 节 ≈1.2s 预算链）。
     */
    public void recordVoiceE2eFirstMs(long elapsedMs, String source, String sessionId)
    {
        timer("voice.e2e.first.ms", "source", source).record(elapsedMs, TimeUnit.MILLISECONDS);
        if (elapsedMs > BUDGET_VOICE_E2E_FIRST_MS)
        {
            log.warn("[A5预算超支] 端到端首响 {}ms > {}ms, source={}, sessionId={}",
                    elapsedMs, BUDGET_VOICE_E2E_FIRST_MS, source, sessionId);
        }
    }

    /** E3：语音机器人 RAG 检索耗时（预算 {@link #BUDGET_VOICE_RAG_MS}ms） */
    public void recordVoiceRagMs(long elapsedMs, String sessionId)
    {
        timer("voice.rag.ms").record(elapsedMs, TimeUnit.MILLISECONDS);
        if (elapsedMs > BUDGET_VOICE_RAG_MS)
        {
            log.warn("[A5预算超支] RAG 检索 {}ms > {}ms, sessionId={}", elapsedMs, BUDGET_VOICE_RAG_MS, sessionId);
        }
    }

    /** E3：语音机器人 LLM 首 token 耗时（预算 {@link #BUDGET_VOICE_LLM_FIRST_TOKEN_MS}ms；非流式调用期口径=整段返回耗时） */
    public void recordVoiceLlmFirstTokenMs(long elapsedMs, String sessionId)
    {
        timer("voice.llm.first.token.ms").record(elapsedMs, TimeUnit.MILLISECONDS);
        if (elapsedMs > BUDGET_VOICE_LLM_FIRST_TOKEN_MS)
        {
            log.warn("[A5预算超支] LLM 首 token {}ms > {}ms, sessionId={}",
                    elapsedMs, BUDGET_VOICE_LLM_FIRST_TOKEN_MS, sessionId);
        }
    }

    /** barge-in 打断停止耗时（预算 {@link #BUDGET_VOICE_BARGEIN_STOP_MS}ms）；trigger ∈ vad/bargein */
    public void recordVoiceBargeinStopMs(long elapsedMs, String trigger, String sessionId)
    {
        timer("voice.bargein.stop.ms", "trigger", trigger).record(elapsedMs, TimeUnit.MILLISECONDS);
        if (elapsedMs > BUDGET_VOICE_BARGEIN_STOP_MS)
        {
            log.warn("[A5预算超支] barge-in 停止 {}ms > {}ms, trigger={}, sessionId={}",
                    elapsedMs, BUDGET_VOICE_BARGEIN_STOP_MS, trigger, sessionId);
        }
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

    private Timer timer(String name, String... kv)
    {
        if (registry == null)
        {
            return NoopCounters.NOOP_TIMER;
        }
        return Timer.builder(PREFIX + "_" + name)
                .tags(kv == null ? Tags.empty() : Tags.of(kv))
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
