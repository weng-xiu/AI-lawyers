package ai.lawyers.web.controller.lawyers.collab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.system.service.lawyers.stat.IVisitSatisfactionService;

/**
 * 回访满意度看板接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class VisitSatisfactionControllerTest
{
    @Mock
    private IVisitSatisfactionService visitSatisfactionService;

    @InjectMocks
    private VisitSatisfactionController controller;

    @Test
    void 显式时间区间原样透传服务()
    {
        Map<String, Object> board = new HashMap<String, Object>();
        board.put("satisfactionRate", 96.5);
        when(visitSatisfactionService.getBoard(any(Date.class), any(Date.class))).thenReturn(board);

        AjaxResult result = controller.board("2026-01-01", "2026-01-31");

        ArgumentCaptor<Date> beginCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endCaptor = ArgumentCaptor.forClass(Date.class);
        verify(visitSatisfactionService).getBoard(beginCaptor.capture(), endCaptor.capture());
        assertEquals(DateUtils.parseDate("2026-01-01").getTime(), beginCaptor.getValue().getTime());
        assertEquals(DateUtils.parseDate("2026-01-31").getTime(), endCaptor.getValue().getTime());
        assertSame(board, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 缺省区间取近三十天且起点为零点()
    {
        when(visitSatisfactionService.getBoard(any(Date.class), any(Date.class)))
                .thenReturn(new HashMap<String, Object>());

        controller.board(null, null);

        ArgumentCaptor<Date> beginCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endCaptor = ArgumentCaptor.forClass(Date.class);
        verify(visitSatisfactionService).getBoard(beginCaptor.capture(), endCaptor.capture());

        Calendar beginCal = Calendar.getInstance();
        beginCal.setTime(beginCaptor.getValue());
        assertEquals(0, beginCal.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, beginCal.get(Calendar.MINUTE));
        assertEquals(0, beginCal.get(Calendar.SECOND));
        assertEquals(0, beginCal.get(Calendar.MILLISECOND));

        Calendar expectedCal = Calendar.getInstance();
        expectedCal.set(Calendar.HOUR_OF_DAY, 0);
        expectedCal.set(Calendar.MINUTE, 0);
        expectedCal.set(Calendar.SECOND, 0);
        expectedCal.set(Calendar.MILLISECOND, 0);
        expectedCal.add(Calendar.DAY_OF_MONTH, -29);
        assertTrue(Math.abs(beginCal.getTimeInMillis() - expectedCal.getTimeInMillis()) < 5000L);
        assertTrue(endCaptor.getValue().getTime() >= beginCaptor.getValue().getTime());
    }
}
