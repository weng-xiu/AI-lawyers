package ai.lawyers.system.service.lawyers;

import java.util.Date;
import java.util.List;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;

/**
 * 跨渠道统一会话索引 Service（F6）
 *
 * @author ai-lawyers
 */
public interface IAiUnifiedSessionService
{
    /**
     * 写会话索引（同一 bizType+bizId 幂等）。
     */
    public int recordSession(AiUnifiedSession session);

    /**
     * 结束会话（置非活跃、补结束时间，释放同渠道占用）。
     */
    public int finishSession(String bizType, String bizId, Date endTime);

    /**
     * 方案B：开启一条"活跃占用"会话（active_flag=1）。
     *
     * @return 占用结果；同身份同渠道已有活跃会话时返回 blocked（携带既有会话），不抛异常
     */
    public ai.lawyers.system.service.lawyers.session.OccupyResult startActive(AiUnifiedSession session,
                                                                              int ttlSeconds);

    /**
     * 续心跳（Redis TTL + DB last_heartbeat）。
     */
    public void heartbeat(String bizType, String bizId);

    /**
     * 查询某身份当前所有渠道的活跃会话（跨渠道合并提示），档案ID + 手机号双键合并。
     */
    public List<AiUnifiedSession> listActive(Long profileId, String callerNumber);

    /**
     * 看门狗：强制回收超过兜底过期时间的残留活跃会话，返回回收条数。
     */
    public int reclaimExpired();

    /**
     * 聚合跨渠道咨询时间线：按档案ID聚合，档案ID缺失时按手机号聚合；
     * 两者均有时合并去重（档案维度为主，补齐仅有手机号的会话）。
     */
    public List<AiUnifiedSession> timeline(Long profileId, String callerNumber);
}
