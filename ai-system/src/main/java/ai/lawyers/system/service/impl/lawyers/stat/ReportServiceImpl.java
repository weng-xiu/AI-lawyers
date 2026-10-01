package ai.lawyers.system.service.impl.lawyers.stat;

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
import org.springframework.stereotype.Service;
import ai.lawyers.system.mapper.lawyers.stat.ReportMapper;
import ai.lawyers.system.service.lawyers.stat.IReportService;

/**
 * 独立多维统计报表 Service 实现（P3-D6）
 *
 * <p>全部实时聚合现有业务表；P0-2 起 Excel 列定义与 POI 输出从 Controller 下沉至本类。</p>
 *
 * @author ai-lawyers
 */
@Service
public class ReportServiceImpl implements IReportService
{
    @Autowired
    private ReportMapper reportMapper;

    @Override
    public List<Map<String, Object>> callReport(String beginTime, String endTime, String granularity)
    {
        return reportMapper.selectCallReport(beginTime, endTime, normalizeGranularity(granularity));
    }

    @Override
    public List<Map<String, Object>> serviceReport(String beginTime, String endTime)
    {
        return reportMapper.selectServiceReport(beginTime, endTime);
    }

    @Override
    public List<Map<String, Object>> qualityReport(String beginTime, String endTime, String granularity)
    {
        return reportMapper.selectQualityReport(beginTime, endTime, normalizeGranularity(granularity));
    }

    @Override
    public List<Map<String, Object>> businessReport(String beginTime, String endTime, String granularity)
    {
        return reportMapper.selectBusinessReport(beginTime, endTime, normalizeGranularity(granularity));
    }

    /** 粒度白名单：仅允许 day/week/month，非法值回退 day（防 SQL 片段注入） */
    private String normalizeGranularity(String granularity)
    {
        if ("week".equals(granularity) || "month".equals(granularity))
        {
            return granularity;
        }
        return "day";
    }

    // ============================ P0-2 Excel 导出下沉 ============================

    @Override
    public void exportExcel(String type, String beginTime, String endTime, String granularity,
                            HttpServletResponse response) throws IOException
    {
        List<Map<String, Object>> rows;
        LinkedHashMap<String, String> columns;
        String sheetName;
        switch (type)
        {
            case "call":
                rows = callReport(beginTime, endTime, granularity);
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
                rows = serviceReport(beginTime, endTime);
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
                rows = qualityReport(beginTime, endTime, granularity);
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
                rows = businessReport(beginTime, endTime, granularity);
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
