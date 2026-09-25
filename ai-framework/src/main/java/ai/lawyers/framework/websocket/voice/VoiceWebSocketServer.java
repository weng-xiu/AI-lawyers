package ai.lawyers.framework.websocket.voice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
import ai.lawyers.common.utils.spring.SpringUtils;
import ai.lawyers.framework.websocket.WebSocketAuthGuard;

/**
 * 流式语音 WebSocket 端点（P3-A1）。
 *
 * <p>路径：{@code /ws/voice/{sessionId}/{role}}，role 取值 {@code agent}(坐席)/{@code caller}(来电者)。
 * 沿用第五部分 5.5 与第十四部分 4.1 定义的 JSON 帧协议：</p>
 * <pre>
 * 客户端→服务端：start / audio(base64 PCM) / tts / bargein / stop；另支持二进制 PCM 直传
 * 服务端→客户端：connected / started / asr_partial / asr_final / tts_audio / tts_end / error
 * </pre>
 *
 * <p>工程约束：Java 8 + javax.websocket（不引入 WebFlux）；握手期 JWT 鉴权复用
 * {@link WebSocketAuthGuard}（query {@code ?token=}，非法/缺失令牌以 1008 关闭）；
 * 保序发送与引擎句柄释放委托 {@link VoiceSession}，{@code @OnClose} 不遗留线程。
 * 当前引擎仅 mock（A2/A3 接 DashScope 等真实 Provider；请求未接入引擎时显式回 error）。</p>
 *
 * @author ai-lawyers
 */
@Component
@ServerEndpoint("/ws/voice/{sessionId}/{role}")
public class VoiceWebSocketServer
{
    private static final Logger log = LoggerFactory.getLogger(VoiceWebSocketServer.class);

    /** 允许的说话方角色（坐席 / 来电者） */
    private static final Set<String> ALLOWED_ROLES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("agent", "caller")));

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") String sessionId,
            @PathParam("role") String role)
    {
        if (sessionId == null || sessionId.trim().isEmpty())
        {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "missing sessionId");
            return;
        }
        if (role == null || !ALLOWED_ROLES.contains(role))
        {
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "role must be agent/caller");
            return;
        }
        WebSocketAuthGuard authGuard;
        VoiceSessionManager manager;
        try
        {
            authGuard = SpringUtils.getBean(WebSocketAuthGuard.class);
            manager = SpringUtils.getBean(VoiceSessionManager.class);
        }
        catch (Exception e)
        {
            log.warn("VoiceWS 容器未就绪，拒绝连接 connId={}: {}", session.getId(), e.getMessage());
            closeQuietly(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "service unavailable");
            return;
        }
        if (!authGuard.authorizeVoice(session.getQueryString()))
        {
            log.warn("VoiceWS 拒绝未授权连接 sessionId={}, role={}, connId={}",
                    sessionId, role, session.getId());
            closeQuietly(session, CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized");
            return;
        }
        VoiceSession voiceSession = manager.register(session, sessionId, role);
        if (voiceSession == null)
        {
            // 功能关闭或超容量
            closeQuietly(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "voice disabled or capacity exceeded");
            return;
        }
        session.getUserProperties().put("voiceSession", voiceSession);
    }

    @OnMessage
    public void onTextMessage(String message, Session session)
    {
        VoiceSession voiceSession = current(session);
        if (voiceSession == null)
        {
            return;
        }
        voiceSession.handleText(message);
    }

    /** 二进制音频帧：整帧即 PCM（S16LE 单声道，采样率以 start 帧为准），直喂 ASR 省一次 base64 */
    @OnMessage
    public void onBinaryMessage(ByteBuffer buffer, Session session)
    {
        VoiceSession voiceSession = current(session);
        if (voiceSession == null || buffer == null)
        {
            return;
        }
        byte[] pcm = new byte[buffer.remaining()];
        buffer.get(pcm);
        voiceSession.handleBinary(pcm);
    }

    @OnClose
    public void onClose(Session session)
    {
        try
        {
            VoiceSessionManager manager = SpringUtils.getBean(VoiceSessionManager.class);
            manager.unregister(session.getId());
        }
        catch (Exception e)
        {
            // 容器关闭时序中 bean 可能已销毁：直接兜底关闭会话
            Object obj = session.getUserProperties().get("voiceSession");
            if (obj instanceof VoiceSession)
            {
                ((VoiceSession) obj).shutdown();
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error)
    {
        log.error("VoiceWS error: connId={}, msg={}",
                session == null ? "" : session.getId(),
                WebSocketAuthGuard.maskQueryToken(error.getMessage()));
    }

    private VoiceSession current(Session session)
    {
        Object obj = session.getUserProperties().get("voiceSession");
        return obj instanceof VoiceSession ? (VoiceSession) obj : null;
    }

    /** 鉴权/容量失败以关闭码拒绝，异常不外抛 */
    private void closeQuietly(Session session, CloseReason.CloseCode code, String reason)
    {
        try
        {
            session.close(new CloseReason(code, reason));
        }
        catch (IOException | IllegalStateException e)
        {
            // 连接可能已关闭，忽略
        }
    }
}
