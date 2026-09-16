package ai.lawyers.system.service.lawyers.stat;

import java.util.Date;
import java.util.Map;

/**
 * F9 工单 SLA 可视化看板聚合服务
 *
 * @author ai-lawyers
 */
public interface ISlaBoardService
{
    /**
     * 聚合 SLA 看板全部模块数据。
     *
     * @param beginTime 建单区间起（统计口径按 create_time）
     * @param endTime   建单区间止
     * @param warnMinutes 临近超时预警窗口（分钟），剩余时限在该窗口内的进行中工单计入预警
     * @return overview/achieveByBizType/achieveByAssignee/overtimeTop/dueSoon/transferStat
     */
    Map<String, Object> getSlaBoard(Date beginTime, Date endTime, Integer warnMinutes);
}
