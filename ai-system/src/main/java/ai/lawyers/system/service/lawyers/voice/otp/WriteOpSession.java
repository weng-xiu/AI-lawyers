package ai.lawyers.system.service.lawyers.voice.otp;

/**
 * P3-#12：写操作确认会话（纯内存值类，不落库）。
 *
 * <p>callerNumber 仅内存留存用于 OTP 签发/校验，会话结束即随对象丢弃；
 * payloadJson 为白名单动作参数草稿（copilot_actions 模式），未经
 * CONFIRMED 不允许任何落库。</p>
 *
 * @author ai-lawyers
 */
public class WriteOpSession
{
    /** 会话ID（UUID） */
    private final String opId;

    /** 白名单动作 */
    private final WriteOpAction action;

    /** 语音会话ID */
    private final String sessionId;

    /** 服务端锚定话单ID */
    private final Long recordId;

    /** 来电号码（本人，来自服务端锚定话单；仅内存） */
    private final String callerNumber;

    /** 动作参数草稿（JSON，由意图解析生成） */
    private final String payloadJson;

    /** 创建时间（毫秒） */
    private final long createdAt;

    /** 当前状态 */
    private volatile WriteOpState state = WriteOpState.DRAFT;

    /** 已播报的操作摘要话术（armConfirm 时设置） */
    private volatile String summarySpeech;

    /** 核身失败次数（审计用） */
    private volatile int identityAttempts;

    /** 最近活跃时间（毫秒，TTL 依据） */
    private volatile long lastActiveAt;

    public WriteOpSession(String opId, WriteOpAction action, String sessionId, Long recordId,
                          String callerNumber, String payloadJson, long createdAt)
    {
        this.opId = opId;
        this.action = action;
        this.sessionId = sessionId;
        this.recordId = recordId;
        this.callerNumber = callerNumber;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.lastActiveAt = createdAt;
    }

    public String getOpId() { return opId; }

    public WriteOpAction getAction() { return action; }

    public String getSessionId() { return sessionId; }

    public Long getRecordId() { return recordId; }

    public String getCallerNumber() { return callerNumber; }

    public String getPayloadJson() { return payloadJson; }

    public long getCreatedAt() { return createdAt; }

    public WriteOpState getState() { return state; }

    public void setState(WriteOpState state) { this.state = state; }

    public String getSummarySpeech() { return summarySpeech; }

    public void setSummarySpeech(String summarySpeech) { this.summarySpeech = summarySpeech; }

    public int getIdentityAttempts() { return identityAttempts; }

    public void incrementIdentityAttempts() { this.identityAttempts++; }

    public long getLastActiveAt() { return lastActiveAt; }

    public void touch(long nowMillis) { this.lastActiveAt = nowMillis; }
}
