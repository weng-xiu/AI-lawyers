package ai.lawyers.web.controller.lawyers.collab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.service.lawyers.stat.ISlaBoardService;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 工单 SLA 看板接口测试：时间范围、预警窗口与默认统计区间。
 *
 * @author ai-lawyers
 */
class SlaBoardControllerTest
{
    private SlaBoardController controller;

    private ISlaBoardService slaBoardService;

    @BeforeEach
    void setUp()
    {
        slaBoardService = mock(ISlaBoardService.class);
        controller = new SlaBoardController();
        ReflectionTestUtils.setField(controller, "slaBoardService", slaBoardService);
    }

    @Test
    void 指定参数时按参数查询()
    {
        Map<String, Object> data = Map.of("onTimeRate", 0.95D);
        when(slaBoardService.getSlaBoard(any(), any(), any())).thenReturn(data);

        AjaxResult result = controller.data("2026-01-01", "2026-01-02", 30);

        assertSame(data, result.get(AjaxResult.DATA_TAG));
        verify(slaBoardService).getSlaBoard(DateUtils.parseDate("2026-01-01"),
                DateUtils.parseDate("2026-01-02"), 30);
    }

    @Test
    void 缺省时间时默认今日且透传预警窗口()
    {
        when(slaBoardService.getSlaBoard(any(), any(), any())).thenReturn(Map.of());

        controller.data(null, null, 15);

        ArgumentCaptor<Date> beginCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endCaptor = ArgumentCaptor.forClass(Date.class);
        verify(slaBoardService).getSlaBoard(beginCaptor.capture(), endCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(15));

        Calendar begin = Calendar.getInstance();
        begin.setTime(beginCaptor.getValue());
        assertEquals(0, begin.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, begin.get(Calendar.MINUTE));
        assertEquals(0, begin.get(Calendar.SECOND));
        assertTrue(endCaptor.getValue() instanceof Date);
    }
}
