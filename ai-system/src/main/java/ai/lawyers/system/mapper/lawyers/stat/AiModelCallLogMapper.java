package ai.lawyers.system.mapper.lawyers.stat;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;

/**
 * 大模型调用明细日志 Mapper（P3-E5）
 *
 * @author ai-lawyers
 * @date 2026-09-25
 */
public interface AiModelCallLogMapper
{
    /** 插入一条调用日志（异步 best-effort 调用） */
    int insertAiModelCallLog(AiModelCallLog log);

    /** 明细查询（支持 kind/scene/modelName/result 与时间区间，分页由 PageHelper 拦截） */
    List<AiModelCallLog> selectLogList(AiModelCallLog query);

    /** 区间总体聚合：调用量/成功/失败/拒绝/重试次数/Token/费用/耗时 */
    Map<String, Object> selectOverview(@Param("beginTime") String beginTime,
                                      @Param("endTime") String endTime);

    /** 区间内话单总量（单位通话成本分母，口径与大屏一致 ai_call_record.call_time） */
    long selectCallCount(@Param("beginTime") String beginTime,
                         @Param("endTime") String endTime);

    /** 按日趋势：调用量/各类结果/Token/费用/平均耗时 */
    List<Map<String, Object>> selectTrend(@Param("beginTime") String beginTime,
                                          @Param("endTime") String endTime);

    /** 按模型聚合 */
    List<Map<String, Object>> selectByModel(@Param("beginTime") String beginTime,
                                            @Param("endTime") String endTime);

    /** 按调用类型+业务场景聚合 */
    List<Map<String, Object>> selectByScene(@Param("beginTime") String beginTime,
                                            @Param("endTime") String endTime);
}
