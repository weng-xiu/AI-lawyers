package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.service.lawyers.stat.IDashboardService;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 运营大屏接口测试：时间参数解析与默认统计区间。
 *
 * @author ai-lawyers
 */
class DashboardControllerTest
{
    private DashboardController controller;

    private IDashboardService dashboardService;

    @BeforeEach
    void setUp()
    {
        dashboardService = mock(IDashboardService.class);
        controller = new DashboardController();
        ReflectionTestUtils.setField(controller, "dashboardService", dashboardService);
    }

    @Test
    void 指定时间范围时按参数查询()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("kpi", 1);
        when(dashboardService.getDashboardData(any(), any())).thenReturn(data);

        AjaxResult result = controller.data("2026-01-01", "2026-01-02");

        assertSame(data, result.get(AjaxResult.DATA_TAG));
        ArgumentCaptor<Date> beginCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endCaptor = ArgumentCaptor.forClass(Date.class);
        verify(dashboardService).getDashboardData(beginCaptor.capture(), endCaptor.capture());
        assertEquals(DateUtils.parseDate("2026-01-01"), beginCaptor.getValue());
        assertEquals(DateUtils.parseDate("2026-01-02"), endCaptor.getValue());
    }

    @Test
    void 未指定时间时默认统计今日()
    {
        when(dashboardService.getDashboardData(any(), any())).thenReturn(new HashMap<>());

        long before = System.currentTimeMillis();
        controller.data(null, null);
        long after = System.currentTimeMillis();

        ArgumentCaptor<Date> beginCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endCaptor = ArgumentCaptor.forClass(Date.class);
        verify(dashboardService).getDashboardData(beginCaptor.capture(), endCaptor.capture());

        Calendar begin = Calendar.getInstance();
        begin.setTime(beginCaptor.getValue());
        assertEquals(0, begin.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, begin.get(Calendar.MINUTE));
        assertEquals(0, begin.get(Calendar.SECOND));
        assertTrue(endCaptor.getValue().getTime() >= before && endCaptor.getValue().getTime() <= after);
    }

    @Test
    void 仅指定开始时间时结束时间取当前()
    {
        when(dashboardService.getDashboardData(any(), any())).thenReturn(new HashMap<>());

        controller.data("2026-01-01", null);

        ArgumentCaptor<Date> endCaptor = ArgumentCaptor.forClass(Date.class);
        verify(dashboardService).getDashboardData(any(), endCaptor.capture());
        assertNotNull(endCaptor.getValue());
    }
}
