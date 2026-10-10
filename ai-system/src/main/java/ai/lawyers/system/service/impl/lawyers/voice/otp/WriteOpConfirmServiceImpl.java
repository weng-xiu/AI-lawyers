package ai.lawyers.system.service.impl.lawyers.voice.otp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService.OtpIssueResult;
import ai.lawyers.system.service.lawyers.voice.otp.IOtpService.OtpVerifyStatus;
import ai.lawyers.system.service.lawyers.voice.otp.IWriteOpConfirmService;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpState;

/**
 * P3-#12：机器人写操作通话内确认状态机实现。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>存储：内存 ConcurrentHashMap（opId → 会话）+ 号码索引（盲索引 → opId，
 *       同号同时仅一个活跃会话，防轰炸）；终态统一经 {@link #finish} 清理索引；</li>
 *   <li>TTL：会话级懒过期（{@code ai.voice.confirm.session-ttl-seconds}，默认 600s），
 *       任何读写前先 {@link #expireIfNeeded}；</li>
 *   <li>转换守卫：check-then-act 在 {@code synchronized(session)} 内完成，非法前置状态
 *       一律返回失败（不外抛、不转换）；</li>
 *   <li>核身复用 {@link IOtpService}；连续错码达上限（TOO_MANY_ATTEMPTS）直接终态 REJECTED。</li>
 * </ul>
 *
 * <p>本服务只做状态机与核身编排，不落库、不执行业务动作；
 * {@code complete} 由业务执行器（后续迭代）落库成功后回执调用。</p>
 *
 * @author ai-lawyers
 */
@Service
public class WriteOpConfirmServiceImpl implements IWriteOpConfirmService
{
    private static final Logger log = LoggerFactory.getLogger(WriteOpConfirmServiceImpl.class);

    /** 会话容量上限（防内存膨胀） */
    private static final int MAX_SESSIONS = 2000;

    @Autowired(required = false)
    private IOtpService otpService;

    /** 会话 TTL（秒） */
    @Value("${ai.voice.confirm.session-ttl-seconds:600}")
    private int sessionTtlSeconds = 600;

    /**
     * 时钟覆写（毫秒；&gt;=0 时替代系统时钟，仅测试用）。
     */
    long nowOverride = -1;

    /** 会话存储：opId -> 会话 */
    private final Map<String, WriteOpSession> sessions = new ConcurrentHashMap<>();

    /** 号码索引：盲索引(callerNumber) -> opId（仅活跃会话） */
    private final Map<String, String> activeByPhone = new ConcurrentHashMap<>();

    @Override
    public BeginResult begin(WriteOpAction action, String sessionId, Long recordId,
                             String callerNumber, String payloadJson)
    {
        try
        {
            if (action == null)
            {
                return BeginResult.fail("写操作不在白名单内，已拒绝");
            }
            if (callerNumber == null || callerNumber.trim().isEmpty())
            {
                return BeginResult.fail("来电号码缺失（须来自服务端锚定话单），已拒绝");
            }
            long now = now();
            String phoneKey = PiiCryptoUtils.blindIndex(callerNumber.trim());
            String existingOpId = activeByPhone.get(phoneKey);
            if (existingOpId != null)
            {
                WriteOpSession existing = sessions.get(existingOpId);
                if (existing != null && !existing.getState().isTerminal() && !expired(existing, now))
                {
                    return BeginResult.fail("该号码已有进行中的确认会话，请先完成或取消");
                }
                activeByPhone.remove(phoneKey, existingOpId);
            }
            if (sessions.size() >= MAX_SESSIONS)
            {
                sessions.entrySet().removeIf(e -> e.getValue().getState().isTerminal()
                        || expired(e.getValue(), now));
                if (sessions.size() >= MAX_SESSIONS)
                {
                    return BeginResult.fail("确认会话数已达上限，请稍后再试");
                }
            }
            String opId = UUID.randomUUID().toString();
            WriteOpSession session =
                    new WriteOpSession(opId, action, sessionId, recordId, callerNumber.trim(), payloadJson, now);
            sessions.put(opId, session);
            activeByPhone.put(phoneKey, opId);
            log.info("写操作确认会话创建 op={} action={} session={} recordId={}",
                    shortId(opId), action.getCode(), sessionId, recordId);
            return BeginResult.ok(opId);
        }
        catch (Exception e)
        {
            log.warn("写操作确认会话创建异常: {}", e.getMessage());
            return BeginResult.fail("会话创建异常，请稍后再试");
        }
    }

    @Override
    public TransitionResult requestOtp(String opId)
    {
        WriteOpSession session = sessions.get(opId);
        if (session == null)
        {
            return TransitionResult.fail("会话不存在", WriteOpState.EXPIRED);
        }
        synchronized (session)
        {
            expireIfNeeded(session);
            if (session.getState() != WriteOpState.DRAFT)
            {
                return TransitionResult.fail("当前状态不允许发送核身码", session.getState());
            }
            if (otpService == null)
            {
                return TransitionResult.fail("核身服务未就绪", session.getState());
            }
            OtpIssueResult r = otpService.issue(session.getCallerNumber(),
                    session.getSessionId(), session.getRecordId());
            if (!r.isSuccess())
            {
                return TransitionResult.fail(r.getMessage(), session.getState());
            }
            session.setState(WriteOpState.OTP_SENT);
            session.touch(now());
            return TransitionResult.ok(session.getState());
        }
    }

    @Override
    public TransitionResult verifyIdentity(String opId, String otpCode)
    {
        WriteOpSession session = sessions.get(opId);
        if (session == null)
        {
            return TransitionResult.fail("会话不存在", WriteOpState.EXPIRED);
        }
        synchronized (session)
        {
            expireIfNeeded(session);
            if (session.getState() != WriteOpState.OTP_SENT)
            {
                return TransitionResult.fail("当前状态不允许核身", session.getState());
            }
            if (otpService == null)
            {
                return TransitionResult.fail("核身服务未就绪", session.getState());
            }
            OtpVerifyStatus status = otpService.verify(session.getCallerNumber(), otpCode);
            session.incrementIdentityAttempts();
            if (status == OtpVerifyStatus.VERIFIED)
            {
                session.setState(WriteOpState.VERIFIED);
                session.touch(now());
                log.info("写操作核身通过 op={} action={}", shortId(opId), session.getAction().getCode());
                return TransitionResult.ok(session.getState());
            }
            if (status == OtpVerifyStatus.TOO_MANY_ATTEMPTS)
            {
                // 会话转 REJECTED 终态并收口；对调用方返回失败（核身未通过，防误读为核身成功）
                finish(session, WriteOpState.REJECTED, "核身错误次数过多，会话终止");
                return TransitionResult.fail(status.getLabel(), WriteOpState.REJECTED);
            }
            return TransitionResult.fail(status.getLabel(), session.getState());
        }
    }

    @Override
    public TransitionResult armConfirm(String opId, String summarySpeech)
    {
        WriteOpSession session = sessions.get(opId);
        if (session == null)
        {
            return TransitionResult.fail("会话不存在", WriteOpState.EXPIRED);
        }
        synchronized (session)
        {
            expireIfNeeded(session);
            if (session.getState() != WriteOpState.VERIFIED)
            {
                return TransitionResult.fail("当前状态不允许进入确认环节", session.getState());
            }
            if (summarySpeech == null || summarySpeech.trim().isEmpty())
            {
                return TransitionResult.fail("操作摘要话术为空", session.getState());
            }
            session.setSummarySpeech(summarySpeech.trim());
            session.setState(WriteOpState.AWAITING_CONFIRM);
            session.touch(now());
            return TransitionResult.ok(session.getState());
        }
    }

    @Override
    public TransitionResult confirm(String opId)
    {
        WriteOpSession session = sessions.get(opId);
        if (session == null)
        {
            return TransitionResult.fail("会话不存在", WriteOpState.EXPIRED);
        }
        synchronized (session)
        {
            expireIfNeeded(session);
            if (session.getState() != WriteOpState.AWAITING_CONFIRM)
            {
                return TransitionResult.fail("当前状态不允许确认", session.getState());
            }
            session.setState(WriteOpState.CONFIRMED);
            session.touch(now());
            log.info("写操作用户已确认 op={} action={}", shortId(opId), session.getAction().getCode());
            return TransitionResult.ok(session.getState());
        }
    }

    @Override
    public TransitionResult complete(String opId)
    {
        WriteOpSession session = sessions.get(opId);
        if (session == null)
        {
            return TransitionResult.fail("会话不存在", WriteOpState.EXPIRED);
        }
        synchronized (session)
        {
            expireIfNeeded(session);
            if (session.getState() != WriteOpState.CONFIRMED)
            {
                return TransitionResult.fail("当前状态不允许回执完成", session.getState());
            }
            return finish(session, WriteOpState.EXECUTED, "写操作已执行");
        }
    }

    @Override
    public TransitionResult reject(String opId)
    {
        WriteOpSession session = sessions.get(opId);
        if (session == null)
        {
            return TransitionResult.fail("会话不存在", WriteOpState.EXPIRED);
        }
        synchronized (session)
        {
            expireIfNeeded(session);
            if (session.getState().isTerminal())
            {
                return TransitionResult.fail("会话已结束", session.getState());
            }
            return finish(session, WriteOpState.REJECTED, "用户已取消");
        }
    }

    @Override
    public WriteOpSession get(String opId)
    {
        WriteOpSession session = sessions.get(opId);
        if (session == null)
        {
            return null;
        }
        synchronized (session)
        {
            expireIfNeeded(session);
            return session;
        }
    }

    /** 终态收口：置状态 + 清理号码索引 */
    private TransitionResult finish(WriteOpSession session, WriteOpState target, String message)
    {
        session.setState(target);
        sessions.remove(session.getOpId());
        String phoneKey = PiiCryptoUtils.blindIndex(session.getCallerNumber());
        activeByPhone.remove(phoneKey, session.getOpId());
        log.info("写操作确认会话结束 op={} state={}", shortId(session.getOpId()), target);
        return TransitionResult.ok(target);
    }

    /** TTL 懒过期（须在持锁内调用） */
    private void expireIfNeeded(WriteOpSession session)
    {
        if (expired(session, now()))
        {
            session.setState(WriteOpState.EXPIRED);
            sessions.remove(session.getOpId());
            String phoneKey = PiiCryptoUtils.blindIndex(session.getCallerNumber());
            activeByPhone.remove(phoneKey, session.getOpId());
        }
    }

    private boolean expired(WriteOpSession session, long now)
    {
        return now > session.getLastActiveAt() + sessionTtlSeconds * 1000L;
    }

    private long now()
    {
        return nowOverride >= 0 ? nowOverride : System.currentTimeMillis();
    }

    private static String shortId(String opId)
    {
        return opId == null ? "null" : opId.substring(0, 8);
    }
}
