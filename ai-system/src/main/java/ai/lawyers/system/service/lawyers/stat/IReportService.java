package ai.lawyers.system.service.lawyers.stat;

import java.util.List;
import java.util.Map;

/**
 * 独立多维统计报表 Service（P3-D6）
 *
 * @author ai-lawyers
 */
public interface IReportService
{
    /** 呼叫报表（按日/周/月聚合） */
    List<Map<String, Object>> callReport(String beginTime, String endTime, String granularity);

    /** 坐席服务报表（按坐席聚合，区间内汇总） */
    List<Map<String, Object>> serviceReport(String beginTime, String endTime);

    /** 质检报表（按日/周/月聚合） */
    List<Map<String, Object>> qualityReport(String beginTime, String endTime, String granularity);

    /** 业务工单报表（按日/周/月聚合） */
    List<Map<String, Object>> businessReport(String beginTime, String endTime, String granularity);
}
