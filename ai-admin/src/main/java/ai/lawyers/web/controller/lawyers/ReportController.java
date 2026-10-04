package ai.lawyers.web.controller.lawyers;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.stat.IReportService;
import ai.lawyers.system.service.lawyers.stat.StatBackfillManager;

/**
 * 独立多维统计报表 Controller（P3-D6）
 *
 * <p>四类报表：呼叫 / 坐席服务 / 质检 / 业务工单，支持日/周/月维度与 Excel 导出。
 * P0-2 起导出列定义与 POI 输出下沉 {@link IReportService}，本类只做路由。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/report")
public class ReportController extends BaseController
{
    @Autowired
    private IReportService reportService;

    @Autowired
    private StatBackfillManager statBackfillManager;

    /**
     * 分钟级物化表手工回填：异步任务化——校验后提交后台任务，立即返回 taskId，前端轮询进度。
     * 半开区间 [beginTime, endTime)，单区间不超过 7 天；分钟聚合幂等覆盖写，中断可重跑。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:report:export')")
    @PostMapping("/stat/backfill")
    public AjaxResult statBackfill(String beginTime, String endTime)
    {
        if (StringUtils.isEmpty(beginTime) || StringUtils.isEmpty(endTime))
        {
            return error("beginTime/endTime 不能为空（格式 yyyy-MM-dd HH:mm）");
        }
        java.time.LocalDateTime begin = java.time.LocalDateTime.parse(beginTime,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        java.time.LocalDateTime end = java.time.LocalDateTime.parse(endTime,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String taskId = statBackfillManager.submit(begin, end);
        return AjaxResult.success("回填任务已提交").put("taskId", taskId);
    }

    /** 查询分钟物化回填任务进度（RUNNING/SUCCESS/FAILED + 百分比与计数） */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/stat/backfill/progress")
    public AjaxResult statBackfillProgress(String taskId)
    {
        return success(statBackfillManager.getProgress(taskId));
    }

    /** 呼叫报表 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/call")
    public AjaxResult callReport(String beginTime, String endTime, String granularity)
    {
        return success(reportService.callReport(beginTime, endTime, granularity));
    }

    /** 坐席服务报表 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/service")
    public AjaxResult serviceReport(String beginTime, String endTime)
    {
        return success(reportService.serviceReport(beginTime, endTime));
    }

    /** 质检报表 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/quality")
    public AjaxResult qualityReport(String beginTime, String endTime, String granularity)
    {
        return success(reportService.qualityReport(beginTime, endTime, granularity));
    }

    /** 业务工单报表 */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/business")
    public AjaxResult businessReport(String beginTime, String endTime, String granularity)
    {
        return success(reportService.businessReport(beginTime, endTime, granularity));
    }

    /** 报表导出（Excel，列定义与 POI 输出由 Service 承担） */
    @PreAuthorize("@ss.hasPermi('lawyers:report:export')")
    @PostMapping("/{type}/export")
    public void export(@PathVariable("type") String type, String beginTime, String endTime,
                       String granularity, HttpServletResponse response) throws IOException
    {
        reportService.exportExcel(type, beginTime, endTime, granularity, response);
    }
}
