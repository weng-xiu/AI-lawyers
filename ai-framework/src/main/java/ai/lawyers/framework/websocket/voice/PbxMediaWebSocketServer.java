package ai.lawyers.framework.websocket.voice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * P3-B3：PBX 媒体接入 WebSocket 端点（来电者 PCM 源）。
 *
 * <p>路径：{@code /ws/pbx/media/{recordId}}，由 FreeSWITCH 侧
 * mod_audio_fork / mod_audio_stream 在来话建立后主动连接；协议翻译委托
 * {@link PbxMediaForkHandler}，桥接为 role=caller 的 {@link VoiceSession}，
 * 使 ASR 流式转写、E4 情绪/意图联动、Copilot 获得真实来电者音频。</p>
 *
 * <p>鉴权：PBX 为服务端组件不持 JWT，校验 query {@code key} 与配置共享密钥
 * （密钥为空且部署在完全隔离内网时放行；建议同时在网络层做 PBX IP 白名单）；
 * recordId 必须为话单数字主键。容量/开关复用 {@link VoiceSessionManager}。</p>
 *
 * @author ai-lawyers
 */
@Component
@ServerEndpoint("/ws/pbx/media/{recordId}")
public class PbxMediaWebSocketServer
{
    private static final Logger log = LoggerFactory.getLogger(PbxMediaWebSocketServer.class);

    /* ---- M3-3：进程内活跃 fork 状态（gauge 单一真源；handler start/close 绑定） ---- */
    private static final AtomicInteger ACTIVE_COUNT = new AtomicInteger();

    private static final Set<PbxMediaForkHandler> ACTIVE_HANDLERS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final Object SILENCE_HOLDER = new Object();

    private static final AtomicBoolean GAUGES_REGISTERED = new AtomicBoolean();

    /** start 受理：活跃数+1、纳入静默扫描、首次注册两个 gauge（幂等） */
    static void onForkStarted(PbxMediaForkHandler handler)
    {
        ACTIVE_HANDLERS.add(handler);
        ACTIVE_COUNT.incrementAndGet();
        HotlineMetrics metrics = handler.metrics();
        if (metrics != null && GAUGES_REGISTERED.compareAndSet(false, true))
        {
            metrics.gaugePbxActive(ACTIVE_COUNT, o -> ACTIVE_COUNT.get());
            metrics.gaugePbxSilence(SILENCE_HOLDER,
                    o -> ACTIVE_HANDLERS.stream()
                            .mapToDouble(h -> h.silenceAgeSeconds(System.nanoTime()))
                            .max().orElse(0));
        }
    }

    /** 连接关闭：活跃数-1（仅当此前在集；stop/异常关闭都收敛到 0） */
    static void onForkClosed(PbxMediaForkHandler handler)
    {
        if (ACTIVE_HANDLERS.remove(handler))
        {
            ACTIVE_COUNT.decrementAndGet();
        }
    }

    /** 测试/巡检用：当前活跃 fork 数 */
    static int activeCount()
    {
        return ACTIVE_COUNT.get();
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("recordId") String recordId)
    {
        if (recordId == null || !recordId.matches("\\d+"))
        {
            recordReject(session, "bad_record", CloseReason.CloseCodes.VIOLATED_POLICY,
                    "recordId must be numeric");
            return;
        }
        PbxMediaForkProperties properties;
        VoiceSessionManager manager;
        try
        {
            properties = SpringUtils.getBean(PbxMediaForkProperties.class);
            manager = SpringUtils.getBean(VoiceSessionManager.class);
        }
        catch (Exception e)
        {
            log.warn("[PbxMedia] 容器未就绪，拒绝连接 connId={}: {}", session.getId(), e.getMessage());
            recordReject(session, "unavailable", CloseReason.CloseCodes.TRY_AGAIN_LATER,
                    "service unavailable");
            return;
        }
        if (!properties.isEnabled())
        {
            recordReject(session, "disabled", CloseReason.CloseCodes.TRY_AGAIN_LATER,
                    "pbx media disabled");
            return;
        }
        if (!checkAuthKey(session.getQueryString(), properties.getAuthKey()))
        {
            log.warn("[PbxMedia] 共享密钥校验失败 recordId={} connId={}", recordId, session.getId());
            recordReject(session, "auth_fail", CloseReason.CloseCodes.VIOLATED_POLICY,
                    "bad auth key");
            return;
        }

        HotlineMetrics metrics = null;
        try
        {
            metrics = SpringUtils.getBean(HotlineMetrics.class);
        }
        catch (Exception e)
        {
            // 指标非关键路径：缺 bean 不影响媒体受理
            log.debug("[PbxMedia] HotlineMetrics 未就绪，本次连接无指标: {}", e.getMessage());
        }

        PbxMediaForkHandler handler = new PbxMediaForkHandler(
                recordId, session, manager, properties, metrics);
        session.getUserProperties().put("pbxHandler", handler);
        session.getUserProperties().put("pbxAccepted", Boolean.TRUE);
        if (metrics != null)
        {
            metrics.incrementPbxConnect();
        }
        log.info("[PbxMedia] PBX 媒体连接已受理 recordId={} connId={}", recordId, session.getId());
    }

    @OnMessage
    public void onTextMessage(String message, Session session)
    {
        PbxMediaForkHandler handler = current(session);
        if (handler == null)
        {
            return;
        }
        if (!handler.handleText(message))
        {
            session.getUserProperties().put("pbxCloseReason", "bad_frame");
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "bad frame");
        }
    }

    /** 二进制裸 PCM（mod_audio_fork）直传 */
    @OnMessage
    public void onBinaryMessage(ByteBuffer buffer, Session session)
    {
        PbxMediaForkHandler handler = current(session);
        if (handler == null || buffer == null)
        {
            return;
        }
        byte[] pcm = new byte[buffer.remaining()];
        buffer.get(pcm);
        handler.handleBinary(pcm);
    }

    @OnClose
    public void onClose(Session session)
    {
        PbxMediaForkHandler handler = current(session);
        boolean accepted = Boolean.TRUE.equals(session.getUserProperties().get("pbxAccepted"));
        try
        {
            if (handler != null)
            {
                handler.finish();
            }
            VoiceSessionManager manager = SpringUtils.getBean(VoiceSessionManager.class);
            manager.unregister(session.getId());
        }
        catch (Exception e)
        {
            // 容器关闭时序 bean 可能已销毁：兜底停会话
            if (handler != null)
            {
                handler.finish();
            }
        }

        // M3-3：已受理连接的关闭原因分类 + 生命周期指标（拒绝的连接不计）
        if (accepted)
        {
            String reason = (String) session.getUserProperties().get("pbxCloseReason");
            if (reason == null)
            {
                reason = handler != null && handler.isStopReceived() ? "stop" : "abnormal";
            }
            HotlineMetrics metrics = handler == null ? null : handler.metrics();
            if (metrics != null)
            {
                metrics.recordPbxSession(
                        (System.nanoTime() - handler.openNanos()) / 1_000_000);
                metrics.incrementPbxClose(reason);
            }
            if (handler != null)
            {
                onForkClosed(handler);
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error)
    {
        log.warn("[PbxMedia] error connId={}: {}",
                session == null ? "" : session.getId(), error.getMessage());
        if (session != null
                && Boolean.TRUE.equals(session.getUserProperties().get("pbxAccepted"))
                && !session.getUserProperties().containsKey("pbxCloseReason"))
        {
            // 端点主动关闭（bad_frame 等）已有原因，不覆盖
            session.getUserProperties().put("pbxCloseReason", "error");
        }
    }

    private PbxMediaForkHandler current(Session session)
    {
        Object obj = session.getUserProperties().get("pbxHandler");
        return obj instanceof PbxMediaForkHandler ? (PbxMediaForkHandler) obj : null;
    }

    /**
     * 校验共享密钥：从 query 中取 key 参数与配置值常量时间比较。
     * 配置密钥为空（未设置）时放行（仅限完全隔离内网，配合 IP 白名单）。
     */
    static boolean checkAuthKey(String queryString, String expectedKey)
    {
        if (StringUtils.isEmpty(expectedKey))
        {
            return true;
        }
        String provided = parseQueryKey(queryString, "key");
        if (provided == null)
        {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expectedKey.getBytes(StandardCharsets.UTF_8));
    }

    /** 极简 query 解析（不引入工具类依赖） */
    private static String parseQueryKey(String queryString, String wanted)
    {
        if (StringUtils.isEmpty(queryString))
        {
            return null;
        }
        for (String pair : queryString.split("&"))
        {
            int eq = pair.indexOf('=');
            String k = eq > 0 ? pair.substring(0, eq) : pair;
            if (wanted.equals(k) && eq > 0)
            {
                try
                {
                    return java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                }
                catch (Exception e)
                {
                    return pair.substring(eq + 1);
                }
            }
        }
        return null;
    }

    /** onOpen 拒绝：记拒绝原因（bean 未就绪时尽力取指标）+ 关闭连接 */
    private void recordReject(Session session, String rejectReason,
                              CloseReason.CloseCode code, String closeText)
    {
        try
        {
            HotlineMetrics metrics = SpringUtils.getBean(HotlineMetrics.class);
            metrics.incrementPbxReject(rejectReason);
        }
        catch (Exception ignore)
        {
            // 容器未就绪本身就是一类拒绝原因，指标可能同样不可用
        }
        closeQuietly(session, code, closeText);
    }

    private void closeQuietly(Session session, CloseReason.CloseCode code, String reason)
    {
        try
        {
            session.close(new CloseReason(code, reason));
        }
        catch (IOException | IllegalStateException e)
        {
            // 连接可能已关闭
        }
    }
}
