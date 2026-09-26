package ai.lawyers.framework.websocket.voice;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.voice.VoiceProperties;

/**
 * DashScope 流式 TTS 引擎（P3-A3）。
 *
 * <p>协议：DashScope 全双工 WebSocket（api-ws/v1/inference），
 * 模型 cosyvoice-v1，首包即播。
 * 每会话独占一条 WebSocket 连接，线程安全由会话层保证。
 * 无 apiKey 时 synthesizeStream 返回已取消句柄并回调 onError。</p>
 *
 * @author ai-lawyers
 */
public class DashScopeStreamTtsEngine implements StreamTtsEngine
{
    public static final String ENGINE_CODE = "dashscope";

    private static final Logger log = LoggerFactory.getLogger(DashScopeStreamTtsEngine.class);

    private static final long CONNECT_TIMEOUT_MS = 10000L;

    private static final int FRAME_MS = 20;

    private final VoiceProperties properties;

    public DashScopeStreamTtsEngine(VoiceProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public String engineCode()
    {
        return ENGINE_CODE;
    }

    @Override
    public StreamSynthesis synthesizeStream(String text, String voice, int sampleRate, TtsStreamCallback callback)
    {
        if (StringUtils.isEmpty(properties.getDashscopeApiKey()))
        {
            callback.onError("ENGINE_UNAVAILABLE", "DashScope api-key 未配置");
            return new CancelledSynthesis();
        }
        String taskId = DashScopeWsProtocol.newTaskId();
        String fmt = sampleRate == 8000 ? "pcm_8000hz_mono_16bit" : "pcm_16000hz_mono_16bit";
        String model = properties.getDashscopeTtsModel();
        String v = StringUtils.isNotEmpty(voice) ? voice : properties.getDashscopeVoice();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        DashSynthesis handle = new DashSynthesis(client);
        int bytesPerFrame = sampleRate * FRAME_MS / 1000 * 2; // S16LE mono

        Request request = new Request.Builder()
                .url(DashScopeWsProtocol.WS_URL)
                .header("Authorization", "Bearer " + properties.getDashscopeApiKey())
                .header("User-Agent", "ai-lawyers/3.9.0")
                .build();

        WebSocket ws = client.newWebSocket(request, new WebSocketListener()
        {
            @Override
            public void onOpen(WebSocket webSocket, Response response)
            {
                ObjectNode params = DashScopeWsProtocol.buildTtsParameters(v, fmt, sampleRate);
                String run = DashScopeWsProtocol.buildRunTask(
                        taskId, "audio", DashScopeWsProtocol.TASK_TTS,
                        DashScopeWsProtocol.FUNCTION_SPEECH_SYNTHESIZER, model, params);
                webSocket.send(run);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text)
            {
                String event = DashScopeWsProtocol.parseEvent(text);
                switch (event)
                {
                    case DashScopeWsProtocol.EVENT_TASK_STARTED:
                        // 发送待合成文本
                        webSocket.send(DashScopeWsProtocol.buildContinueTask(taskId, text));
                        break;
                    case DashScopeWsProtocol.EVENT_RESULT_GENERATED:
                        // TTS 音频在二进制帧中，JSON 中可能包含元数据，忽略
                        break;
                    case DashScopeWsProtocol.EVENT_TASK_FAILED:
                        String err = DashScopeWsProtocol.parseError(text);
                        log.warn("DashScope TTS task-failed taskId={} {}", taskId, err);
                        if (!handle.isCancelled())
                        {
                            callback.onError("TTS_TASK_FAILED", err);
                        }
                        break;
                    case DashScopeWsProtocol.EVENT_TASK_FINISHED:
                        if (!handle.isCancelled())
                        {
                            flushAudioBuffer(handle, bytesPerFrame, callback);
                            callback.onEnd();
                        }
                        client.dispatcher().executorService().shutdown();
                        client.connectionPool().evictAll();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes)
            {
                if (handle.isCancelled())
                {
                    return;
                }
                handle.append(bytes.toByteArray());
                emitFrames(handle, bytesPerFrame, callback);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response)
            {
                log.warn("DashScope TTS ws failure taskId={} {}", taskId, t.getMessage());
                if (!handle.isCancelled())
                {
                    callback.onError("TTS_WS_FAILURE", t.getMessage());
                }
                client.dispatcher().executorService().shutdown();
                client.connectionPool().evictAll();
            }
        });
        handle.setWs(ws);
        return handle;
    }

    /** 从缓冲区按固定帧大小切分并回调 */
    private void emitFrames(DashSynthesis handle, int bytesPerFrame, TtsStreamCallback callback)
    {
        while (true)
        {
            byte[] chunk = handle.takeChunk(bytesPerFrame);
            if (chunk == null)
            {
                break;
            }
            int seq = handle.nextSeq();
            callback.onAudio(seq, -1, chunk); // chunkCount 未知，由前端按 tts_end 截断
        }
    }

    /** 任务结束时清空残余缓冲区 */
    private void flushAudioBuffer(DashSynthesis handle, int bytesPerFrame, TtsStreamCallback callback)
    {
        byte[] tail = handle.takeRemainder();
        if (tail != null && tail.length > 0)
        {
            int seq = handle.nextSeq();
            callback.onAudio(seq, -1, tail);
        }
    }

    /**
     * DashScope 流式合成句柄（barge-in 可取消）。
     */
    private static final class DashSynthesis implements StreamSynthesis
    {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<WebSocket> wsRef = new AtomicReference<>();
        private final OkHttpClient client;
        private final Object bufLock = new Object();
        private byte[] buffer = new byte[0];
        private final AtomicInteger seq = new AtomicInteger(-1);

        DashSynthesis(OkHttpClient client)
        {
            this.client = client;
        }

        void setWs(WebSocket ws)
        {
            wsRef.set(ws);
        }

        void append(byte[] data)
        {
            synchronized (bufLock)
            {
                byte[] next = new byte[buffer.length + data.length];
                System.arraycopy(buffer, 0, next, 0, buffer.length);
                System.arraycopy(data, 0, next, buffer.length, data.length);
                buffer = next;
            }
        }

        /** 从缓冲区取一帧（bytesPerFrame），不足返回 null */
        byte[] takeChunk(int bytesPerFrame)
        {
            synchronized (bufLock)
            {
                if (buffer.length < bytesPerFrame)
                {
                    return null;
                }
                byte[] chunk = new byte[bytesPerFrame];
                System.arraycopy(buffer, 0, chunk, 0, bytesPerFrame);
                byte[] remain = new byte[buffer.length - bytesPerFrame];
                System.arraycopy(buffer, bytesPerFrame, remain, 0, remain.length);
                buffer = remain;
                return chunk;
            }
        }

        /** 取剩余全部 */
        byte[] takeRemainder()
        {
            synchronized (bufLock)
            {
                byte[] r = buffer;
                buffer = new byte[0];
                return r.length > 0 ? r : null;
            }
        }

        int nextSeq()
        {
            return seq.incrementAndGet();
        }

        @Override
        public void cancel()
        {
            if (cancelled.compareAndSet(false, true))
            {
                WebSocket ws = wsRef.getAndSet(null);
                if (ws != null)
                {
                    try
                    {
                        ws.close(1000, "cancel");
                    }
                    catch (Exception ignore)
                    {
                    }
                }
                client.dispatcher().executorService().shutdown();
                client.connectionPool().evictAll();
            }
        }

        @Override
        public boolean isCancelled()
        {
            return cancelled.get();
        }
    }

    /** 已取消句柄（apiKey 缺失时返回） */
    private static final class CancelledSynthesis implements StreamSynthesis
    {
        @Override
        public void cancel()
        {
        }

        @Override
        public boolean isCancelled()
        {
            return true;
        }
    }
}
