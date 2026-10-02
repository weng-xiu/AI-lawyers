package ai.lawyers.framework.websocket.voice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.voice.vad.SileroVadShadowService;
import ai.lawyers.system.service.lawyers.voice.vad.VoiceActivityDetector;

/**
 * 单条 /ws/voice 连接的语音会话编排（P3-A1）。
 *
 * <p>持有：ASR 引擎实例、当前 TTS 合成句柄、<b>单线程保序发送队列</b>
 * （所有下发帧入队由唯一 sender 线程经 {@code getBasicRemote()} 阻塞写出，
 * 符合 JSR-356 "同一 RemoteEndpoint 禁止多线程并发发送"约束）。</p>
 *
 * <p>barge-in：取消当前合成 + 清空队列中尚未发出的 tts 分片 + 下发一帧
 * interrupted tts_end 供客户端复位播放状态。{@link #shutdown()} 幂等，
 * 供 {@code @OnClose} 释放全部引擎句柄与线程。</p>
 *
 * @author ai-lawyers
 */
public class VoiceSession
{
    private static final Logger log = LoggerFactory.getLogger(VoiceSession.class);

    /** TTS 文本上限（防超长合成拖垮 Mock/真实引擎） */
    static final int TTS_TEXT_MAX = 2000;

    /** 发送队列满时入队等待上限（ms） */
    private static final long ENQUEUE_TIMEOUT_MS = 200L;

    /** F4：Copilot 辅助触发最小间隔（ms，节流控制 LLM 调用频次） */
    static final long COPILOT_INTERVAL_MS = 8000L;

    /** F4：触发辅助的滚动缓冲最小字符数（短确认话术不触发） */
    static final int COPILOT_MIN_CHARS = 30;

    /** F4：送辅助服务的文本滚动窗口上限（字，防超 token） */
    static final int COPILOT_TEXT_MAX = 3000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Session wsSession;

    private final String connId;

    private final String sessionId;

    private final String role;

    private final LinkedBlockingQueue<Entry> sendQueue;

    private final Thread sender;

    private final AtomicBoolean shutdown = new AtomicBoolean();

    private final AtomicLong asrSeq = new AtomicLong();

    private final AtomicLong droppedFrames = new AtomicLong();

    private volatile boolean asrActive;

    private volatile String engine = MockStreamAsrEngine.ENGINE_CODE;

    private volatile int sampleRate = 16000;

    private volatile StreamAsrEngine asrEngine;

    private volatile StreamSynthesis currentTts;

    /** A2/A3：引擎注册表（null 时仅 mock 可用，保持 A1 行为） */
    private final VoiceEngineRegistry engineRegistry;

    /** A4：VAD 开关（voice.vad-enabled），关闭时不创建检测器、不做自动打断 */
    private final boolean vadEnabled;

    /** A4：能量+过零率 VAD，start 帧后按 sampleRate 创建，stop/shutdown 释放 */
    private volatile VoiceActivityDetector vad;

    /** P2-12：Silero VAD 影子服务（null 时影子模式关闭） */
    private final SileroVadShadowService vadShadowService;

    /** P2-12：影子 VAD 对（start 帧后创建，stop/shutdown 释放） */
    private volatile SileroVadShadowService.VadPair vadShadowPair;

    /** A5：指标门面（null 时埋点静默跳过，不抛异常） */
    private final HotlineMetrics metrics;

    /* ---- E3：语音机器人（RAG+LLM 对话引擎） ---- */
    /** E3：对话引擎服务（null 时 ask/robot 回 ROBOT_UNAVAILABLE） */
    private final ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robotService;
    /** E3：start 帧 "robot":true 开启 ASR final 自动应答 */
    private volatile boolean robotMode;
    /** E3：机器人回合单线程执行器（回合串行，不阻塞 WS/ASR 回调线程） */
    private final java.util.concurrent.ExecutorService robotExecutor;
    /** E3：回合号发号器 */
    private final java.util.concurrent.atomic.AtomicLong robotTurnSeq = new java.util.concurrent.atomic.AtomicLong();
    /** E3：当前有效回合号（新回合/新 final/stop/shutdown 时递增作废旧回合） */
    private final java.util.concurrent.atomic.AtomicLong activeRobotTurn = new java.util.concurrent.atomic.AtomicLong();
    /** E3：本回合问题就绪时刻（ASR final / ask 受理，robot E2E 起点） */
    private volatile long robotQuestionNanos;
    /** E3：本回合 E2E 已埋点标记（新回合重置） */
    private final AtomicBoolean robotE2eMarked = new AtomicBoolean(true);

    /* ---- E4：情绪/意图实时识别与联动 ---- */
    /** E4：联动动作服务（null 时不执行预警/提优，识别不挂钩） */
    private final ai.lawyers.system.service.lawyers.voice.emotion.VoiceRiskActionService emotionAction;
    /** E4：纯词库识别器（无状态、微秒级） */
    private final ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentDetector emotionDetector =
            new ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentDetector();
    /** E4：本会话 urgent 已动作（每通话只发一次高级预警） */
    private final AtomicBoolean urgentEmotionActed = new AtomicBoolean();
    /** E4：本会话 negative 已动作（urgent 命中后不再发 negative） */
    private final AtomicBoolean negativeEmotionActed = new AtomicBoolean();

    /* ---- F4：坐席 Copilot 实时辅助（要素/法条/相似工单） ---- */
    /** F4：Copilot 辅助服务（null 时 final 文本不触发辅助） */
    private final ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistService copilotService;
    /** F4：辅助回合单线程执行器（LLM/RAG 调用不阻塞 ASR 回调） */
    private final java.util.concurrent.ExecutorService copilotExecutor;
    /** F4：辅助回合肥号器 */
    private final java.util.concurrent.atomic.AtomicLong copilotTurnSeq = new java.util.concurrent.atomic.AtomicLong();
    /** F4：当前有效回合号（stop/shutdown 递增作废迟到结果） */
    private final java.util.concurrent.atomic.AtomicLong activeCopilotSeq = new java.util.concurrent.atomic.AtomicLong();
    /** F4：final 文本滚动累计缓冲（rolling window） */
    private final StringBuilder copilotBuffer = new StringBuilder();
    /** F4：上次辅助触发时间（节流，最小间隔 COPILOT_INTERVAL_MS） */
    private volatile long lastCopilotMs;

    /* ---- A5 时间戳/一次性标记（纳秒，System.nanoTime） ---- */
    /** start 帧受理时刻（ASR 首包/E2E 首响起点） */
    private volatile long startNanos;
    /** 首个 asr_partial 已上报标记 */
    private final AtomicBoolean asrFirstMarked = new AtomicBoolean();
    /** 最近一次 tts 帧受理时刻 */
    private volatile long ttsStartNanos;
    /** 当前 tts 首包已上报标记（新 tts 帧重置） */
    private final AtomicBoolean ttsFirstMarked = new AtomicBoolean();
    /** start 后首包 TTS 音频已上报标记（E2E 只记一次） */
    private final AtomicBoolean e2eFirstMarked = new AtomicBoolean();
    /** 本次 VAD 起始判定起点（首个有声帧进入时刻） */
    private volatile long vadSpeechBeginNanos;

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity)
    {
        this(wsSession, sessionId, role, queueCapacity, null);
    }

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity,
            VoiceEngineRegistry engineRegistry)
    {
        this(wsSession, sessionId, role, queueCapacity, engineRegistry, true);
    }

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity,
            VoiceEngineRegistry engineRegistry, boolean vadEnabled)
    {
        this(wsSession, sessionId, role, queueCapacity, engineRegistry, vadEnabled, null);
    }

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity,
            VoiceEngineRegistry engineRegistry, boolean vadEnabled, HotlineMetrics metrics)
    {
        this(wsSession, sessionId, role, queueCapacity, engineRegistry, vadEnabled, metrics, null);
    }

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity,
            VoiceEngineRegistry engineRegistry, boolean vadEnabled, HotlineMetrics metrics,
            ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robotService)
    {
        this(wsSession, sessionId, role, queueCapacity, engineRegistry, vadEnabled, metrics,
                robotService, null);
    }

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity,
            VoiceEngineRegistry engineRegistry, boolean vadEnabled, HotlineMetrics metrics,
            ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robotService,
            ai.lawyers.system.service.lawyers.voice.emotion.VoiceRiskActionService emotionAction)
    {
        this(wsSession, sessionId, role, queueCapacity, engineRegistry, vadEnabled, metrics,
                robotService, emotionAction, null);
    }

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity,
            VoiceEngineRegistry engineRegistry, boolean vadEnabled, HotlineMetrics metrics,
            ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robotService,
            ai.lawyers.system.service.lawyers.voice.emotion.VoiceRiskActionService emotionAction,
            ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistService copilotService)
    {
        this(wsSession, sessionId, role, queueCapacity, engineRegistry, vadEnabled, metrics,
                robotService, emotionAction, copilotService, null);
    }

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity,
            VoiceEngineRegistry engineRegistry, boolean vadEnabled, HotlineMetrics metrics,
            ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robotService,
            ai.lawyers.system.service.lawyers.voice.emotion.VoiceRiskActionService emotionAction,
            ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistService copilotService,
            SileroVadShadowService vadShadowService)
    {
        this.wsSession = wsSession;
        this.connId = wsSession.getId();
        this.sessionId = sessionId;
        this.role = role;
        this.engineRegistry = engineRegistry != null ? engineRegistry : new VoiceEngineRegistry();
        this.vadEnabled = vadEnabled;
        this.metrics = metrics;
        this.robotService = robotService;
        this.emotionAction = emotionAction;
        this.copilotService = copilotService;
        this.vadShadowService = vadShadowService;
        int cap = queueCapacity > 0 ? queueCapacity : 1000;
        this.sendQueue = new LinkedBlockingQueue<>(cap);
        this.sender = new Thread(this::runSender, "voice-sender-" + connId);
        this.sender.setDaemon(true);
        this.sender.start();
        this.robotExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "voice-robot-" + connId);
            t.setDaemon(true);
            return t;
        });
        this.copilotExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "voice-copilot-" + connId);
            t.setDaemon(true);
            return t;
        });
    }

    /* ================= 生命周期 ================= */

    void sendConnected()
    {
        enqueue(Entry.Kind.OTHER, VoiceFrames.connected(sessionId, role));
    }

    /**
     * 处理一帧文本控制消息（start/audio/tts/bargein/stop）。
     * 任何异常不外抛到容器，统一回 error 帧。
     */
    public void handleText(String raw)
    {
        if (shutdown.get())
        {
            return;
        }
        JsonNode node;
        try
        {
            node = MAPPER.readTree(raw);
        }
        catch (Exception e)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("BAD_FRAME", "帧不是合法 JSON"));
            return;
        }
        String type = node.path("type").asText("");
        try
        {
            switch (type)
            {
                case "start":
                    handleStart(node);
                    break;
                case "audio":
                    handleAudio(node);
                    break;
                case "tts":
                    handleTts(node);
                    break;
                case "ask":
                    handleAsk(node);
                    break;
                case "bargein":
                    handleBargein();
                    break;
                case "stop":
                    handleStop();
                    break;
                default:
                    enqueue(Entry.Kind.OTHER,
                            VoiceFrames.error("UNKNOWN_TYPE", "未知帧类型: " + type));
            }
        }
        catch (Exception e)
        {
            log.warn("VoiceWS 帧处理异常 conn={}, type={}: {}", connId, type, e.getMessage());
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("FRAME_ERROR", e.getMessage()));
        }
    }

    /** 二进制音频帧直喂（JSON base64 之外的高效通道，同为 S16LE PCM） */
    public void handleBinary(byte[] pcm)
    {
        if (shutdown.get() || pcm == null || pcm.length == 0)
        {
            return;
        }
        if (!asrActive || asrEngine == null)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("NOT_STARTED", "请先发送 start 帧再传音频"));
            return;
        }
        feedVad(pcm);
        asrEngine.feed(pcm);
    }

    /** @OnClose 释放：停 ASR（排空 final）→ 取消 TTS → 毒丸终止 sender。幂等。 */
    public void shutdown()
    {
        if (!shutdown.compareAndSet(false, true))
        {
            return;
        }
        StreamAsrEngine engineRef = asrEngine;
        if (engineRef != null)
        {
            try
            {
                // close 内部等待 final 任务入队后再关工作池，final 已在 sender 队列中保序
                engineRef.close();
            }
            catch (Exception e)
            {
                log.warn("VoiceWS ASR close 异常 conn={}: {}", connId, e.getMessage());
            }
        }
        asrActive = false;
        vad = null;
        // P2-12：释放影子 VAD 对
        SileroVadShadowService.VadPair pair = vadShadowPair;
        if (pair != null)
        {
            try
            {
                pair.close();
            }
            catch (Exception e)
            {
                log.warn("VoiceWS Silero VAD 影子关闭异常 conn={}: {}", connId, e.getMessage());
            }
            vadShadowPair = null;
        }
        // E3：作废旧回合并停回合执行器（阻塞中的 LLM 调用结果产出时被 turnId 校验丢弃）
        activeRobotTurn.incrementAndGet();
        robotExecutor.shutdownNow();
        // F4：作废迟到 Copilot 回合并停辅助执行器
        activeCopilotSeq.incrementAndGet();
        copilotExecutor.shutdownNow();
        StreamSynthesis tts = currentTts;
        if (tts != null)
        {
            tts.cancel();
        }
        sendQueue.offer(Entry.POISON);
        long dropped = droppedFrames.get();
        log.info("VoiceWS session shutdown conn={}, sessionId={}, droppedFrames={}", connId, sessionId, dropped);
    }

    /* ================= start ================= */

    private void handleStart(JsonNode node)
    {
        if (asrActive)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("ALREADY_STARTED", "识别流已开启，请先 stop"));
            return;
        }
        String reqEngine = node.path("engine").asText(MockStreamAsrEngine.ENGINE_CODE);
        String format = node.path("format").asText("pcm");
        int rate = node.path("sampleRate").asInt(16000);
        if (!"pcm".equalsIgnoreCase(format))
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("BAD_FORMAT", "A1 仅支持 PCM(S16LE 单声道) 音频格式: " + format));
            return;
        }
        if (rate != 8000 && rate != 16000)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("BAD_SAMPLE_RATE", "采样率仅支持 8000/16000: " + rate));
            return;
        }
        StreamAsrEngine engineInstance = engineRegistry.createAsr(reqEngine);
        if (engineInstance == null)
        {
            // 显式拒绝，禁止静默降级成 Mock 造成"假成功"（L4 教训）
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("ENGINE_UNAVAILABLE",
                    "流式引擎 " + reqEngine + " 不可用（未注册或凭证缺失），当前支持 mock/dashscope"));
            return;
        }
        this.engine = engineInstance.engineCode();
        this.sampleRate = rate;
        // E3：start 帧可声明 robot:true 开启 ASR final 自动应答（服务未装配时仅记日志，ask 仍会明确报错）
        this.robotMode = node.path("robot").asBoolean(false);
        if (this.robotMode && this.robotService == null)
        {
            log.warn("VoiceWS robot 模式已请求但对话引擎未装配 sessionId={}，ASR final 不会触发应答", sessionId);
        }
        // A4：start 时按采样率创建 VAD（能量+过零率），语音起始 ≤80ms、尾静默 400ms 判结束
        this.vad = vadEnabled ? new VoiceActivityDetector(rate, new VoiceActivityDetector.Listener()
        {
            @Override
            public void onSpeechStart()
            {
                onVadSpeechStart();
            }

            @Override
            public void onSpeechEnd()
            {
                onVadSpeechEnd();
            }
        }) : null;
        // P2-12：Silero VAD 影子模式（主决策仍走旧 VAD，Silero 仅记录日志打标）
        if (vadShadowService != null && vadEnabled)
        {
            this.vadShadowPair = vadShadowService.createShadowPair(sessionId, rate,
                    new VoiceActivityDetector.Listener()
                    {
                        @Override
                        public void onSpeechStart()
                        {
                            onVadSpeechStart();
                        }

                        @Override
                        public void onSpeechEnd()
                        {
                            onVadSpeechEnd();
                        }
                    });
            // 影子模式下主 VAD 由 VadPair 内部持有，外部统一走影子链路
            this.vad = this.vadShadowPair.getLegacy();
        }
        // A5：记录 start 受理时刻（ASR 首包/E2E 首响起点）
        this.startNanos = System.nanoTime();
        this.asrFirstMarked.set(false);
        this.ttsFirstMarked.set(false);
        this.e2eFirstMarked.set(false);
        VoiceAsrContext ctx = new VoiceAsrContext(sessionId, role, "pcm", rate);
        engineInstance.open(ctx, new AsrStreamCallback()
        {
            @Override
            public void onPartial(String text)
            {
                if (!shutdown.get())
                {
                    // A5：首个 asr_partial → 记录 ASR 流式首包耗时
                    if (metrics != null && asrFirstMarked.compareAndSet(false, true))
                    {
                        metrics.recordVoiceAsrFirstMs(nanosToMs(System.nanoTime() - startNanos), sessionId);
                    }
                    enqueue(Entry.Kind.OTHER, VoiceFrames.asr(VoiceFrames.T_ASR_PARTIAL, asrSeq.incrementAndGet(), text));
                    // E4：partial 仅做 urgent 早预警（negative 等 final，防部分转写噪声误报）
                    detectEmotion(text, false);
                }
            }

            @Override
            public void onFinal(String text)
            {
                if (!shutdown.get())
                {
                    enqueue(Entry.Kind.OTHER, VoiceFrames.asr(VoiceFrames.T_ASR_FINAL, asrSeq.incrementAndGet(), text));
                    // E4：final 完整识别（urgent/negative）
                    detectEmotion(text, true);
                    // E3：robot 模式下 ASR final 自动触发对话引擎回合（空文本不触发）
                    if (robotMode && text != null && !text.trim().isEmpty())
                    {
                        submitRobotTurn(text.trim());
                    }
                    // F4：final 文本滚动入缓冲，满足节流窗口时触发 Copilot 辅助（服务坐席，不区分角色）
                    queueCopilot(text);
                }
            }

            @Override
            public void onError(String code, String message)
            {
                enqueue(Entry.Kind.OTHER, VoiceFrames.error(code, message));
            }
        });
        this.asrEngine = engineInstance;
        this.asrActive = true;
        enqueue(Entry.Kind.OTHER, VoiceFrames.started(sessionId, role, this.engine, "pcm", rate));
    }

    /* ================= audio ================= */

    private void handleAudio(JsonNode node)
    {
        if (!asrActive || asrEngine == null)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("NOT_STARTED", "请先发送 start 帧再传音频"));
            return;
        }
        String b64 = node.path("data").asText("");
        if (b64.isEmpty())
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("BAD_AUDIO", "audio 帧缺少 data(base64 PCM)"));
            return;
        }
        final byte[] pcm;
        try
        {
            pcm = Base64.getDecoder().decode(b64);
        }
        catch (IllegalArgumentException e)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("BAD_AUDIO", "data 不是合法 base64"));
            return;
        }
        if (pcm.length == 0)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("BAD_AUDIO", "音频帧为空"));
            return;
        }
        feedVad(pcm);
        asrEngine.feed(pcm);
    }

    /* ================= tts ================= */

    private void handleTts(JsonNode node)
    {
        String text = node.path("text").asText("");
        String voice = node.path("voice").asText(null);
        if (text.trim().isEmpty())
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("INVALID_PARAM", "tts 帧缺少 text"));
            return;
        }
        if (text.length() > TTS_TEXT_MAX)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("TEXT_TOO_LONG",
                    "tts 文本上限 " + TTS_TEXT_MAX + " 字符，当前 " + text.length()));
            return;
        }
        startTts(text, voice);
    }

    /**
     * 启动一次流式合成（客户端 tts 帧与 E3 机器人应答共用）。
     * 新合成请求覆盖旧的：旧句柄静默取消（新任务自带 tts_end，客户端按 seq 重新计数）。
     */
    private void startTts(String text, String voice)
    {
        StreamSynthesis prev = currentTts;
        if (prev != null)
        {
            prev.cancel();
            drainPendingTts();
        }
        final int ttsRate = this.sampleRate;
        // A5：TTS 首包计时起点（新请求重置，覆盖旧任务）
        this.ttsStartNanos = System.nanoTime();
        this.ttsFirstMarked.set(false);
        // A2/A3：TTS 引擎跟随 start 帧选定的引擎（mock/dashscope）
        StreamTtsEngine ttsEngine = engineRegistry.createTts(this.engine);
        if (ttsEngine == null)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("ENGINE_UNAVAILABLE",
                    "流式 TTS 引擎 " + this.engine + " 不可用（未注册或凭证缺失）"));
            return;
        }
        StreamSynthesis handle = ttsEngine.synthesizeStream(text, voice, ttsRate,
                new TtsStreamCallback()
                {
                    @Override
                    public void onAudio(int seq, int chunkCount, byte[] pcm)
                    {
                        StreamSynthesis self = currentTts;
                        if (self != null && self.isCancelled())
                        {
                            return;
                        }
                        // A5：TTS 首包 + E2E 首响埋点（各自仅首分片记一次）
                        if (metrics != null && ttsFirstMarked.compareAndSet(false, true))
                        {
                            metrics.recordVoiceTtsFirstMs(nanosToMs(System.nanoTime() - ttsStartNanos), sessionId);
                        }
                        if (metrics != null && e2eFirstMarked.compareAndSet(false, true))
                        {
                            metrics.recordVoiceE2eFirstMs(nanosToMs(System.nanoTime() - startNanos), sessionId);
                        }
                        // E3：机器人回合 E2E（问题就绪→应答首个音频分片）
                        if (metrics != null && robotQuestionNanos > 0 && robotE2eMarked.compareAndSet(false, true))
                        {
                            metrics.recordVoiceE2eFirstMs(nanosToMs(System.nanoTime() - robotQuestionNanos),
                                    "robot", sessionId);
                        }
                        enqueue(Entry.Kind.TTS, VoiceFrames.ttsAudio(seq, chunkCount, ttsRate, pcm));
                    }

                    @Override
                    public void onEnd()
                    {
                        StreamSynthesis self = currentTts;
                        if (self != null && self.isCancelled())
                        {
                            return;
                        }
                        enqueue(Entry.Kind.OTHER, VoiceFrames.ttsEnd(false, null));
                    }

                    @Override
                    public void onError(String code, String message)
                    {
                        enqueue(Entry.Kind.OTHER, VoiceFrames.error(code, message));
                    }
                });
        this.currentTts = handle;
    }

    /* ================= E3：语音机器人（ask 帧 / robot 自动应答） ================= */

    /** E3：ask 帧显式提问（text 必填；robot 模式外也可用于调试/坐席侧主动提问） */
    private void handleAsk(JsonNode node)
    {
        String question = node.path("text").asText("").trim();
        if (question.isEmpty())
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("INVALID_PARAM", "ask 帧缺少 text"));
            return;
        }
        if (question.length() > 500)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("TEXT_TOO_LONG",
                    "ask 问题上限 500 字符，当前 " + question.length()));
            return;
        }
        if (robotService == null)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("ROBOT_UNAVAILABLE", "对话引擎未装配"));
            return;
        }
        submitRobotTurn(question);
    }

    /**
     * E3：提交一个机器人回合（最新回合取代旧回合——旧回合结果产出前发现 turnId
     * 已失效即丢弃，保证"新问题就是新回合"，迟到应答不下发、不播报）。
     */
    private void submitRobotTurn(String question)
    {
        if (shutdown.get() || robotService == null)
        {
            return;
        }
        final long turnId = robotTurnSeq.incrementAndGet();
        activeRobotTurn.set(turnId);
        final long questionNanos = System.nanoTime();
        try
        {
            robotExecutor.execute(() -> runRobotTurn(turnId, question, questionNanos));
        }
        catch (java.util.concurrent.RejectedExecutionException e)
        {
            log.warn("VoiceWS robot 回合提交失败（已关闭）conn={}", connId);
        }
    }

    /** E3：执行机器人回合：RAG+LLM → answer_delta/done 帧 → 自动 TTS 播报 */
    private void runRobotTurn(long turnId, String question, long questionNanos)
    {
        ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult result;
        try
        {
            result = robotService.answer(question, sessionId);
        }
        catch (Exception e)
        {
            // 服务自身已兜底，这里仅防御性兜底
            log.warn("VoiceWS robot 回合异常 conn={}, turn={}: {}", connId, turnId, e.getMessage());
            return;
        }
        // 迟到回合丢弃（新问题/打断/停机已取代）
        if (shutdown.get() || turnId != activeRobotTurn.get())
        {
            return;
        }
        enqueue(Entry.Kind.OTHER, VoiceFrames.answerDelta(turnId, 0, result.getAnswer()));
        enqueue(Entry.Kind.OTHER, VoiceFrames.answerDone(turnId, result.getRagHits(),
                result.getSources(), result.isDegraded()));
        // 自动播报：机器人 E2E 起点=问题就绪时刻
        this.robotQuestionNanos = questionNanos;
        this.robotE2eMarked.set(false);
        startTts(result.getAnswer(), null);
    }

    /* ================= F4：Copilot 实时辅助 ================= */

    /**
     * final 文本滚动入缓冲；缓冲达最小字数且距上次触发 ≥ 节流间隔时提交辅助。
     *
     * <p>与 E4 不同，Copilot 服务对象是坐席，不做 caller 门禁——当前生产链路
     * 为坐席麦克风（role=agent），坐席语音本身含案情复述。</p>
     */
    private void queueCopilot(String text)
    {
        if (copilotService == null || text == null)
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
        synchronized (copilotBuffer)
        {
            if (copilotBuffer.length() > 0)
            {
                copilotBuffer.append('\n');
            }
            copilotBuffer.append(t);
            fire = copilotBuffer.length() >= COPILOT_MIN_CHARS
                    && now - lastCopilotMs >= COPILOT_INTERVAL_MS;
        }
        if (fire)
        {
            submitCopilot(false);
        }
    }

    /**
     * 提交一个 Copilot 辅助回合（单线程执行，迟到结果按 seq 丢弃）。
     *
     * @param force true=stop 前强制（忽略间隔/最小字数）
     */
    private void submitCopilot(boolean force)
    {
        if (shutdown.get() || copilotService == null)
        {
            return;
        }
        final long seq;
        final String payload;
        synchronized (copilotBuffer)
        {
            if (copilotBuffer.length() == 0)
            {
                return;
            }
            if (!force && copilotBuffer.length() < COPILOT_MIN_CHARS)
            {
                return;
            }
            // 滚动窗口：超长按尾部截取，并把窗口保留进下一回合
            String all = copilotBuffer.toString();
            payload = all.length() > COPILOT_TEXT_MAX
                    ? all.substring(all.length() - COPILOT_TEXT_MAX) : all;
            copilotBuffer.setLength(0);
            copilotBuffer.append(payload);
            lastCopilotMs = System.currentTimeMillis();
            seq = copilotTurnSeq.incrementAndGet();
            activeCopilotSeq.set(seq);
        }
        try
        {
            copilotExecutor.execute(() -> runCopilotTurn(seq, payload));
        }
        catch (java.util.concurrent.RejectedExecutionException e)
        {
            log.warn("VoiceWS Copilot 回合提交失败（已关闭）conn={}", connId);
        }
    }

    /** 执行辅助：要素/法条/工单三帧下发；服务自身全兜底，此处仅防御性兜底 */
    private void runCopilotTurn(long seq, String payload)
    {
        ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistResult result;
        try
        {
            result = copilotService.assist(payload, sessionId);
        }
        catch (Exception e)
        {
            log.warn("VoiceWS Copilot 回合异常 conn={}, seq={}: {}", connId, seq, e.getMessage());
            return;
        }
        // 迟到回合丢弃（stop/shutdown 已作废）
        if (shutdown.get() || seq != activeCopilotSeq.get())
        {
            return;
        }
        enqueue(Entry.Kind.OTHER, VoiceFrames.copilotElement(seq, result.getDisputeType(),
                result.getClaims(), result.getUrgency(), result.getKeyFacts(),
                result.isElementDegraded()));

        List<java.util.Map<String, Object>> laws = new ArrayList<>();
        for (ai.lawyers.system.service.lawyers.voice.copilot.CopilotLaw law : result.getLaws())
        {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("chunkId", law.getChunkId());
            m.put("title", law.getTitle());
            m.put("lawArticle", law.getLawArticle());
            m.put("source", law.getSource());
            laws.add(m);
        }
        enqueue(Entry.Kind.OTHER, VoiceFrames.copilotLaws(seq, laws));

        List<java.util.Map<String, Object>> tickets = new ArrayList<>();
        for (ai.lawyers.system.service.lawyers.voice.copilot.CopilotTicket ticket : result.getTickets())
        {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("ticketId", ticket.getTicketId());
            m.put("ticketNo", ticket.getTicketNo());
            m.put("title", ticket.getTitle());
            m.put("status", ticket.getStatus());
            m.put("contentSnippet", ticket.getContentSnippet());
            tickets.add(m);
        }
        enqueue(Entry.Kind.OTHER, VoiceFrames.copilotTickets(seq, tickets));
    }

    /* ================= E4：情绪/意图识别联动 ================= */

    /**
     * 对一段 ASR 文本执行情绪/意图联动。
     *
     * <p>门禁：仅"说话人=来电者"的通道（role=caller 或 robot 模式）挂钩——
     * 坐席自身语音不作为风险来源。去重：urgent 每通话动作一次；negative
     * 每通话一次且 urgent 已命中后不再动作；urgent 可覆盖在先的 negative
     * （紧急高级预警优先，宁错报不遗漏）。</p>
     *
     * @param text    ASR 文本
     * @param isFinal true=final（urgent/negative 均可）；false=partial（仅 urgent）
     */
    private void detectEmotion(String text, boolean isFinal)
    {
        if (emotionAction == null || text == null || text.trim().isEmpty())
        {
            return;
        }
        if (!emotionChannel())
        {
            return;
        }
        ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentResult r = emotionDetector.analyze(text);
        if (r.isUrgent())
        {
            if (!urgentEmotionActed.compareAndSet(false, true))
            {
                return;
            }
            fireEmotion(r, text);
        }
        else if (isFinal && r.isNegative())
        {
            if (urgentEmotionActed.get() || !negativeEmotionActed.compareAndSet(false, true))
            {
                return;
            }
            fireEmotion(r, text);
        }
    }

    /** 来电者通道判定：caller 角色或 robot 模式（robot ASR 即来电者语音） */
    private boolean emotionChannel()
    {
        return "caller".equalsIgnoreCase(role) || robotMode;
    }

    /** 执行联动动作（预警+提优+计数）并下发 emotion 帧；动作服务内部全兜底 */
    private void fireEmotion(ai.lawyers.system.service.lawyers.voice.emotion.EmotionIntentResult r, String hitText)
    {
        Long warningId = emotionAction.handleEmotion(r.getEmotion(), r.getIntent(),
                hitText, r.getEmotionKeywords(), sessionId);
        enqueue(Entry.Kind.OTHER,
                VoiceFrames.emotion(r.getEmotion(), r.getIntent(), r.getEmotionKeywords(), warningId));
    }

    /* ================= bargein / stop ================= */

    /** A4：音频帧喂 VAD（独立于 ASR 引擎，Mock/真实引擎下均生效） */
    private void feedVad(byte[] pcm)
    {
        // P2-12：影子模式开启时，VadPair 内部已包含主 VAD，直接走影子链路避免重复 feed
        SileroVadShadowService.VadPair pair = vadShadowPair;
        if (pair != null)
        {
            try
            {
                pair.feed(pcm);
            }
            catch (Exception e)
            {
                log.warn("VoiceWS Silero VAD 影子处理异常 conn={}: {}", connId, e.getMessage());
            }
            return;
        }
        VoiceActivityDetector detector = vad;
        if (detector != null)
        {
            try
            {
                detector.feed(pcm);
            }
            catch (Exception e)
            {
                log.warn("VoiceWS VAD 处理异常 conn={}: {}", connId, e.getMessage());
            }
        }
    }

    /**
     * A4：VAD 语音起始——下发 vad_speech_start 帧（客户端立即清播放队列抢 ≤300ms），
     * 播报期间自动 barge-in：取消合成 + 丢弃队列中未发分片 + interrupted tts_end。
     * 已合成未播音频随取消丢弃，不落录音（4.1 节约束）。
     */
    private void onVadSpeechStart()
    {
        if (shutdown.get())
        {
            return;
        }
        // A5：VAD 起始判定耗时（首个有声帧进入 → 判定触发）
        VoiceActivityDetector detector = vad;
        if (metrics != null && detector != null)
        {
            metrics.recordVoiceVadMs(detector.getLastDetectionMs(), sessionId);
        }
        enqueue(Entry.Kind.OTHER, VoiceFrames.vad(VoiceFrames.T_VAD_SPEECH_START));
        StreamSynthesis tts = currentTts;
        if (tts != null && !tts.isCancelled())
        {
            long bargeinBegin = System.nanoTime();
            tts.cancel();
            drainPendingTts();
            enqueue(Entry.Kind.OTHER, VoiceFrames.ttsEnd(true, "vad_speech_start"));
            // A5：VAD 触发的打断停止耗时
            if (metrics != null)
            {
                metrics.recordVoiceBargeinStopMs(nanosToMs(System.nanoTime() - bargeinBegin), "vad", sessionId);
            }
        }
    }

    /** A4：VAD 语音结束——仅通知客户端（UI 状态/可驱动后续话术时机） */
    private void onVadSpeechEnd()
    {
        if (!shutdown.get())
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.vad(VoiceFrames.T_VAD_SPEECH_END));
        }
    }

    private void handleBargein()
    {
        long bargeinBegin = System.nanoTime();
        StreamSynthesis tts = currentTts;
        if (tts != null)
        {
            tts.cancel();
        }
        drainPendingTts();
        // 明确发一帧 interrupted tts_end，客户端复位 AudioContext 播放队列
        enqueue(Entry.Kind.OTHER, VoiceFrames.ttsEnd(true, "bargein"));
        // A5：客户端主动打断停止耗时
        if (metrics != null)
        {
            metrics.recordVoiceBargeinStopMs(nanosToMs(System.nanoTime() - bargeinBegin), "bargein", sessionId);
        }
    }

    private void handleStop()
    {
        StreamAsrEngine engineRef = asrEngine;
        if (!asrActive || engineRef == null)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("NOT_STARTED", "无进行中的识别流"));
            return;
        }
        asrActive = false;
        asrEngine = null;
        vad = null;
        // P2-12：停止识别时释放影子 VAD 对
        SileroVadShadowService.VadPair stopPair = vadShadowPair;
        if (stopPair != null)
        {
            try
            {
                stopPair.close();
            }
            catch (Exception e)
            {
                log.warn("VoiceWS Silero VAD 影子 stop 释放异常 conn={}: {}", connId, e.getMessage());
            }
            vadShadowPair = null;
        }
        robotMode = false;
        // E3：停止识别即作废机器人回合（在途 LLM 结果产出时丢弃）
        activeRobotTurn.incrementAndGet();
        // F4：作废在途 Copilot 回合（close 内部 final 仍会入缓冲，随后强制补最后一回合）
        activeCopilotSeq.incrementAndGet();
        try
        {
            engineRef.close();
        }
        catch (Exception e)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("ASR_CLOSE_ERROR", e.getMessage()));
        }
        // F4：停止前以累计缓冲强制辅助一次（不受间隔/最小字数限制），让挂断前要素完整
        submitCopilot(true);
    }

    /** A5：纳秒差转毫秒（System.nanoTime 差值 → ms，截断取整） */
    private static long nanosToMs(long nanos)
    {
        return nanos / 1_000_000L;
    }

    /* ================= 发送队列 ================= */

    private void drainPendingTts()
    {
        List<Entry> kept = new ArrayList<>();
        sendQueue.drainTo(kept);
        for (Entry e : kept)
        {
            if (e.kind != Entry.Kind.TTS)
            {
                sendQueue.offer(e);
            }
        }
    }

    private void enqueue(Entry.Kind kind, String payload)
    {
        if (shutdown.get() && kind != Entry.Kind.POISON)
        {
            return;
        }
        try
        {
            if (sendQueue.offer(new Entry(kind, payload), ENQUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            {
                return;
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        long n = droppedFrames.incrementAndGet();
        if (n == 1 || n % 100 == 0)
        {
            log.warn("VoiceWS 发送队列满，丢帧 conn={}, 累计丢弃={}", connId, n);
        }
    }

    private void runSender()
    {
        while (true)
        {
            try
            {
                Entry e = sendQueue.take();
                if (e == Entry.POISON)
                {
                    return;
                }
                if (wsSession.isOpen())
                {
                    // 单线程 + BasicRemote：天然保序且线程安全
                    wsSession.getBasicRemote().sendText(e.payload);
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (IOException e)
            {
                // 对端断开期间 @OnClose 会随后触发并 shutdown，这里不重复关闭
                log.debug("VoiceWS send fail conn={}: {}", connId, e.getMessage());
            }
            catch (Exception e)
            {
                log.error("VoiceWS sender 异常 conn={}", connId, e);
            }
        }
    }

    String getConnId()
    {
        return connId;
    }

    String getSessionId()
    {
        return sessionId;
    }

    String getRole()
    {
        return role;
    }

    boolean isAsrActive()
    {
        return asrActive;
    }

    long getDroppedFrames()
    {
        return droppedFrames.get();
    }

    /** 发送队列条目：区分 TTS 分片以便 barge-in 整批丢弃，POISON 终止 sender */
    static final class Entry
    {
        static final Entry POISON = new Entry(Kind.POISON, "");

        enum Kind
        {
            TTS, OTHER, POISON
        }

        final Kind kind;

        final String payload;

        Entry(Kind kind, String payload)
        {
            this.kind = kind;
            this.payload = payload;
        }
    }
}
