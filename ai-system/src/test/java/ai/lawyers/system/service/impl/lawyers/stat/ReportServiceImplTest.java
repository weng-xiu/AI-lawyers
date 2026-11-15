package ai.lawyers.system.service.impl.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.mapper.lawyers.stat.ReportMapper;

/**
 * 多维统计报表服务单测。
 *
 * <p>覆盖预聚合开关切换、粒度白名单回退，以及四类报表 Excel 导出的表头/内容与未知类型异常。</p>
 *
 * @author ai-lawyers
 */
class ReportServiceImplTest
{
    private ReportServiceImpl service;

    @Mock private ReportMapper reportMapper;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception
    {
        mocks = MockitoAnnotations.openMocks(this);
        service = new ReportServiceImpl();
        ReflectionTestUtils.setField(service, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "preaggEnabled", true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        mocks.close();
    }

    @Test
    void callReport_preaggEnabled_usesPreaggAndNormalizesGranularity()
    {
        List<Map<String, Object>> rows = Collections.singletonList(Collections.emptyMap());
        when(reportMapper.selectCallReportPreagg("a", "b", "week")).thenReturn(rows);

        assertEquals(rows, service.callReport("a", "b", "week"));
    }

    @Test
    void callReport_invalidGranularity_fallsBackToDay()
    {
        List<Map<String, Object>> rows = Collections.singletonList(Collections.emptyMap());
        when(reportMapper.selectCallReportPreagg("a", "b", "day")).thenReturn(rows);

        assertEquals(rows, service.callReport("a", "b", "hour;drop"));
        assertEquals(rows, service.callReport("a", "b", null));
    }

    @Test
    void callReport_preaggDisabled_usesRealtime()
    {
        ReflectionTestUtils.setField(service, "preaggEnabled", false);
        List<Map<String, Object>> rows = Collections.singletonList(Collections.emptyMap());
        when(reportMapper.selectCallReport("a", "b", "month")).thenReturn(rows);

        assertEquals(rows, service.callReport("a", "b", "month"));
    }

    @Test
    void serviceReport_preaggSwitches()
    {
        List<Map<String, Object>> rows = Collections.singletonList(Collections.emptyMap());
        when(reportMapper.selectServiceReportPreagg("a", "b")).thenReturn(rows);

        assertEquals(rows, service.serviceReport("a", "b"));

        ReflectionTestUtils.setField(service, "preaggEnabled", false);
        when(reportMapper.selectServiceReport("a", "b")).thenReturn(rows);
        assertEquals(rows, service.serviceReport("a", "b"));
    }

    @Test
    void qualityReport_preaggSwitches()
    {
        List<Map<String, Object>> rows = Collections.singletonList(Collections.emptyMap());
        when(reportMapper.selectQualityReportPreagg("a", "b", "day")).thenReturn(rows);
        assertEquals(rows, service.qualityReport("a", "b", ""));

        ReflectionTestUtils.setField(service, "preaggEnabled", false);
        when(reportMapper.selectQualityReport("a", "b", "day")).thenReturn(rows);
        assertEquals(rows, service.qualityReport("a", "b", ""));
    }

    @Test
    void businessReport_preaggSwitches()
    {
        List<Map<String, Object>> rows = Collections.singletonList(Collections.emptyMap());
        when(reportMapper.selectBusinessReportPreagg("a", "b", "week")).thenReturn(rows);
        assertEquals(rows, service.businessReport("a", "b", "week"));

        ReflectionTestUtils.setField(service, "preaggEnabled", false);
        when(reportMapper.selectBusinessReport("a", "b", "week")).thenReturn(rows);
        assertEquals(rows, service.businessReport("a", "b", "week"));
    }

    @Test
    void exportExcel_unknownType_throws()
    {
        MockHttpServletResponse response = new MockHttpServletResponse();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.exportExcel("unknown", "a", "b", "day", response));
        assertTrue(ex.getMessage().contains("未知报表类型"));
    }

    @Test
    void exportExcel_call_writesHeaderAndRows() throws Exception
    {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("period", "2026-01-01");
        row.put("totalCalls", 10);
        row.put("answeredCalls", 8);
        row.put("missedCalls", 2);
        row.put("transferredCalls", 1);
        row.put("answerRate", 80.0);
        row.put("avgDurationSec", 60);
        when(reportMapper.selectCallReportPreagg(eq("a"), eq("b"), eq("day")))
            .thenReturn(Collections.singletonList(row));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportExcel("call", "a", "b", "day", response);

        assertExcel(response, "呼叫报表", "周期", "2026-01-01");
    }

    @Test
    void exportExcel_service_writesHeaderAndRows() throws Exception
    {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("agentName", "小王");
        row.put("totalCalls", 5);
        when(reportMapper.selectServiceReportPreagg("a", "b")).thenReturn(Collections.singletonList(row));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportExcel("service", "a", "b", null, response);

        assertExcel(response, "坐席服务报表", "坐席", "小王");
    }

    @Test
    void exportExcel_quality_writesHeaderAndRows() throws Exception
    {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("period", "2026-01");
        row.put("inspections", 3);
        row.put("avgScore", 90.5);
        when(reportMapper.selectQualityReportPreagg("a", "b", "month"))
            .thenReturn(Collections.singletonList(row));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportExcel("quality", "a", "b", "month", response);

        assertExcel(response, "质检报表", "周期", "2026-01");
    }

    @Test
    void exportExcel_business_writesHeaderAndRows_nullCellsBecomeEmpty() throws Exception
    {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("period", "2026-01");
        row.put("totalTickets", null);
        when(reportMapper.selectBusinessReportPreagg("a", "b", "day"))
            .thenReturn(Collections.singletonList(row));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportExcel("business", "a", "b", "day", response);

        assertExcel(response, "业务工单报表", "周期", "2026-01");
    }

    private void assertExcel(MockHttpServletResponse response, String sheetName,
                             String header, String cellValue) throws Exception
    {
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains(".xlsx"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray())))
        {
            assertEquals(sheetName, wb.getSheetName(0));
            assertEquals(header, wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals(cellValue, wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
    }
}
