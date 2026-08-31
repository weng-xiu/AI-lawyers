package ai.lawyers.system.mapper.lawyers.quality;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.quality.AiAgentStatusLog;

/**
 * 坐席状态变更流水 数据层（T4-3）
 *
 * @author ai-lawyers
 */
public interface AiAgentStatusLogMapper
{
    /**
     * 新增一条状态流水
     */
    public int insertAiAgentStatusLog(AiAgentStatusLog log);

    /**
     * 查询坐席最近一条未回填 duration 的流水（按 log_time 倒序取首条）
     */
    public AiAgentStatusLog selectLatestLog(@Param("agentId") Long agentId);

    /**
     * 回填流水持续时长
     */
    public int updateDuration(@Param("logId") Long logId, @Param("duration") Integer duration);

    /**
     * 按时间区间汇总各状态停留秒数（在线/忙碌/休息；通话/话后等）。
     * 返回每行：to_status、to_call_status、duration_sum（秒）
     *
     * @param beginTime 开始（yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束
     */
    public List<Map<String, Object>> sumStatusDuration(@Param("beginTime") String beginTime,
                                                       @Param("endTime") String endTime);
}
