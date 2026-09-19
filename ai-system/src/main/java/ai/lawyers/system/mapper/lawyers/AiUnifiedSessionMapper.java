package ai.lawyers.system.mapper.lawyers;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;

/**
 * 跨渠道统一会话索引 Mapper（F6 + 方案B 会话占用）
 *
 * @author ai-lawyers
 */
public interface AiUnifiedSessionMapper
{
    /** 写会话索引（uk_biz 去重，重复业务主键不报错） */
    public int insertIgnoreAiUnifiedSession(AiUnifiedSession session);

    /**
     * 方案B：以"活跃占用"方式写索引。active_flag=1，携带心跳/兜底过期时间；
     * 同身份同渠道已有活跃会话时触发 uk_active_owner 冲突（由调用方判定拦截）。
     * @return 影响行数；0 表示被唯一键拒绝（已有同渠道活跃会话）
     */
    public int insertActiveSession(AiUnifiedSession session);

    /** 更新会话结束时间（同时置非活跃，释放占用键） */
    public int finishSession(@Param("bizType") String bizType,
                             @Param("bizId") String bizId,
                             @Param("endTime") Date endTime);

    /** 续心跳：更新最近心跳时间（仅活跃会话） */
    public int heartbeat(@Param("bizType") String bizType,
                         @Param("bizId") String bizId,
                         @Param("heartbeat") Date heartbeat);

    /**
     * 查询某身份在某渠道的活跃会话（DB 兜底占用判定）。
     * active_owner 由 profileId/callerNumber/channelType 拼成，与生成列口径一致。
     */
    public AiUnifiedSession selectActiveByOwner(@Param("activeOwner") String activeOwner);

    /** 查某身份当前全部渠道的活跃会话（跨渠道合并提示用）：档案 + 手机号双键合并 */
    public List<AiUnifiedSession> selectActiveByProfileId(@Param("profileId") Long profileId);

    public List<AiUnifiedSession> selectActiveByCallerNumber(@Param("callerNumber") String callerNumber);

    /** 看门狗：扫描已超过兜底过期时间、仍处于活跃态的会话 */
    public List<AiUnifiedSession> selectExpiredActive(@Param("now") Date now,
                                                      @Param("limit") int limit);

    /** 看门狗：按主键强制回收（置非活跃、补结束时间） */
    public int forceFinish(@Param("sessionId") Long sessionId,
                           @Param("endTime") Date endTime);

    /** 按档案聚合时间线 */
    public List<AiUnifiedSession> selectByProfileId(Long profileId);

    /** 按手机号聚合时间线（无档案场景兜底） */
    public List<AiUnifiedSession> selectByCallerNumber(String callerNumber);
}
