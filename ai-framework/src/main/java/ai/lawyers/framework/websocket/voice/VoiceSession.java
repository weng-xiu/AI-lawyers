package ai.lawyers.framework.websocket.voice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    VoiceSession(Session wsSession, String sessionId, String role, int queueCapacity)
    {
        this.wsSession = wsSession;
        this.connId = wsSession.getId();
        this.sessionId = sessionId;
        this.role = role;
        int cap = queueCapacity > 0 ? queueCapacity : 1000;
        this.sendQueue = new LinkedBlockingQueue<>(cap);
        this.sender = new Thread(this::runSender, "voice-sender-" + connId);
        this.sender.setDaemon(true);
        this.sender.start();
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
        if (!MockStreamAsrEngine.ENGINE_CODE.equalsIgnoreCase(reqEngine))
        {
            // 显式拒绝，禁止静默降级成 Mock 造成"假成功"（L4 教训）
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("ENGINE_UNAVAILABLE",
                    "流式引擎 " + reqEngine + " 尚未接入（真实 Provider 随 P3-A2/A3），当前仅支持 engine=mock"));
            return;
        }
        this.engine = MockStreamAsrEngine.ENGINE_CODE;
        this.sampleRate = rate;
        MockStreamAsrEngine engineInstance = new MockStreamAsrEngine();
        VoiceAsrContext ctx = new VoiceAsrContext(sessionId, role, "pcm", rate);
        engineInstance.open(ctx, new AsrStreamCallback()
        {
            @Override
            public void onPartial(String text)
            {
                if (!shutdown.get())
                {
                    enqueue(Entry.Kind.OTHER, VoiceFrames.asr(VoiceFrames.T_ASR_PARTIAL, asrSeq.incrementAndGet(), text));
                }
            }

            @Override
            public void onFinal(String text)
            {
                if (!shutdown.get())
                {
                    enqueue(Entry.Kind.OTHER, VoiceFrames.asr(VoiceFrames.T_ASR_FINAL, asrSeq.incrementAndGet(), text));
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
        // 新合成请求覆盖旧的：旧句柄静默取消（新任务自带 tts_end，客户端按 seq 重新计数）
        StreamSynthesis prev = currentTts;
        if (prev != null)
        {
            prev.cancel();
            drainPendingTts();
        }
        final int ttsRate = this.sampleRate;
        StreamSynthesis handle = MockStreamTtsEngine.INSTANCE.synthesizeStream(text, voice, ttsRate,
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

    /* ================= bargein / stop ================= */

    private void handleBargein()
    {
        StreamSynthesis tts = currentTts;
        if (tts != null)
        {
            tts.cancel();
        }
        drainPendingTts();
        // 明确发一帧 interrupted tts_end，客户端复位 AudioContext 播放队列
        enqueue(Entry.Kind.OTHER, VoiceFrames.ttsEnd(true, "bargein"));
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
        try
        {
            engineRef.close();
        }
        catch (Exception e)
        {
            enqueue(Entry.Kind.OTHER, VoiceFrames.error("ASR_CLOSE_ERROR", e.getMessage()));
        }
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
