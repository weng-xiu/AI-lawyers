package ai.lawyers.framework.websocket.voice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult;
import ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService;

/**
 * E3 语音机器人回合执行器（P5 从 {@link VoiceSession} 拆出）。
 *
 * <p>回合串行（单线程执行器，不阻塞 WS/ASR 回调线程）；最新回合取代旧回合——
 * 旧回合结果产出前发现 turnId 已失效即丢弃，保证"新问题就是新回合"，
 * 迟到应答不下发、不播报。应答产出后经宿主回调下发 answer_delta/done 帧
 * 并自动进入 TTS 播报。</p>
 *
 * <p>G6 安全约束：话单ID仅允许由 PBX 媒体 fork 桥接层（服务端）锚定一次，
 * 浏览器侧恒为 null，禁止由前端透传，防伪造越权。</p>
 *
 * @author ai-lawyers
 */
final class VoiceRobotTurnRunner
{
    private static final Logger log = LoggerFactory.getLogger(VoiceRobotTurnRunner.class);

    /** 宿主回调：下发帧与启动 TTS 播报（协作类不反向依赖会话门面） */
    interface Host
    {
        /** 下发一帧控制消息（经会话保序发送队列） */
        void send(String frame);

        /** 启动一次流式 TTS 播报（机器人应答自动播报） */
        void startTts(String text);
    }

    private final VoiceRobotService robotService;

    private final Host host;

    private final String connId;

    private final String sessionId;

    /** A5：指标门面（null 时埋点静默跳过，不抛异常） */
    private final HotlineMetrics metrics;

    /** E3：回合单线程执行器（回合串行，不阻塞 WS/ASR 回调线程） */
    private final ExecutorService executor;

    /** E3：回合号发号器 */
    private final AtomicLong turnSeq = new AtomicLong();

    /** E3：当前有效回合号（新回合/新 final/stop/shutdown 时递增作废旧回合） */
    private final AtomicLong activeTurn = new AtomicLong();

    /** G6：服务端锚定的话单ID（仅 PBX 媒体 fork 桥接时由服务端设置） */
    private volatile Long anchoredRecordId;

    /** E3：本回合问题就绪时刻（ASR final / ask 受理，robot E2E 起点） */
    private volatile long questionNanos;

    /** E3：本回合 E2E 已埋点标记（新回合重置） */
    private final AtomicBoolean e2eMarked = new AtomicBoolean(true);

    VoiceRobotTurnRunner(VoiceRobotService robotService, Host host,
            String connId, String sessionId, HotlineMetrics metrics)
    {
        this.robotService = robotService;
        this.host = host;
        this.connId = connId;
        this.sessionId = sessionId;
        this.metrics = metrics;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "voice-robot-" + connId);
            t.setDaemon(true);
            return t;
        });
    }

    /** 对话引擎是否装配（未装配时 ask 帧回 ROBOT_UNAVAILABLE、robot 模式不自动应答） */
    boolean isAvailable()
    {
        return robotService != null;
    }

    /**
     * 提交一个机器人回合（最新回合取代旧回合——旧回合结果产出前发现 turnId
     * 已失效即丢弃，保证"新问题就是新回合"，迟到应答不下发、不播报）。
     */
    void submitTurn(String question)
    {
        if (robotService == null)
        {
            return;
        }
        final long turnId = turnSeq.incrementAndGet();
        activeTurn.set(turnId);
        final long questionNanos = System.nanoTime();
        try
        {
            executor.execute(() -> runTurn(turnId, question, questionNanos));
        }
        catch (RejectedExecutionException e)
        {
            log.warn("VoiceWS robot 回合提交失败（已关闭）conn={}", connId);
        }
    }

    /** 作废当前回合（新 final/stop：在途 LLM 结果产出时被 turnId 校验丢弃） */
    void invalidateTurn()
    {
        activeTurn.incrementAndGet();
    }

    /**
     * G6：由 PBX 媒体 fork 桥接层锚定本通话的话单ID（服务端可信来源）。
     * 仅允许设置一次，浏览器/坐席链路不调用此方法。
     */
    void anchorRecordId(Long recordId)
    {
        if (recordId != null && this.anchoredRecordId == null)
        {
            this.anchoredRecordId = recordId;
        }
    }

    /** 机器人回合 E2E 埋点（TTS 首个音频分片回调触发，仅记一次） */
    void markE2eIfNeeded()
    {
        if (metrics != null && questionNanos > 0 && e2eMarked.compareAndSet(false, true))
        {
            metrics.recordVoiceE2eFirstMs((System.nanoTime() - questionNanos) / 1_000_000L,
                    "robot", sessionId);
        }
    }

    /** 停机：作废旧回合并停回合执行器（阻塞中的 LLM 调用结果产出时被 turnId 校验丢弃） */
    void shutdown()
    {
        invalidateTurn();
        executor.shutdownNow();
    }

    /** 执行机器人回合：RAG+LLM → answer_delta/done 帧 → 自动 TTS 播报 */
    private void runTurn(long turnId, String question, long questionNanos)
    {
        VoiceRobotResult result;
        try
        {
            result = robotService.answer(question, sessionId, anchoredRecordId);
        }
        catch (Exception e)
        {
            // 服务自身已兜底，这里仅防御性兜底
            log.warn("VoiceWS robot 回合异常 conn={}, turn={}: {}", connId, turnId, e.getMessage());
            return;
        }
        // 迟到回合丢弃（新问题/打断/停机已取代；停机时 activeTurn 已递增）
        if (turnId != activeTurn.get())
        {
            return;
        }
        host.send(VoiceFrames.answerDelta(turnId, 0, result.getAnswer()));
        host.send(VoiceFrames.answerDone(turnId, result.getRagHits(),
                result.getSources(), result.isDegraded()));
        // 自动播报：机器人 E2E 起点=问题就绪时刻
        this.questionNanos = questionNanos;
        e2eMarked.set(false);
        host.startTts(result.getAnswer());
    }
}
