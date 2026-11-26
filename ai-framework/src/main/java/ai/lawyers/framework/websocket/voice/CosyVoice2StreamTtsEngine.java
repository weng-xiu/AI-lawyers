package ai.lawyers.framework.websocket.voice;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.voice.VoiceProperties;

/**
 * 内网自建 CosyVoice 2 流式 TTS 引擎（N1③，语雀 7.3）。
 *
 * <p><b>协议说明（推断，非权威）</b>：自建 CosyVoice 2 / Fun-CosyVoice 的 OpenAI 兼容服务
 * 通常暴露 {@code POST {base-url}/audio/speech}，请求体
 * {@code {model, input, voice, response_format:"pcm", speed}}，响应为 S16LE 单声道 PCM 字节流。
 * 本类按该约定实现；联调若字段/路径不同，仅需调整 {@link #buildRequest} 的请求构造与
 * {@link #streamResponse} 的流读取两处，不影响上层会话与回调契约。</p>
 *
 * <p><b>双轨纪律</b>：与云侧 {@link DashScopeStreamTtsEngine}（cosyvoice-v1）并存，
 * 任一异常可经 {@code voice.engine} 一键回退；未配置
 * {@code voice.cosyvoice2-tts-base-url} 时注册表返回 null，会话层显式回 ENGINE_UNAVAILABLE，
 * 默认 mock 链路零差异。</p>
 *
 * <p><b>线程模型</b>：每次合成独占 OkHttp 连接与一个守护工作线程顺序读取响应流，
 * 按 20ms 固定帧切分回调；barge-in 时 cancel 关闭连接并中断读取，目标 ≤300ms 停播。</p>
 *
 * @author ai-lawyers
 */
public class CosyVoice2StreamTtsEngine implements StreamTtsEngine
{
    public static final String ENGINE_CODE = "cosyvoice2";

    private static final Logger log = LoggerFactory.getLogger(CosyVoice2StreamTtsEngine.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long CONNECT_TIMEOUT_MS = 10000L;

    private static final int FRAME_MS = 20;

    private static final int READ_CHUNK = 4096;

    /** 单次合成工作线程池（守护线程，空闲回收），避免裸 new Thread */
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "voice-cosyvoice2-tts");
        t.setDaemon(true);
        return t;
    });

    private final VoiceProperties properties;

    public CosyVoice2StreamTtsEngine(VoiceProperties properties)
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
        if (StringUtils.isEmpty(properties.getCosyvoice2TtsBaseUrl()))
        {
            callback.onError("ENGINE_UNAVAILABLE", "CosyVoice2 tts-base-url 未配置");
            return new CancelledSynthesis();
        }
        if (text == null || text.trim().isEmpty())
        {
            callback.onError("TTS_BAD_REQUEST", "待合成文本为空");
            return new CancelledSynthesis();
        }
        if (sampleRate != 8000 && sampleRate != 16000)
        {
            callback.onError("TTS_BAD_REQUEST", "不支持的采样率: " + sampleRate);
            return new CancelledSynthesis();
        }

        String v = StringUtils.isNotEmpty(voice) ? voice : properties.getCosyvoice2Voice();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        CosySynthesis handle = new CosySynthesis();
        int bytesPerFrame = sampleRate * FRAME_MS / 1000 * 2; // S16LE mono
        Call call = client.newCall(buildRequest(text, v, sampleRate));
        handle.setCall(call);

        POOL.submit(() -> streamResponse(handle, client, call, bytesPerFrame, callback));
        return handle;
    }

    /** 构造并发起 HTTP 请求，顺序读取 PCM 流切帧回调 */
    private void streamResponse(CosySynthesis handle, OkHttpClient client, Call call,
                                int bytesPerFrame, TtsStreamCallback callback)
    {
        try (Response response = call.execute())
        {
            if (!response.isSuccessful())
            {
                String err = "HTTP " + response.code();
                log.warn("CosyVoice2 TTS 请求失败 {}", err);
                if (!handle.isCancelled())
                {
                    callback.onError("TTS_HTTP_ERROR", err);
                }
                return;
            }
            ResponseBody body = response.body();
            if (body == null)
            {
                if (!handle.isCancelled())
                {
                    callback.onError("TTS_EMPTY_BODY", "响应体为空");
                }
                return;
            }
            try (InputStream in = body.byteStream())
            {
                byte[] readBuf = new byte[READ_CHUNK];
                int n;
                while (!handle.isCancelled() && (n = in.read(readBuf)) != -1)
                {
                    if (n <= 0)
                    {
                        continue;
                    }
                    byte[] data = new byte[n];
                    System.arraycopy(readBuf, 0, data, 0, n);
                    handle.append(data);
                    emitFrames(handle, bytesPerFrame, callback);
                }
                if (!handle.isCancelled())
                {
                    flushTail(handle, bytesPerFrame, callback);
                    callback.onEnd();
                }
            }
        }
        catch (Exception e)
        {
            log.warn("CosyVoice2 TTS 流式合成异常: {}", e.getMessage());
            if (!handle.isCancelled())
            {
                callback.onError("TTS_STREAM_ERROR", e.getMessage());
            }
        }
        finally
        {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }

    private Request buildRequest(String text, String voice, int sampleRate)
    {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", properties.getCosyvoice2TtsModel());
        payload.put("input", text);
        payload.put("voice", voice);
        payload.put("response_format", "pcm");
        // 采样率不在 OpenAI 标准字段内，按自建网关扩展参数传递；不支持的网关忽略不影响 PCM 解析
        payload.put("sample_rate", sampleRate);

        Request.Builder builder = new Request.Builder()
                .url(joinSpeechUrl(properties.getCosyvoice2TtsBaseUrl()))
                .post(RequestBody.create(MediaTypes.JSON, payload.toString().getBytes(StandardCharsets.UTF_8)));
        if (StringUtils.isNotEmpty(properties.getCosyvoice2ApiKey()))
        {
            builder.header("Authorization", "Bearer " + properties.getCosyvoice2ApiKey());
        }
        return builder.build();
    }

    /** 拼接 /audio/speech，容忍 base-url 末尾斜杠与是否含 /v1 */
    private static String joinSpeechUrl(String baseUrl)
    {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/audio/speech";
    }

    /** 从缓冲区按固定帧大小切分并回调（chunkCount 未知，传 -1 由前端按 tts_end 截断） */
    private void emitFrames(CosySynthesis handle, int bytesPerFrame, TtsStreamCallback callback)
    {
        while (true)
        {
            byte[] chunk = handle.takeChunk(bytesPerFrame);
            if (chunk == null)
            {
                break;
            }
            callback.onAudio(handle.nextSeq(), -1, chunk);
        }
    }

    /** 结束时下发残余不足一帧的 PCM，避免尾部音频丢失 */
    private void flushTail(CosySynthesis handle, int bytesPerFrame, TtsStreamCallback callback)
    {
        byte[] tail = handle.takeRemainder();
        if (tail != null && tail.length > 0)
        {
            callback.onAudio(handle.nextSeq(), -1, tail);
        }
    }

    /** 合成句柄：Call 取消 + 缓冲切帧，语义对齐 DashScope 实现 */
    private static final class CosySynthesis implements StreamSynthesis
    {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private final AtomicReference<Call> callRef = new AtomicReference<>();

        private final Object bufLock = new Object();

        private byte[] buffer = new byte[0];

        private final AtomicInteger seq = new AtomicInteger(-1);

        void setCall(Call call)
        {
            callRef.set(call);
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
                // 取消在途 Call：阻塞中的 InputStream.read 立即抛 IOException，工作线程随之收敛
                Call call = callRef.getAndSet(null);
                if (call != null)
                {
                    call.cancel();
                }
            }
        }

        @Override
        public boolean isCancelled()
        {
            return cancelled.get();
        }
    }

    /** 已取消句柄（前置校验失败时返回） */
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

    /** 内部 MediaType 常量集中持有，避免散落字面量 */
    private static final class MediaTypes
    {
        static final okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
    }
}
