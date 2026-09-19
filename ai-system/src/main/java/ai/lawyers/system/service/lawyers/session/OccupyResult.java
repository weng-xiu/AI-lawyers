package ai.lawyers.system.service.lawyers.session;

import ai.lawyers.system.domain.lawyers.AiUnifiedSession;

/**
 * 方案B 同渠道占用抢占结果。
 *
 * <p>抢占成功（本渠道此前无活跃会话）：{@link #acquired} 为 true；
 * 抢占失败（同身份同渠道已有活跃会话）：携带既有会话 {@link #existing}，由调用方决定幂等恢复或拒绝。</p>
 *
 * @author ai-lawyers
 */
public class OccupyResult
{
    /** 是否成功取得本渠道占用 */
    private final boolean acquired;

    /** 抢占失败时的既有活跃会话（成功时为本会话本身） */
    private final AiUnifiedSession session;

    /** Redis 是否可用（false 表示本次仅落了 DB 唯一键兜底） */
    private final boolean redisHealthy;

    private OccupyResult(boolean acquired, AiUnifiedSession session, boolean redisHealthy)
    {
        this.acquired = acquired;
        this.session = session;
        this.redisHealthy = redisHealthy;
    }

    public static OccupyResult acquired(AiUnifiedSession session, boolean redisHealthy)
    {
        return new OccupyResult(true, session, redisHealthy);
    }

    public static OccupyResult blocked(AiUnifiedSession existing, boolean redisHealthy)
    {
        return new OccupyResult(false, existing, redisHealthy);
    }

    public boolean isAcquired() { return acquired; }

    public AiUnifiedSession getSession() { return session; }

    public boolean isRedisHealthy() { return redisHealthy; }

    /** 既有活跃会话的业务主键（抢占失败时用于幂等恢复） */
    public String getExistingBizId() { return session == null ? null : session.getBizId(); }
}
