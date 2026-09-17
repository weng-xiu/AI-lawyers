package ai.lawyers.system.mapper.lawyers.stat;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * F5 智能回访满意度闭环 —— 满意度归因分析聚合 Mapper
 *
 * <p>聚合三类现有数据源，不新增表、不改在线链路：
 * ① {@code ai_callback} 电话回访满意度（1 非常满意 / 2 满意 / 3 一般 / 4 不满意）；
 * ② {@code ai_user_evaluation} 公众端图文咨询评价（overall/professionalism/responsiveness/quality 1-5 分 + feedback）；
 * ③ {@code ai_return_visit_task} 回访任务（0 待回访 / 1 已完成 / 2 已逾期）。</p>
 *
 * <p>"不满意原因归因"采用规则版（对回访意见/文字反馈做关键词归类），作为 P3 LLM 聚类上线前的
 * 可落地兜底，接口契约对前端保持稳定，后续可平滑替换为模型归因结果。</p>
 *
 * @author ai-lawyers
 */
public interface VisitSatisfactionMapper
{
    /** 电话回访满意度总览（区间口径：已回访且 visit_time 落在区间内） */
    Map<String, Object> selectCallbackOverview(@Param("beginTime") Date beginTime,
                                               @Param("endTime") Date endTime);

    /** 电话回访满意度每日趋势（四档分布 + 日均满意度得分） */
    List<Map<String, Object>> selectCallbackTrend(@Param("beginTime") Date beginTime,
                                                  @Param("endTime") Date endTime);

    /** 回访员维度：回访量、不满意量、平均满意度得分（5-satisfaction） */
    List<Map<String, Object>> selectByVisitor(@Param("beginTime") Date beginTime,
                                              @Param("endTime") Date endTime);

    /**
     * 不满意/一般回访明细（satisfaction in 3,4，含回访意见），用于 Service 层关键词归因。
     * 只取 visit_opinion 非空记录，限制条数防止内存归因过大。
     */
    List<Map<String, Object>> selectNegativeCallbackOpinions(@Param("beginTime") Date beginTime,
                                                             @Param("endTime") Date endTime,
                                                             @Param("limit") int limit);

    /** 图文咨询评价总览：四维均分、好评率（overall≥4 占比）、低分评价数（overall≤2） */
    Map<String, Object> selectEvaluationOverview(@Param("beginTime") Date beginTime,
                                                 @Param("endTime") Date endTime);

    /** 图文咨询低分评价明细（overall≤2，含 feedback），用于关键词归因 */
    List<Map<String, Object>> selectNegativeEvaluationFeedback(@Param("beginTime") Date beginTime,
                                                               @Param("endTime") Date endTime,
                                                               @Param("limit") int limit);

    /** 回访任务运营总览：待回访/已完成/已逾期/高优数量 */
    Map<String, Object> selectTaskOverview();

    /** 逾期未回访任务（status=2），按超期分钟分级，供看板分级监控与逐级升级决策 */
    List<Map<String, Object>> selectOverdueTasks(@Param("now") Date now,
                                                 @Param("limit") int limit);
}
