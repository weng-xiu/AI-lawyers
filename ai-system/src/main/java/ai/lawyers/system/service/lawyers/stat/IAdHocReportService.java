package ai.lawyers.system.service.lawyers.stat;

import java.util.Map;
import ai.lawyers.system.domain.lawyers.stat.AdHocStatQuery;

/**
 * 报表自助分析 Service（F6）
 *
 * <p>在 ai_stat_minute（30 指标×5 维度）与 4 类固定报表之上提供
 * 「自定义指标 + 维度选择器 + 下钻到话单明细」能力：</p>
 * <ul>
 *   <li>无维度 / 单维度：走分钟预聚合表 SQL 聚合（快）；</li>
 *   <li>跨维度组合（≥2 个）：走源业务表实时聚合，结果行数受上限保护；</li>
 *   <li>所有指标/维度键经 {@link AdHocStatCatalog} 白名单校验。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public interface IAdHocReportService
{
    /**
     * 指标/维度目录（供前端选择器渲染）。
     *
     * @return metrics（含分组标签）、dimensions（含各自可用指标与是否支持跨维度）、crossMetrics
     */
    Map<String, Object> catalog();

    /**
     * 自助分析查询。
     *
     * @return columns（动态列定义）、rows（含 _drill 下钻参数）、labels（维度值→名称映射）、
     *         capped（是否被结果上限截断）、rowCount、source（preagg/realtime）、maxRows
     */
    Map<String, Object> query(AdHocStatQuery query);
}
