package ai.lawyers.system.service.lawyers.stat;

import java.util.Date;
import java.util.Map;

/**
 * B4 运营大屏聚合服务
 */
public interface IDashboardService
{
    /**
     * 大屏一次性聚合数据，按模块键组织返回。
     *
     * @param beginTime 统计开始时间
     * @param endTime   统计结束时间
     */
    Map<String, Object> getDashboardData(Date beginTime, Date endTime);
}
