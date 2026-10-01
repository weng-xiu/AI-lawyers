package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ai.lawyers.system.service.lawyers.stat.IReportService;
import ai.lawyers.system.task.StatMinuteScheduleTask;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-2：{@link ReportController} 薄层 MockMvc 测试——验证报表查询与导出均正确委托 Service。
 * standalone 模式不加载 Spring Security（权限路径由框架配置侧保证）。
 */
public class ReportControllerTest
{
    @Mock
    private IReportService reportService;

    @Mock
    private StatMinuteScheduleTask statMinuteScheduleTask;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        ReportController controller = new ReportController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "reportService", reportService);
        org.springframework.test.util.ReflectionTestUtils.setField(controller,
                "statMinuteScheduleTask", statMinuteScheduleTask);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void callReport_delegatesToService() throws Exception
    {
        when(reportService.callReport(eq("2026-10-01 00:00:00"), eq("2026-10-01 23:59:59"), eq("day")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/lawyers/report/call")
                        .param("beginTime", "2026-10-01 00:00:00")
                        .param("endTime", "2026-10-01 23:59:59")
                        .param("granularity", "day"))
                .andExpect(status().isOk());

        verify(reportService).callReport("2026-10-01 00:00:00", "2026-10-01 23:59:59", "day");
    }

    @Test
    void export_delegatesToService() throws Exception
    {
        doNothing().when(reportService).exportExcel(
                eq("call"), any(), any(), any(), any(HttpServletResponse.class));

        mockMvc.perform(post("/lawyers/report/call/export")
                        .param("beginTime", "2026-10-01 00:00:00")
                        .param("endTime", "2026-10-01 23:59:59")
                        .param("granularity", "day"))
                .andExpect(status().isOk());

        verify(reportService).exportExcel(eq("call"), eq("2026-10-01 00:00:00"),
                eq("2026-10-01 23:59:59"), eq("day"), any(HttpServletResponse.class));
    }
}
