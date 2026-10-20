package ai.lawyers.system.mapper.lawyers.stat;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 报表自助分析 Mapper（F6）
 *
 * <p>指标键/维度键/粒度格式均为 Service 白名单常量后经 #{} 传入，
 * 无任何 ${} 拼接用户输入（别名 m0..mN 的序号来自 foreach index，非用户输入）。</p>
 *
 * @author ai-lawyers
 */
public interface AdHocStatMapper
{
    /**
     * ai_stat_minute 聚合：按周期（+可选维度值）透视请求指标。
     *
     * @param dimensionPrefix 维度前缀（如 "agent:"，null=仅全局 ALL 行）
     * @param limitRows       结果行上限（Service 传 maxRows+1 以探测截断）
     * @return 行键：period / dim_value / m0..mN（与 metricKeys 下标对应）
     */
    List<Map<String, Object>> selectAdHocAgg(@Param("beginTime") String beginTime,
                                             @Param("endTime") String endTime,
                                             @Param("periodFmt") String periodFmt,
                                             @Param("metricKeys") List<String> metricKeys,
                                             @Param("dimensionPrefix") String dimensionPrefix,
                                             @Param("limitRows") int limitRows);

    /**
     * 跨维度组合实时聚合（ai_call_record，agent/category/lang 同源维度）。
     *
     * @return 行键：period / dim_agent / dim_agent_name / dim_category / dim_category_name /
     *         dim_lang / call_total / call_answered / call_missed / call_transferred / answered_duration_sum
     */
    List<Map<String, Object>> selectCallCrossDim(@Param("beginTime") String beginTime,
                                                 @Param("endTime") String endTime,
                                                 @Param("periodFmt") String periodFmt,
                                                 @Param("dimKeys") List<String> dimKeys,
                                                 @Param("limitRows") int limitRows);

    /** 坐席 id → 名称（用于维度值标签） */
    List<Map<String, Object>> selectAgentNames(@Param("agentIds") List<Long> agentIds);

    /** 分类 id → 名称（用于维度值标签） */
    List<Map<String, Object>> selectCategoryNames(@Param("categoryIds") List<Long> categoryIds);
}
