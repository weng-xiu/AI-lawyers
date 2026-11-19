package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.service.lawyers.stat.IModelCostService;

/**
 * 大模型调用成本看板接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class ModelCostControllerTest
{
    @Mock
    private IModelCostService modelCostService;

    @InjectMocks
    private ModelCostController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiModelCallLog log(String kind, String result)
    {
        AiModelCallLog log = new AiModelCallLog();
        log.setCallTime(new Date(1700000000000L));
        log.setKind(kind);
        log.setScene("rag");
        log.setConfigName("默认配置");
        log.setModelName("doubao-pro");
        log.setPromptTokens(100);
        log.setCompletionTokens(20);
        log.setTotalTokens(120);
        log.setCostAmount(new BigDecimal("0.0123"));
        log.setElapsedMs(850);
        log.setAttempts(1);
        log.setResult(result);
        log.setTraceId("trace-1");
        return log;
    }

    @Test
    void 概览趋势与分布透传()
    {
        Map<String, Object> overview = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> trend = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> byModel = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> byScene = new ArrayList<Map<String, Object>>();
        when(modelCostService.overview("2026-11-01", "2026-11-20")).thenReturn(overview);
        when(modelCostService.trend("2026-11-01", "2026-11-20")).thenReturn(trend);
        when(modelCostService.byModel("2026-11-01", "2026-11-20")).thenReturn(byModel);
        when(modelCostService.byScene("2026-11-01", "2026-11-20")).thenReturn(byScene);

        assertSame(overview, controller.overview("2026-11-01", "2026-11-20").get(AjaxResult.DATA_TAG));
        assertSame(trend, controller.trend("2026-11-01", "2026-11-20").get(AjaxResult.DATA_TAG));
        assertSame(byModel, controller.byModel("2026-11-01", "2026-11-20").get(AjaxResult.DATA_TAG));
        assertSame(byScene, controller.byScene("2026-11-01", "2026-11-20").get(AjaxResult.DATA_TAG));
    }

    @Test
    void 明细分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiModelCallLog query = new AiModelCallLog();
        List<AiModelCallLog> list = new ArrayList<AiModelCallLog>();
        list.add(log("chat", AiModelCallLog.RESULT_SUCCESS));
        when(modelCostService.logList(query)).thenReturn(list);

        TableDataInfo table = controller.logList(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 导出Excel写入中文表头与枚举标签() throws Exception
    {
        List<AiModelCallLog> list = new ArrayList<AiModelCallLog>();
        list.add(log("chat", AiModelCallLog.RESULT_SUCCESS));
        list.add(log("embed", AiModelCallLog.RESULT_FAIL));
        list.add(log("rerank", AiModelCallLog.RESULT_REJECT));
        AiModelCallLog empty = log(null, null);
        empty.setCallTime(null);
        empty.setCostAmount(null);
        list.add(empty);
        when(modelCostService.logList(any(AiModelCallLog.class))).thenReturn(list);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(new AiModelCallLog(), response);

        assertTrue(response.getContentType().contains("spreadsheetml"));
        byte[] content = response.getContentAsByteArray();
        assertTrue(content.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content)))
        {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals("调用时间", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("结果", sheet.getRow(0).getCell(11).getStringCellValue());
            assertEquals("对话", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("向量", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals("精排", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals("成功", sheet.getRow(1).getCell(11).getStringCellValue());
            assertEquals("失败", sheet.getRow(2).getCell(11).getStringCellValue());
            assertEquals("舱壁拒绝", sheet.getRow(3).getCell(11).getStringCellValue());
            assertEquals("", sheet.getRow(4).getCell(11).getStringCellValue());
            assertEquals("", sheet.getRow(4).getCell(0).getStringCellValue());
        }
    }
}
