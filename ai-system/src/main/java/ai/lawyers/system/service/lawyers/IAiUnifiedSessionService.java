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
     * 结束会话。
     */
    public int finishSession(String bizType, String bizId, Date endTime);

    /**
     * 聚合跨渠道咨询时间线：按档案ID聚合，档案ID缺失时按手机号聚合；
     * 两者均有时合并去重（档案维度为主，补齐仅有手机号的会话）。
     */
    public List<AiUnifiedSession> timeline(Long profileId, String callerNumber);
}
