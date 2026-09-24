package ai.lawyers.web.controller.lawyers;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import ai.lawyers.system.task.StatMinuteScheduleTask;

/**
 * 独立多维统计报表 Controller（P3-D6）
 *
 * <p>四类报表：呼叫 / 坐席服务 / 质检 / 业务工单，支持日/周/月维度与 Excel 导出。</p>
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
    private StatMinuteScheduleTask statMinuteScheduleTask;

    /**
     * 分钟级物化表手工回填（P3-F3）：按分钟循环聚合 [beginTime, endTime) 区间。
     * 区间上限 7 天防误操作长时间占用；幂等覆盖写可重复执行。
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
        if (!begin.isBefore(end))
        {
            return error("beginTime 必须早于 endTime");
        }
        if (begin.isBefore(end.minusDays(7)))
        {
            return error("回填区间不能超过 7 天");
        }
        int minutes = 0;
        int rows = 0;
        for (java.time.LocalDateTime t = begin; t.isBefore(end); t = t.plusMinutes(1))
        {
            rows += statMinuteScheduleTask.aggregateMinute(t);
            minutes++;
        }
        return success("回填完成，共聚合 " + minutes + " 分钟、写入/更新 " + rows + " 条指标");
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

    /** 报表导出（Excel，列定义按报表类型固定） */
    @PreAuthorize("@ss.hasPermi('lawyers:report:export')")
    @PostMapping("/{type}/export")
    public void export(@PathVariable("type") String type, String beginTime, String endTime,
                       String granularity, HttpServletResponse response) throws IOException
    {
        List<Map<String, Object>> rows;
        LinkedHashMap<String, String> columns;
        String sheetName;
        switch (type)
        {
            case "call":
                rows = reportService.callReport(beginTime, endTime, granularity);
                columns = new LinkedHashMap<>();
                columns.put("period", "周期");
                columns.put("totalCalls", "呼叫总量");
                columns.put("answeredCalls", "接通量");
                columns.put("missedCalls", "未接量");
                columns.put("transferredCalls", "转接量");
                columns.put("answerRate", "接通率(%)");
                columns.put("avgDurationSec", "平均通话时长(秒)");
                sheetName = "呼叫报表";
                break;
            case "service":
                rows = reportService.serviceReport(beginTime, endTime);
                columns = new LinkedHashMap<>();
                columns.put("agentName", "坐席");
                columns.put("totalCalls", "接线量");
                columns.put("answeredCalls", "接通量");
                columns.put("answerRate", "接通率(%)");
                columns.put("avgDurationSec", "平均通话时长(秒)");
                columns.put("totalTalkSec", "总通话时长(秒)");
                sheetName = "坐席服务报表";
                break;
            case "quality":
                rows = reportService.qualityReport(beginTime, endTime, granularity);
                columns = new LinkedHashMap<>();
                columns.put("period", "周期");
                columns.put("inspections", "质检量");
                columns.put("avgScore", "平均分");
                columns.put("reviewedCount", "已复核");
                columns.put("pendingCount", "待复核");
                columns.put("riskCount", "关联风险数");
                sheetName = "质检报表";
                break;
            case "business":
                rows = reportService.businessReport(beginTime, endTime, granularity);
                columns = new LinkedHashMap<>();
                columns.put("period", "周期");
                columns.put("totalTickets", "工单量");
                columns.put("closedTickets", "办结量");
                columns.put("closeRate", "办结率(%)");
                columns.put("overdueTickets", "超时量");
                columns.put("avgCloseMinutes", "平均办结时长(分钟)");
                sheetName = "业务工单报表";
                break;
            default:
                throw new IllegalArgumentException("未知报表类型: " + type);
        }
        writeExcel(sheetName, columns, rows, response);
    }

    /** 简单 XLSX 输出：首行表头 + 数据行（Map 按键取值，null 输出空串） */
    private void writeExcel(String sheetName, LinkedHashMap<String, String> columns,
                            List<Map<String, Object>> rows, HttpServletResponse response) throws IOException
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode(sheetName + "_" + System.currentTimeMillis() + ".xlsx", "UTF-8")
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);

        try (XSSFWorkbook wb = new XSSFWorkbook())
        {
            XSSFSheet sheet = wb.createSheet(sheetName);
            XSSFRow header = sheet.createRow(0);
            int col = 0;
            for (String title : columns.values())
            {
                header.createCell(col++).setCellValue(title);
            }
            int rowIdx = 1;
            for (Map<String, Object> rowData : rows)
            {
                XSSFRow row = sheet.createRow(rowIdx++);
                col = 0;
                for (String key : columns.keySet())
                {
                    XSSFCell cell = row.createCell(col++);
                    Object value = rowData.get(key);
                    cell.setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            wb.write(response.getOutputStream());
        }
    }
}
