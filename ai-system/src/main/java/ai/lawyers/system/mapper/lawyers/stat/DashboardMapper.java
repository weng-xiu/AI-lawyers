package ai.lawyers.system.mapper.lawyers.stat;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * B4 运营大屏聚合查询 Mapper
 *
 * 全部基于现有业务表实时聚合，不新建业务表。
 */
public interface DashboardMapper
{
    /** 今日呼入汇总：总量/接通/未接/转接/平均时长 */
    Map<String, Object> selectCallSummary(@Param("beginTime") Date beginTime, @Param("endTime") Date endTime);

    /** 呼叫趋势（按小时桶）：呼入/接通/未接/转接 */
    List<Map<String, Object>> selectCallTrend(@Param("beginTime") Date beginTime, @Param("endTime") Date endTime);

    /** 咨询分类占比 */
    List<Map<String, Object>> selectCategoryPie(@Param("beginTime") Date beginTime, @Param("endTime") Date endTime);

    /** AI 独立解决 vs 转人工会话数 */
    Map<String, Object> selectAiRatio(@Param("beginTime") Date beginTime, @Param("endTime") Date endTime);

    /** 坐席负载（在线坐席 + 今日通话统计） */
    List<Map<String, Object>> selectAgentLoad();

    /** 当前排队列表 */
    List<Map<String, Object>> selectQueueNow();

    /** 外呼任务进度 */
    List<Map<String, Object>> selectOutboundProgress();

    /** 坐席状态汇总（总数/在线/忙碌） */
    Map<String, Object> selectAgentStatusSummary();

    /** 满意度汇总 */
    Map<String, Object> selectSatisfactionSummary(@Param("beginTime") Date beginTime, @Param("endTime") Date endTime);

    /**
     * T4-3 SLA 汇总（基于 ai_call_queue）：
     * 分配总量 / X 秒内接听量 / 放弃量 / 平均等待 / 平均振铃。
     *
     * @param thresholdSeconds 服务水平阈值秒数（技能组 service_level_threshold，默认 20）
     */
    Map<String, Object> selectSlaSummary(@Param("beginTime") Date beginTime,
                                         @Param("endTime") Date endTime,
                                         @Param("thresholdSeconds") int thresholdSeconds);
}
