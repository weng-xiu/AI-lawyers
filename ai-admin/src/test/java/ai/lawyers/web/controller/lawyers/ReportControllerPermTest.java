package ai.lawyers.web.controller.lawyers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ai.lawyers.system.service.lawyers.stat.IReportService;
import ai.lawyers.system.service.lawyers.stat.StatBackfillManager;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P0-2：{@link ReportController} 权限校验——报表查询需 lawyers:report:view，
 * 导出与回填提交需 lawyers:report:export；未登录 401、权限不足 403、持权放行并委托 Service。
 */
public class ReportControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IReportService reportService;

    @Mock
    private StatBackfillManager statBackfillManager;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(ReportController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("reportService", reportService);
        registerMock("statBackfillManager", statBackfillManager);
    }

    @Test
    void callReport_unauthenticated_401() throws Exception
    {
        mockMvc.perform(get("/lawyers/report/call"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callReport_wrongPermission_403() throws Exception
    {
        mockMvc.perform(get("/lawyers/report/call").with(loginAs("lawyers:nope")))
                .andExpect(status().isForbidden());
    }

    @Test
    void callReport_withViewPermission_delegatesService() throws Exception
    {
        mockMvc.perform(get("/lawyers/report/call")
                        .param("beginTime", "2026-10-01 00:00")
                        .param("endTime", "2026-10-01 23:59")
                        .param("granularity", "day")
                        .with(loginAs("lawyers:report:view")))
                .andExpect(status().isOk());

        verify(reportService).callReport("2026-10-01 00:00", "2026-10-01 23:59", "day");
    }

    @Test
    void export_viewPermissionOnly_403() throws Exception
    {
        mockMvc.perform(post("/lawyers/report/call/export")
                        .with(loginAs("lawyers:report:view")))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_withExportPermission_delegatesService() throws Exception
    {
        mockMvc.perform(post("/lawyers/report/call/export")
                        .param("beginTime", "2026-10-01 00:00")
                        .param("endTime", "2026-10-01 23:59")
                        .with(loginAs("lawyers:report:export")))
                .andExpect(status().isOk());

        verify(reportService).exportExcel(eq("call"), eq("2026-10-01 00:00"),
                eq("2026-10-01 23:59"), eq(null), any());
    }

    @Test
    void backfill_withExportPermission_delegatesManager() throws Exception
    {
        mockMvc.perform(post("/lawyers/report/stat/backfill")
                        .param("beginTime", "2026-10-01 00:00")
                        .param("endTime", "2026-10-02 00:00")
                        .with(loginAs("lawyers:report:export")))
                .andExpect(status().isOk());

        verify(statBackfillManager).submit(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void backfillProgress_withViewPermission_delegatesManager() throws Exception
    {
        mockMvc.perform(get("/lawyers/report/stat/backfill/progress")
                        .param("taskId", "t-1")
                        .with(loginAs("lawyers:report:view")))
                .andExpect(status().isOk());

        verify(statBackfillManager).getProgress("t-1");
    }
}
