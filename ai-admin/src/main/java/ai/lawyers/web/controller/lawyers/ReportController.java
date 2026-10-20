package ai.lawyers.web.controller.lawyers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.stat.AdHocStatQuery;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.stat.IAdHocReportService;
import ai.lawyers.system.service.lawyers.stat.IReportService;
import ai.lawyers.system.service.lawyers.stat.StatBackfillManager;

/**
 * 独立多维统计报表 Controller（P3-D6）
 *
 * <p>四类报表：呼叫 / 坐席服务 / 质检 / 业务工单，支持日/周/月维度与 Excel 导出。
 * P0-2 起导出列定义与 POI 输出下沉 {@link IReportService}，本类只做路由。
 * F6 增加自助分析：自定义指标 + 维度选择器 + 下钻话单明细。</p>
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

    @Autowired
    private IAdHocReportService adHocReportService;

    @Autowired
    private IAiCallRecordService aiCallRecordService;

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

    // ---------------------------------------------------------------- F6 报表自助分析

    /** 自助分析目录：指标/维度白名单（供前端选择器渲染） */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/adhoc/catalog")
    public AjaxResult adhocCatalog()
    {
        return success(adHocReportService.catalog());
    }

    /**
     * 自助分析查询：无/单维度走 ai_stat_minute 预聚合，跨维度组合走实时聚合，
     * 结果行数受 stat.adhoc.max-rows 上限保护（capped=true 表示被截断）。
     *
     * @param metrics     指标键，逗号分隔
     * @param dimensions  维度键，逗号分隔（空=仅全局）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/adhoc/query")
    public AjaxResult adhocQuery(String beginTime, String endTime, String granularity,
                                 String metrics, String dimensions)
    {
        AdHocStatQuery query = new AdHocStatQuery();
        query.setBeginTime(beginTime);
        query.setEndTime(endTime);
        query.setGranularity(granularity);
        query.setMetrics(splitCsv(metrics));
        query.setDimensions(splitCsv(dimensions));
        return success(adHocReportService.query(query));
    }

    /**
     * 自助分析下钻：按周期区间 + 维度值查询话单明细分页。
     * 维度参数可组合传入：agentId / categoryId（NONE=未分类）/ lang。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:report:view')")
    @GetMapping("/adhoc/drill")
    public TableDataInfo adhocDrill(String beginTime, String endTime, String agentId,
                                    String categoryId, String lang)
    {
        AiCallRecord cond = new AiCallRecord();
        Map<String, Object> params = cond.getParams();
        params.put("beginCallTime", beginTime);
        params.put("endCallTime", endTime);
        try
        {
            if (StringUtils.isNotEmpty(agentId))
            {
                cond.setAgentId(Long.valueOf(agentId));
            }
            if (StringUtils.isNotEmpty(categoryId))
            {
                if ("NONE".equals(categoryId))
                {
                    params.put("categoryNull", Boolean.TRUE);
                }
                else
                {
                    cond.setCategoryId(Long.valueOf(categoryId));
                }
            }
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("维度值不合法");
        }
        if (StringUtils.isNotEmpty(lang))
        {
            params.put("language", lang);
        }
        startPage();
        List<AiCallRecord> list = aiCallRecordService.selectAiCallRecordList(cond);
        return getDataTable(list);
    }

    private List<String> splitCsv(String csv)
    {
        List<String> out = new ArrayList<>();
        if (StringUtils.isEmpty(csv))
        {
            return out;
        }
        for (String s : csv.split(","))
        {
            if (StringUtils.isNotEmpty(s) && !out.contains(s.trim()))
            {
                out.add(s.trim());
            }
        }
        return out;
    }
}
