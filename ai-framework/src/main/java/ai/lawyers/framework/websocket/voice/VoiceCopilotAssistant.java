package ai.lawyers.framework.websocket.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistResult;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistService;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotLaw;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotTicket;

/**
 * F4 坐席 Copilot 实时辅助执行器（P5 从 {@link VoiceSession} 拆出）。
 *
 * <p>ASR final 文本滚动入缓冲；缓冲达最小字数且距上次触发 ≥ 节流间隔时提交
 * 辅助回合。回合单线程执行（LLM/RAG 调用不阻塞 ASR 回调线程），迟到结果按
 * 回合号丢弃。产出后经宿主回调下发要素/法条/工单/建议动作/口播话术五类帧。</p>
 *
 * <p>与 E4 情绪联动不同，Copilot 服务对象是坐席，不做 caller 门禁——当前生产
 * 链路为坐席麦克风（role=agent），坐席语音本身含案情复述。</p>
 *
 * @author ai-lawyers
 */
final class VoiceCopilotAssistant
{
    private static final Logger log = LoggerFactory.getLogger(VoiceCopilotAssistant.class);

    /** 触发辅助的滚动缓冲最小字符数（短确认话术不触发） */
    private static final int MIN_CHARS = 30;

    /** 辅助触发最小间隔（ms，节流控制 LLM 调用频次） */
    private static final long INTERVAL_MS = 8000L;

    /** 送辅助服务的文本滚动窗口上限（字，防超 token） */
    private static final int TEXT_MAX = 3000;

    /** 宿主回调：下发帧（协作类不反向依赖会话门面） */
    interface Host
    {
        void send(String frame);
    }

    private final CopilotAssistService service;

    private final Host host;

    private final String connId;

    private final String sessionId;

    /** P1-8：坐席用户 ID（用于话术个性化画像，null 时不注入画像） */
    private final Long agentId;

    /** F4：辅助回合单线程执行器（LLM/RAG 调用不阻塞 ASR 回调） */
    private final ExecutorService executor;

    /** F4：辅助回合发号器 */
    private final AtomicLong turnSeq = new AtomicLong();

    /** F4：当前有效回合号（stop/shutdown 递增作废迟到结果） */
    private final AtomicLong activeSeq = new AtomicLong();

    /** F4：final 文本滚动累计缓冲（rolling window） */
    private final StringBuilder buffer = new StringBuilder();

    /** F4：上次辅助触发时间（节流，最小间隔 INTERVAL_MS） */
    private volatile long lastMs;

    VoiceCopilotAssistant(CopilotAssistService service, Host host,
            String connId, String sessionId, Long agentId)
    {
        this.service = service;
        this.host = host;
        this.connId = connId;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "voice-copilot-" + connId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * final 文本滚动入缓冲；缓冲达最小字数且距上次触发 ≥ 节流间隔时提交辅助。
     */
    void queue(String text)
    {
        if (service == null || text == null)
        {
            return;
        }
        String t = text.trim();
        if (t.isEmpty())
        {
            return;
        }
        boolean fire;
        long now = System.currentTimeMillis();
        synchronized (buffer)
        {
            if (buffer.length() > 0)
            {
                buffer.append('\n');
            }
            buffer.append(t);
            fire = buffer.length() >= MIN_CHARS && now - lastMs >= INTERVAL_MS;
        }
        if (fire)
        {
            submit(false);
        }
    }

    /** 作废在途回合（stop：close 内部 final 仍会入缓冲，随后强制补最后一回合） */
    void invalidate()
    {
        activeSeq.incrementAndGet();
    }

    /** stop 前强制补一回合（不受间隔/最小字数限制），让挂断前要素完整 */
    void forceFlush()
    {
        submit(true);
    }

    /** 停机：作废迟到 Copilot 回合并停辅助执行器 */
    void shutdown()
    {
        invalidate();
        executor.shutdownNow();
    }

    /**
     * 提交一个辅助回合（单线程执行，迟到结果按回合号丢弃）。
     *
     * @param force true=stop 前强制（忽略间隔/最小字数）
     */
    private void submit(boolean force)
    {
        if (service == null)
        {
            return;
        }
        final long seq;
        final String payload;
        synchronized (buffer)
        {
            if (buffer.length() == 0)
            {
                return;
            }
            if (!force && buffer.length() < MIN_CHARS)
            {
                return;
            }
            // 滚动窗口：超长按尾部截取，并把窗口保留进下一回合
            String all = buffer.toString();
            payload = all.length() > TEXT_MAX ? all.substring(all.length() - TEXT_MAX) : all;
            buffer.setLength(0);
            buffer.append(payload);
            lastMs = System.currentTimeMillis();
            seq = turnSeq.incrementAndGet();
            activeSeq.set(seq);
        }
        try
        {
            executor.execute(() -> runTurn(seq, payload));
        }
        catch (RejectedExecutionException e)
        {
            log.warn("VoiceWS Copilot 回合提交失败（已关闭）conn={}", connId);
        }
    }

    /** 执行辅助：要素/法条/工单/动作/话术五帧下发；服务自身全兜底，此处仅防御性兜底 */
    private void runTurn(long seq, String payload)
    {
        CopilotAssistResult result;
        try
        {
            result = service.assist(payload, sessionId, agentId);
        }
        catch (Exception e)
        {
            log.warn("VoiceWS Copilot 回合异常 conn={}, seq={}: {}", connId, seq, e.getMessage());
            return;
        }
        // 迟到回合丢弃（stop/shutdown 已作废）
        if (seq != activeSeq.get())
        {
            return;
        }
        host.send(VoiceFrames.copilotElement(seq, result.getDisputeType(),
                result.getClaims(), result.getUrgency(), result.getKeyFacts(),
                result.isElementDegraded()));

        List<Map<String, Object>> laws = new ArrayList<>();
        for (CopilotLaw law : result.getLaws())
        {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("chunkId", law.getChunkId());
            m.put("title", law.getTitle());
            m.put("lawArticle", law.getLawArticle());
            m.put("source", law.getSource());
            laws.add(m);
        }
        host.send(VoiceFrames.copilotLaws(seq, laws));

        List<Map<String, Object>> tickets = new ArrayList<>();
        for (CopilotTicket ticket : result.getTickets())
        {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("ticketId", ticket.getTicketId());
            m.put("ticketNo", ticket.getTicketNo());
            m.put("title", ticket.getTitle());
            m.put("status", ticket.getStatus());
            m.put("contentSnippet", ticket.getContentSnippet());
            tickets.add(m);
        }
        host.send(VoiceFrames.copilotTickets(seq, tickets));

        List<Map<String, Object>> actions = new ArrayList<>();
        for (CopilotAssistResult.SuggestedAction act : result.getActions())
        {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("action", act.getAction());
            m.put("label", act.getLabel());
            m.put("payload", act.getPayload());
            actions.add(m);
        }
        host.send(VoiceFrames.copilotActions(seq, actions));

        // P1-8：对客口播话术（空串表示未生成/已降级，客户端据此隐藏话术区）
        host.send(VoiceFrames.copilotScript(seq, result.getSuggestedScript()));
    }
}
