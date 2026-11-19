package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastCalendar;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastHour;
import ai.lawyers.system.domain.lawyers.forecast.AiForecastSlot;
import ai.lawyers.system.service.lawyers.IAiCallForecastService;

/**
 * 话务预测与智能排班接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallForecastControllerTest
{
    @Mock
    private IAiCallForecastService forecastService;

    @InjectMocks
    private AiCallForecastController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 逐时与分时预测透传服务结果()
    {
        List<AiForecastHour> hours = new ArrayList<AiForecastHour>();
        List<AiForecastSlot> slots = new ArrayList<AiForecastSlot>();
        when(forecastService.preview(7)).thenReturn(hours);
        when(forecastService.slotPreview(7)).thenReturn(slots);

        assertSame(hours, controller.preview(7).get(AjaxResult.DATA_TAG));
        assertSame(slots, controller.slotPreview(7).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 准确率与排班建议透传服务结果()
    {
        Map<String, Object> accuracy = new HashMap<String, Object>();
        Map<String, Object> staffing = new HashMap<String, Object>();
        when(forecastService.slotAccuracy(7)).thenReturn(accuracy);
        when(forecastService.staffing(7)).thenReturn(staffing);

        assertSame(accuracy, controller.slotAccuracy(7).get(AjaxResult.DATA_TAG));
        assertSame(staffing, controller.staffing(7).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 排班确认使用请求天数与备注()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("days", 3);
        body.put("remark", "十一假期");
        when(forecastService.confirm(3, "十一假期", "admin")).thenReturn(88L);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            AjaxResult result = controller.confirm(body);

            assertEquals(88L, result.get("planId"));
            assertEquals(88L, result.get(AjaxResult.DATA_TAG));
        }
        verify(forecastService).confirm(3, "十一假期", "admin");
    }

    @Test
    void 排班确认缺省天数为7()
    {
        Map<String, Object> body = new HashMap<String, Object>();
        when(forecastService.confirm(eq(7), eq(null), eq("admin"))).thenReturn(1L);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            controller.confirm(body);
        }
        verify(forecastService).confirm(7, null, "admin");
    }

    @Test
    void 日历列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiForecastCalendar query = new AiForecastCalendar();
        List<AiForecastCalendar> list = new ArrayList<AiForecastCalendar>();
        list.add(new AiForecastCalendar());
        when(forecastService.selectCalendarList(query)).thenReturn(list);

        TableDataInfo table = controller.calendarList(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
    }

    @Test
    void 新增与修改日历写入操作人()
    {
        AiForecastCalendar calendar = new AiForecastCalendar();
        when(forecastService.insertCalendar(calendar)).thenReturn(1);
        when(forecastService.updateCalendar(calendar)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            assertEquals(200, controller.addCalendar(calendar).get(AjaxResult.CODE_TAG));
            assertEquals(500, controller.editCalendar(calendar).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", calendar.getCreateBy());
        assertEquals("admin", calendar.getUpdateBy());
    }

    @Test
    void 批量删除日历返回影响行数()
    {
        Long[] calendarIds = new Long[] { 1L, 2L };
        when(forecastService.deleteCalendarByIds(calendarIds)).thenReturn(2);

        assertEquals(200, controller.removeCalendar(calendarIds).get(AjaxResult.CODE_TAG));
        verify(forecastService).deleteCalendarByIds(calendarIds);
    }
}
