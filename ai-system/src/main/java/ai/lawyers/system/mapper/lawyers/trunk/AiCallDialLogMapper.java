package ai.lawyers.system.mapper.lawyers.trunk;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;

/**
 * 拨号明细日志 Mapper
 */
public interface AiCallDialLogMapper
{
    public AiCallDialLog selectAiCallDialLogByLogId(Long logId);

    public AiCallDialLog selectByCallUuid(String callUuid);

    public List<AiCallDialLog> selectAiCallDialLogList(AiCallDialLog aiCallDialLog);

    public int insertAiCallDialLog(AiCallDialLog aiCallDialLog);

    public int updateAiCallDialLog(AiCallDialLog aiCallDialLog);

    /** 网关下发成功后回写真实的 callUuid */
    public int updateCallUuid(@Param("logId") Long logId, @Param("callUuid") String callUuid);

    public int deleteAiCallDialLogByLogId(Long logId);

    public int deleteAiCallDialLogByLogIds(Long[] logIds);

    /** 按线路聚合指定时间窗内的质量指标（用于生成 ai_trunk_metric 与实时统计） */
    public List<Map<String, Object>> aggregateByTrunk(@Param("beginTime") Date beginTime,
                                                      @Param("endTime") Date endTime);

    /** 按运营商聚合指定时间窗内的质量指标 */
    public List<Map<String, Object>> aggregateByCarrier(@Param("beginTime") Date beginTime,
                                                        @Param("endTime") Date endTime);

    /** 单条线路在时间窗内的接通率 */
    public Map<String, Object> aggregateOneTrunk(@Param("trunkId") Long trunkId,
                                                 @Param("beginTime") Date beginTime,
                                                 @Param("endTime") Date endTime);

    /** 今日总体概览 */
    public Map<String, Object> selectTodayOverview();
}
