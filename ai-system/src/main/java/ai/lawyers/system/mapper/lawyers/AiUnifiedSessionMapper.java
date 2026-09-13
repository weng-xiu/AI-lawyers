package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;

/**
 * 跨渠道统一会话索引 Mapper（F6）
 *
 * @author ai-lawyers
 */
public interface AiUnifiedSessionMapper
{
    /** 写会话索引（uk_biz 去重，重复业务主键不报错） */
    public int insertIgnoreAiUnifiedSession(AiUnifiedSession session);

    /** 更新会话结束时间 */
    public int finishSession(@org.apache.ibatis.annotations.Param("bizType") String bizType,
                             @org.apache.ibatis.annotations.Param("bizId") String bizId,
                             @org.apache.ibatis.annotations.Param("endTime") java.util.Date endTime);

    /** 按档案聚合时间线 */
    public List<AiUnifiedSession> selectByProfileId(Long profileId);

    /** 按手机号聚合时间线（无档案场景兜底） */
    public List<AiUnifiedSession> selectByCallerNumber(String callerNumber);
}
