package ai.lawyers.framework.websocket.voice;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * DashScope 流式 ASR 引擎（P3-A2）。
 *
 * <p>协议：DashScope 全双工 WebSocket（api-ws/v1/inference），
 * 模型 paraformer-realtime-v2，PCM 16000Hz/8000Hz S16LE 单声道。
 * 每会话独占一条 WebSocket 连接，线程安全由会话层保证。
 * 无 apiKey 时 open 立即回调 onError("ENGINE_UNAVAILABLE")。</p>
 *
 * @author ai-lawyers
 */
public class DashScopeStreamAsrEngine implements StreamAsrEngine
{
    public static final String ENGINE_CODE = "dashscope";

    private static final Logger log = LoggerFactory.getLogger(DashScopeStreamAsrEngine.class);

    private static final long CONNECT_TIMEOUT_MS = 10000L;

    private static final long OPEN_TIMEOUT_MS = 5000L;

    /** DashScope 要求 PCM 单声道 S16LE */
    private static final String FORMAT_PCM = "pcm";

    private final VoiceProperties properties;

    private final OkHttpClient httpClient;

    private final AtomicBoolean opened = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final AtomicReference<WebSocket> wsRef = new AtomicReference<>();

    private volatile AsrStreamCallback callback;

    private volatile String taskId;

    private volatile VoiceAsrContext context;

    public DashScopeStreamAsrEngine(VoiceProperties properties)
    {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String engineCode()
    {
        return ENGINE_CODE;
    }

    @Override
    public void open(VoiceAsrContext ctx, AsrStreamCallback cb)
    {
        if (ctx == null || cb == null)
        {
            throw new IllegalArgumentException("context/callback required");
        }
        if (StringUtils.isEmpty(properties.getDashscopeApiKey()))
        {
            cb.onError("ENGINE_UNAVAILABLE", "DashScope api-key 未配置");
            return;
        }
        if (!opened.compareAndSet(false, true))
        {
            return;
        }
        this.context = ctx;
        this.callback = cb;
        this.taskId = DashScopeWsProtocol.newTaskId();
        connect(ctx, cb);
    }

    private void connect(VoiceAsrContext ctx, AsrStreamCallback cb)
    {
        Request request = new Request.Builder()
                .url(DashScopeWsProtocol.WS_URL)
                .header("Authorization", "Bearer " + properties.getDashscopeApiKey())
                .header("User-Agent", "ai-lawyers/3.9.0")
                .build();

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final AtomicBoolean startedOk = new AtomicBoolean();
        final AtomicReference<String> startError = new AtomicReference<>();
        WebSocket ws = httpClient.newWebSocket(request, new WebSocketListener()
        {
            @Override
            public void onOpen(WebSocket webSocket, Response response)
            {
                log.debug("DashScope ASR ws opened taskId={}", taskId);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text)
            {
                String event = DashScopeWsProtocol.parseEvent(text);
                switch (event)
                {
                    case DashScopeWsProtocol.EVENT_TASK_STARTED:
                        startedOk.set(true);
                        startedLatch.countDown();
                        break;
                    case DashScopeWsProtocol.EVENT_RESULT_GENERATED:
                        DashScopeWsProtocol.AsrSentence s = DashScopeWsProtocol.parseAsrSentence(text);
                        if (s != null && !s.text.isEmpty())
                        {
                            if (s.isEnd)
                            {
                                cb.onFinal(s.text);
                            }
                            else
                            {
                                cb.onPartial(s.text);
                            }
                        }
                        break;
                    case DashScopeWsProtocol.EVENT_TASK_FAILED:
                        String err = DashScopeWsProtocol.parseError(text);
                        log.warn("DashScope ASR task-failed taskId={} {}", taskId, err);
                        cb.onError("ASR_TASK_FAILED", err);
                        startedLatch.countDown();
                        break;
                    case DashScopeWsProtocol.EVENT_TASK_FINISHED:
                        log.debug("DashScope ASR task-finished taskId={}", taskId);
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes)
            {
                // ASR 服务端不发送二进制帧，忽略
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason)
            {
                webSocket.close(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response)
            {
                log.warn("DashScope ASR ws failure taskId={} {}", taskId, t.getMessage());
                if (!closed.get())
                {
                    cb.onError("ASR_WS_FAILURE", t.getMessage());
                }
                startedLatch.countDown();
            }
        });
        wsRef.set(ws);

        // 等待 task-started（服务端在收到 run-task 前不会发音频结果）
        boolean awaitOk;
        try
        {
            awaitOk = startedLatch.await(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            awaitOk = false;
        }
        if (!awaitOk || !startedOk.get())
        {
            String err = startError.get();
            log.warn("DashScope ASR task-started 超时 taskId={} err={}", taskId, err);
            safeClose();
            cb.onError("ASR_OPEN_TIMEOUT", "DashScope ASR 连接超时或启动失败");
            return;
        }

        // 发送 run-task
        ObjectNode params = DashScopeWsProtocol.buildAsrParameters(FORMAT_PCM, ctx.getSampleRate());
        String runTask = DashScopeWsProtocol.buildRunTask(
                taskId, "audio", DashScopeWsProtocol.TASK_ASR,
                DashScopeWsProtocol.FUNCTION_RECOGNITION,
                properties.getDashscopeAsrModel(), params);
        ws.send(runTask);
        log.info("DashScope ASR 已启动 taskId={} model={} sampleRate={}",
                taskId, properties.getDashscopeAsrModel(), ctx.getSampleRate());
    }

    @Override
    public void feed(byte[] frame)
    {
        if (closed.get() || frame == null || frame.length == 0)
        {
            return;
        }
        WebSocket ws = wsRef.get();
        if (ws == null)
        {
            return;
        }
        ws.send(ByteString.of(frame));
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        WebSocket ws = wsRef.get();
        if (ws != null)
        {
            try
            {
                ws.send(DashScopeWsProtocol.buildFinishTask(taskId));
            }
            catch (Exception e)
            {
                log.debug("DashScope ASR finish-task 发送异常: {}", e.getMessage());
            }
            safeClose();
        }
        AsrStreamCallback cb = callback;
        if (cb != null)
        {
            cb.onFinal("");
        }
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    private void safeClose()
    {
        WebSocket ws = wsRef.getAndSet(null);
        if (ws != null)
        {
            try
            {
                ws.close(1000, "client close");
            }
            catch (Exception ignore)
            {
            }
        }
    }
}
