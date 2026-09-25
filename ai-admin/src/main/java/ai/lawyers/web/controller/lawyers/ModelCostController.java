package ai.lawyers.web.controller.lawyers;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.service.lawyers.stat.IModelCostService;

/**
 * 大模型调用成本与质量看板 Controller（P3-E5）
 *
 * <p>提供概览指标、按日趋势、模型/场景分布、明细分页与 Excel 导出。
 * 明细数据为异步 best-effort 埋点，反映模型实际 Token 费用与调用质量。</p>
 *
 * @author ai-lawyers
 * @date 2026-09-25
 */
@RestController
@RequestMapping("/lawyers/modelCost")
public class ModelCostController extends BaseController
{
    @Autowired
    private IModelCostService modelCostService;

    /** 概览（调用量/成功率/Token/估算费用/平均耗时/单位通话成本） */
    @PreAuthorize("@ss.hasPermi('lawyers:modelCost:view')")
    @GetMapping("/overview")
    public AjaxResult overview(String beginTime, String endTime)
    {
        return success(modelCostService.overview(beginTime, endTime));
    }

    /** 按日趋势 */
    @PreAuthorize("@ss.hasPermi('lawyers:modelCost:view')")
    @GetMapping("/trend")
    public AjaxResult trend(String beginTime, String endTime)
    {
        return success(modelCostService.trend(beginTime, endTime));
    }

    /** 模型分布 */
    @PreAuthorize("@ss.hasPermi('lawyers:modelCost:view')")
    @GetMapping("/byModel")
    public AjaxResult byModel(String beginTime, String endTime)
    {
        return success(modelCostService.byModel(beginTime, endTime));
    }

    /** 调用类型 + 业务场景分布 */
    @PreAuthorize("@ss.hasPermi('lawyers:modelCost:view')")
    @GetMapping("/byScene")
    public AjaxResult byScene(String beginTime, String endTime)
    {
        return success(modelCostService.byScene(beginTime, endTime));
    }

    /** 明细分页 */
    @PreAuthorize("@ss.hasPermi('lawyers:modelCost:view')")
    @GetMapping("/logList")
    public TableDataInfo logList(AiModelCallLog query)
    {
        startPage();
        List<AiModelCallLog> list = modelCostService.logList(query);
        return getDataTable(list);
    }

    /** 明细导出（XLSX，筛选条件同 logList；不限分页） */
    @PreAuthorize("@ss.hasPermi('lawyers:modelCost:export')")
    @PostMapping("/export")
    public void export(AiModelCallLog query, HttpServletResponse response) throws IOException
    {
        List<AiModelCallLog> list = modelCostService.logList(query);

        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        columns.put("callTime", "调用时间");
        columns.put("kind", "调用类型");
        columns.put("scene", "业务场景");
        columns.put("configName", "模型配置");
        columns.put("modelName", "模型名称");
        columns.put("promptTokens", "输入Token");
        columns.put("completionTokens", "输出Token");
        columns.put("totalTokens", "总Token");
        columns.put("costAmount", "估算费用(元)");
        columns.put("elapsedMs", "耗时(毫秒)");
        columns.put("attempts", "尝试次数");
        columns.put("result", "结果");
        columns.put("failReason", "失败原因");
        columns.put("traceId", "链路ID");

        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (AiModelCallLog log : list)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("callTime", log.getCallTime() == null ? ""
                    : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(log.getCallTime()));
            row.put("kind", kindLabel(log.getKind()));
            row.put("scene", log.getScene());
            row.put("configName", log.getConfigName());
            row.put("modelName", log.getModelName());
            row.put("promptTokens", log.getPromptTokens());
            row.put("completionTokens", log.getCompletionTokens());
            row.put("totalTokens", log.getTotalTokens());
            row.put("costAmount", log.getCostAmount());
            row.put("elapsedMs", log.getElapsedMs());
            row.put("attempts", log.getAttempts());
            row.put("result", resultLabel(log.getResult()));
            row.put("failReason", log.getFailReason());
            row.put("traceId", log.getTraceId());
            rows.add(row);
        }
        writeExcel("大模型调用明细", columns, rows, response);
    }

    /** 简单 XLSX 输出：首行表头 + 数据行（Map 按键取值，null 输出空串） */
    private void writeExcel(String sheetName, LinkedHashMap<String, String> columns,
                            List<Map<String, Object>> rows, HttpServletResponse response) throws IOException
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode(sheetName + "_" + System.currentTimeMillis() + ".xlsx", "UTF-8")
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);

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

    private String kindLabel(String kind)
    {
        if ("chat".equals(kind))
        {
            return "对话";
        }
        if ("embed".equals(kind))
        {
            return "向量";
        }
        if ("rerank".equals(kind))
        {
            return "精排";
        }
        return kind == null ? "" : kind;
    }

    private String resultLabel(String result)
    {
        if (AiModelCallLog.RESULT_SUCCESS.equals(result))
        {
            return "成功";
        }
        if (AiModelCallLog.RESULT_FAIL.equals(result))
        {
            return "失败";
        }
        if (AiModelCallLog.RESULT_REJECT.equals(result))
        {
            return "舱壁拒绝";
        }
        return result == null ? "" : result;
    }
}
