package ai.lawyers.system.service.lawyers.stat;

import java.util.Date;
import java.util.Map;

/**
 * F5 智能回访满意度闭环 —— 满意度归因分析服务
 *
 * @author ai-lawyers
 */
public interface IVisitSatisfactionService
{
    /**
     * 聚合回访满意度分析看板全部模块。
     *
     * @param beginTime 统计区间起（按回访时间/评价时间）
     * @param endTime   统计区间止
     * @return 电话回访总览/趋势/回访员/原因归因/情绪分布 + 图文评价总览/原因 + 任务运营/逾期分级
     */
    Map<String, Object> getBoard(Date beginTime, Date endTime);
}
