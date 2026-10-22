package ai.lawyers.framework.websocket.voice;

import java.util.Base64;
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
 * <p>P5 超大类拆分：本类只保留"会话编排"职责——帧路由、ASR/TTS/VAD 链路、
 * 下行防抖、延迟埋点与生命周期。保序发送队列下沉 {@link VoiceSendQueue}，
 * E3 机器人回合下沉 {@link VoiceRobotTurnRunner}，F4 坐席辅助下沉
 * {@link VoiceCopilotAssistant}，E4 情绪联动下沉 {@link VoiceEmotionWatcher}。
 * 协作类经 {@link HostBridge} 回调下发帧/启动 TTS/读取 robot 模式，
 * 均不反向依赖门面，无循环依赖。</p>
 *
 * @author ai-lawyers
 */
public class VoiceSession
{
    private static final Logger log = LoggerFactory.getLogger(VoiceSession.class);

    /** TTS 文本上限（防超长合成拖垮 Mock/真实引擎） */
    static final int TTS_TEXT_MAX = 2000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Session wsSession;

    private final String connId;

    private final String sessionId;

    private final String role;

    private final AtomicBoolean shutdown = new AtomicBoolean();

    private final AtomicLong asrSeq = new AtomicLong();

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

    /** E3：start 帧 "robot":true 开启 ASR final 自动应答 */
    private volatile boolean robotMode;

    /* ---- P5 协作类（同包，经 HostBridge 回调，不反向依赖门面） ---- */
    /** P5：保序发送队列（所有下发帧经此入队，单线程写出） */
    private final VoiceSendQueue sendQueue;

    /** P5：E3 机器人回合执行器（回合串行，迟到结果按 turnId 丢弃） */
    private final VoiceRobotTurnRunner robot;

    /** P5：F4 坐席 Copilot 辅助执行器（滚动缓冲 + 节流 + 单线程） */
    private final VoiceCopilotAssistant copilot;

    /** P5：E4 情绪/意图联动（null=联动服务未装配，静默跳过） */
    private final VoiceEmotionWatcher emotion;

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

    /* ---- #1 下行媒体预留（PBX 侧空实现；媒体桥接就绪后替换 sink 即可） ---- */
    /** 下行防抖缓冲（attach 时创建，内含 sink 引用；可空=未挂下行） */
    private volatile DownlinkDebouncer downlinkDebouncer;

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
        this(wsSession, sessionId, role, queueCapacity, engineRegistry, vadEnabled, metrics,
                robotService, emotionAction, copilotService, vadShadowService, null);
    }

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity,
            VoiceEngineRegistry engineRegistry, boolean vadEnabled, HotlineMetrics metrics,
            ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService robotService,
            ai.lawyers.system.service.lawyers.voice.emotion.VoiceRiskActionService emotionAction,
            ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistService copilotService,
            SileroVadShadowService vadShadowService, Long agentId)
    {
        this.wsSession = wsSession;
        this.connId = wsSession.getId();
        this.sessionId = sessionId;
        this.role = role;
        this.engineRegistry = engineRegistry != null ? engineRegistry : new VoiceEngineRegistry();
        this.vadEnabled = vadEnabled;
        this.metrics = metrics;
        this.vadShadowService = vadShadowService;
        int cap = queueCapacity > 0 ? queueCapacity : 1000;
        // P5：组装协作类——HostBridge 持有隐式 VoiceSession 引用，协作类构造期不调用 host，
        // 实际回调（send/startTts/robotMode）发生在首个回合提交时，彼时字段已全部就绪
        this.sendQueue = new VoiceSendQueue(wsSession, connId, cap);
        HostBridge host = new HostBridge();
        this.robot = new VoiceRobotTurnRunner(robotService, host, connId, sessionId, metrics);
        this.copilot = new VoiceCopilotAssistant(copilotService, host, connId, sessionId, agentId);
        this.emotion = emotionAction == null ? null
                : new VoiceEmotionWatcher(emotionAction, host, sessionId, role);
    }

    /**
     * P5：协作类宿主桥——下发帧经会话保序队列、启动 TTS 经会话合成链路、
     * robot 模式读取会话 volatile 状态。内部类隐式持有 VoiceSession 引用，
     * 不暴露给协作类之外的组件。
     */
    private final class HostBridge
            implements VoiceRobotTurnRunner.Host, VoiceCopilotAssistant.Host, VoiceEmotionWatcher.Host
    {
        @Override
        public void send(String frame)
        {
            sendQueue.send(VoiceSendQueue.Entry.Kind.OTHER, frame);
        }

        @Override
        public void startTts(String text)
        {
            VoiceSession.this.startTts(text, null);
        }

        @Override
        public boolean robotMode()
        {
            return VoiceSession.this.robotMode;
        }
    }

    /* ================= 生命周期 ================= */

    /**
     * #1：附加 PBX 下行音频 sink（TTS 双路分发预留；bufferMs 钳制 100~200）。
     * 重复 attach 覆盖旧值；传 null 摘除。
     */
    void attachDownlinkSink(PbxDownlinkAudioSink sink, int bufferMs)
    {
        this.downlinkDebouncer = sink == null ? null : new DownlinkDebouncer(sink, bufferMs);
        log.info("VoiceWS downlink sink attached conn={} sink={} bufferMs={}", connId,
                sink == null ? "none" : sink.getClass().getSimpleName(),
                downlinkDebouncer == null ? -1 : downlinkDebouncer.getBufferMillis());
    }

    /** #1：打断/取消时丢弃下行防抖缓冲（与浏览器侧 drainPendingTts 对齐） */
    private void discardDownlink()
    {
        DownlinkDebouncer downlink = downlinkDebouncer;
        if (downlink != null)
        {
            downlink.discard();
        }
    }

    /** #1：会话释放——清残余下行缓冲并关闭 sink */
    private void releaseDownlink()
    {
        DownlinkDebouncer downlink = downlinkDebouncer;
        downlinkDebouncer = null;
        if (downlink != null)
        {
            downlink.release();
        }
    }

    void sendConnected()
    {
        enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.connected(sessionId, role));
    }

    /**
     * 处理一帧文本控制消息（start/audio/tts/ask/bargein/stop）。
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
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("BAD_FRAME", "帧不是合法 JSON"));
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
                    enqueue(VoiceSendQueue.Entry.Kind.OTHER,
                            VoiceFrames.error("UNKNOWN_TYPE", "未知帧类型: " + type));
            }
        }
        catch (Exception e)
        {
            log.warn("VoiceWS 帧处理异常 conn={}, type={}: {}", connId, type, e.getMessage());
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("FRAME_ERROR", e.getMessage()));
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
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("NOT_STARTED", "请先发送 start 帧再传音频"));
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
        robot.shutdown();
        // F4：作废迟到 Copilot 回合并停辅助执行器
        copilot.shutdown();
        StreamSynthesis tts = currentTts;
        if (tts != null)
        {
            tts.cancel();
        }
        discardDownlink();
        sendQueue.poison();
        releaseDownlink();
        long dropped = sendQueue.getDroppedFrames();
        log.info("VoiceWS session shutdown conn={}, sessionId={}, droppedFrames={}", connId, sessionId, dropped);
    }

    /* ================= start ================= */

    private void handleStart(JsonNode node)
    {
        if (asrActive)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("ALREADY_STARTED", "识别流已开启，请先 stop"));
            return;
        }
        String reqEngine = node.path("engine").asText(MockStreamAsrEngine.ENGINE_CODE);
        String format = node.path("format").asText("pcm");
        int rate = node.path("sampleRate").asInt(16000);
        if (!"pcm".equalsIgnoreCase(format))
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("BAD_FORMAT", "A1 仅支持 PCM(S16LE 单声道) 音频格式: " + format));
            return;
        }
        if (rate != 8000 && rate != 16000)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("BAD_SAMPLE_RATE", "采样率仅支持 8000/16000: " + rate));
            return;
        }
        StreamAsrEngine engineInstance = engineRegistry.createAsr(reqEngine);
        if (engineInstance == null)
        {
            // 显式拒绝，禁止静默降级成 Mock 造成"假成功"（L4 教训）
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("ENGINE_UNAVAILABLE",
                    "流式引擎 " + reqEngine + " 不可用（未注册或凭证缺失），当前支持 mock/dashscope"));
            return;
        }
        this.engine = engineInstance.engineCode();
        this.sampleRate = rate;
        // E3：start 帧可声明 robot:true 开启 ASR final 自动应答（服务未装配时仅记日志，ask 仍会明确报错）
        this.robotMode = node.path("robot").asBoolean(false);
        if (this.robotMode && !robot.isAvailable())
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
                    enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.asr(VoiceFrames.T_ASR_PARTIAL, asrSeq.incrementAndGet(), text));
                    // E4：partial 仅做 urgent 早预警（negative 等 final，防部分转写噪声误报）
                    detectEmotion(text, false);
                }
            }

            @Override
            public void onFinal(String text)
            {
                if (!shutdown.get())
                {
                    enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.asr(VoiceFrames.T_ASR_FINAL, asrSeq.incrementAndGet(), text));
                    // E4：final 完整识别（urgent/negative）
                    detectEmotion(text, true);
                    // E3：robot 模式下 ASR final 自动触发对话引擎回合（空文本不触发）
                    if (robotMode && text != null && !text.trim().isEmpty())
                    {
                        robot.submitTurn(text.trim());
                    }
                    // F4：final 文本滚动入缓冲，满足节流窗口时触发 Copilot 辅助（服务坐席，不区分角色）
                    copilot.queue(text);
                }
            }

            @Override
            public void onError(String code, String message)
            {
                enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error(code, message));
            }
        });
        this.asrEngine = engineInstance;
        this.asrActive = true;
        enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.started(sessionId, role, this.engine, "pcm", rate));
    }

    /* ================= audio ================= */

    private void handleAudio(JsonNode node)
    {
        if (!asrActive || asrEngine == null)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("NOT_STARTED", "请先发送 start 帧再传音频"));
            return;
        }
        String b64 = node.path("data").asText("");
        if (b64.isEmpty())
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("BAD_AUDIO", "audio 帧缺少 data(base64 PCM)"));
            return;
        }
        final byte[] pcm;
        try
        {
            pcm = Base64.getDecoder().decode(b64);
        }
        catch (IllegalArgumentException e)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("BAD_AUDIO", "data 不是合法 base64"));
            return;
        }
        if (pcm.length == 0)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("BAD_AUDIO", "音频帧为空"));
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
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("INVALID_PARAM", "tts 帧缺少 text"));
            return;
        }
        if (text.length() > TTS_TEXT_MAX)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("TEXT_TOO_LONG",
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
            discardDownlink();
        }
        final int ttsRate = this.sampleRate;
        // A5：TTS 首包计时起点（新请求重置，覆盖旧任务）
        this.ttsStartNanos = System.nanoTime();
        this.ttsFirstMarked.set(false);
        // A2/A3：TTS 引擎跟随 start 帧选定的引擎（mock/dashscope）
        StreamTtsEngine ttsEngine = engineRegistry.createTts(this.engine);
        if (ttsEngine == null)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("ENGINE_UNAVAILABLE",
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
                        // E3：机器人回合 E2E（问题就绪→应答首个音频分片，仅记一次）
                        robot.markE2eIfNeeded();
                        enqueue(VoiceSendQueue.Entry.Kind.TTS, VoiceFrames.ttsAudio(seq, chunkCount, ttsRate, pcm));
                        // #1：下行双路分发——浏览器侧已入发送队列，PBX 侧经防抖缓冲合片写 sink
                        DownlinkDebouncer downlink = downlinkDebouncer;
                        if (downlink != null)
                        {
                            downlink.offer(pcm, ttsRate);
                        }
                    }

                    @Override
                    public void onEnd()
                    {
                        StreamSynthesis self = currentTts;
                        if (self != null && self.isCancelled())
                        {
                            return;
                        }
                        // #1：正常结束补写不足一个防抖窗口的尾片（先 flush 再发 tts_end，保序）
                        DownlinkDebouncer downlink = downlinkDebouncer;
                        if (downlink != null)
                        {
                            downlink.flush();
                        }
                        enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.ttsEnd(false, null));
                    }

                    @Override
                    public void onError(String code, String message)
                    {
                        // #1：合成失败——已分发到浏览器的部分分片照常补写下行使两侧一致
                        DownlinkDebouncer downlink = downlinkDebouncer;
                        if (downlink != null)
                        {
                            downlink.flush();
                        }
                        enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error(code, message));
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
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("INVALID_PARAM", "ask 帧缺少 text"));
            return;
        }
        if (question.length() > 500)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("TEXT_TOO_LONG",
                    "ask 问题上限 500 字符，当前 " + question.length()));
            return;
        }
        if (!robot.isAvailable())
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("ROBOT_UNAVAILABLE", "对话引擎未装配"));
            return;
        }
        robot.submitTurn(question);
    }

    /**
     * G6：由 PBX 媒体 fork 桥接层锚定本通话的话单ID（服务端可信来源）。
     * 仅允许设置一次，浏览器/坐席链路不调用此方法。
     */
    void anchorRecordId(Long recordId)
    {
        robot.anchorRecordId(recordId);
    }

    /* ================= E4：情绪/意图识别联动 ================= */

    /**
     * 对一段 ASR 文本执行情绪/意图联动（未装配联动服务时静默跳过）。
     *
     * @param text    ASR 文本
     * @param isFinal true=final（urgent/negative 均可）；false=partial（仅 urgent）
     */
    private void detectEmotion(String text, boolean isFinal)
    {
        if (emotion == null)
        {
            return;
        }
        emotion.detect(text, isFinal);
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
        enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.vad(VoiceFrames.T_VAD_SPEECH_START));
        StreamSynthesis tts = currentTts;
        if (tts != null && !tts.isCancelled())
        {
            long bargeinBegin = System.nanoTime();
            tts.cancel();
            drainPendingTts();
            discardDownlink();
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.ttsEnd(true, "vad_speech_start"));
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
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.vad(VoiceFrames.T_VAD_SPEECH_END));
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
        discardDownlink();
        // 明确发一帧 interrupted tts_end，客户端复位 AudioContext 播放队列
        enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.ttsEnd(true, "bargein"));
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
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("NOT_STARTED", "无进行中的识别流"));
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
        robot.invalidateTurn();
        // F4：作废在途 Copilot 回合（close 内部 final 仍会入缓冲，随后强制补最后一回合）
        copilot.invalidate();
        try
        {
            engineRef.close();
        }
        catch (Exception e)
        {
            enqueue(VoiceSendQueue.Entry.Kind.OTHER, VoiceFrames.error("ASR_CLOSE_ERROR", e.getMessage()));
        }
        // F4：停止前以累计缓冲强制辅助一次（不受间隔/最小字数限制），让挂断前要素完整
        copilot.forceFlush();
    }

    /** A5：纳秒差转毫秒（System.nanoTime 差值 → ms，截断取整） */
    private static long nanosToMs(long nanos)
    {
        return nanos / 1_000_000L;
    }

    /* ================= 发送队列（委托 VoiceSendQueue） ================= */

    private void drainPendingTts()
    {
        sendQueue.drainTts();
    }

    private void enqueue(VoiceSendQueue.Entry.Kind kind, String payload)
    {
        if (shutdown.get() && kind != VoiceSendQueue.Entry.Kind.POISON)
        {
            return;
        }
        sendQueue.send(kind, payload);
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
}
