package ai.lawyers.system.service.lawyers.stat.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.mapper.lawyers.stat.SlaBoardMapper;

/**
 * 工单 SLA 看板服务单测。
 *
 * <p>覆盖预警窗口默认值与自定义值、过期/准时/办结率计算、空聚合行字段补零，
 * 以及各列表为空时的兜底。</p>
 *
 * @author ai-lawyers
 */
class SlaBoardServiceImplTest
{
    private SlaBoardServiceImpl service;

    @Mock private SlaBoardMapper slaBoardMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SlaBoardServiceImpl();
        ReflectionTestUtils.setField(service, "slaBoardMapper", slaBoardMapper);
    }

    @Test
    void getSlaBoard_nullWarn_usesDefaultSixty()
    {
        when(slaBoardMapper.selectOverview(any(), any())).thenReturn(null);
        when(slaBoardMapper.countDueSoon(60)).thenReturn(3L);

        Map<String, Object> data = service.getSlaBoard(null, null, null);

        assertEquals(60, data.get("warnMinutes"));
        Map<?, ?> overview = (Map<?, ?>) data.get("overview");
        assertEquals(3L, overview.get("dueSoonCount"));
        assertEquals(0L, overview.get("totalCount"));
        assertEquals(0L, overview.get("closedCount"));
        assertEquals(0L, overview.get("closedOnTimeCount"));
        assertEquals(0L, overview.get("closedOvertimeCount"));
        assertEquals(0L, overview.get("openCount"));
        assertEquals(0L, overview.get("openOvertimeCount"));
        assertEquals(0.0, overview.get("achievementRate"));
        assertEquals(0.0, overview.get("closeRate"));
        assertEquals(0.0, overview.get("overtimeRate"));
        assertEquals(0L, overview.get("totalOvertimeCount"));
    }

    @Test
    void getSlaBoard_nonPositiveWarn_usesDefault()
    {
        when(slaBoardMapper.selectOverview(any(), any())).thenReturn(null);
        when(slaBoardMapper.countDueSoon(60)).thenReturn(0L);

        assertEquals(60, service.getSlaBoard(null, null, 0).get("warnMinutes"));
        assertEquals(60, service.getSlaBoard(null, null, -5).get("warnMinutes"));
    }

    @Test
    void getSlaBoard_customWarn_passedThrough()
    {
        when(slaBoardMapper.selectOverview(any(), any())).thenReturn(null);
        when(slaBoardMapper.countDueSoon(120)).thenReturn(1L);

        assertEquals(120, service.getSlaBoard(null, null, 120).get("warnMinutes"));
    }

    @Test
    void getSlaBoard_computesRates()
    {
        Map<String, Object> overview = new HashMap<>();
        overview.put("totalCount", 10);
        overview.put("closedCount", 8);
        overview.put("closedOnTimeCount", 6);
        overview.put("closedOvertimeCount", 2);
        overview.put("openOvertimeCount", "1");
        when(slaBoardMapper.selectOverview(any(), any())).thenReturn(overview);
        when(slaBoardMapper.countDueSoon(anyInt())).thenReturn(2L);

        Map<?, ?> result = (Map<?, ?>) service.getSlaBoard(new Date(), new Date(), 30).get("overview");

        assertEquals(60.0, result.get("achievementRate"));
        assertEquals(80.0, result.get("closeRate"));
        assertEquals(25.0, result.get("overtimeRate"));
        assertEquals(3L, result.get("totalOvertimeCount"));
        assertEquals(2L, result.get("dueSoonCount"));
    }

    @Test
    void getSlaBoard_nullLists_becomeEmpty()
    {
        when(slaBoardMapper.selectOverview(any(), any())).thenReturn(null);
        when(slaBoardMapper.countDueSoon(anyInt())).thenReturn(0L);
        when(slaBoardMapper.selectDueSoon(anyInt(), anyInt())).thenReturn(null);
        when(slaBoardMapper.selectAchieveByBizType(any(), any())).thenReturn(null);
        when(slaBoardMapper.selectAchieveByAssignee(any(), any())).thenReturn(null);
        when(slaBoardMapper.selectOvertimeTop(any(), any(), anyInt())).thenReturn(null);
        when(slaBoardMapper.selectTransferStat(any(), any())).thenReturn(null);

        Map<String, Object> data = service.getSlaBoard(null, null, null);

        assertTrue(((List<?>) data.get("achieveByBizType")).isEmpty());
        assertTrue(((List<?>) data.get("achieveByAssignee")).isEmpty());
        assertTrue(((List<?>) data.get("overtimeTop")).isEmpty());
        assertTrue(((List<?>) data.get("dueSoon")).isEmpty());
        assertTrue(((List<?>) data.get("transferStat")).isEmpty());
    }

    @Test
    void getSlaBoard_listsPassedThrough()
    {
        List<Map<String, Object>> list = Collections.singletonList(new LinkedHashMap<>());
        when(slaBoardMapper.selectOverview(any(), any())).thenReturn(null);
        when(slaBoardMapper.countDueSoon(anyInt())).thenReturn(0L);
        when(slaBoardMapper.selectDueSoon(60, 10)).thenReturn(list);
        when(slaBoardMapper.selectAchieveByBizType(any(), any())).thenReturn(list);
        when(slaBoardMapper.selectAchieveByAssignee(any(), any())).thenReturn(list);
        when(slaBoardMapper.selectOvertimeTop(any(), any(), anyInt())).thenReturn(list);
        when(slaBoardMapper.selectTransferStat(any(), any())).thenReturn(list);

        Map<String, Object> data = service.getSlaBoard(null, null, null);

        assertSame(list, data.get("achieveByBizType"));
        assertSame(list, data.get("achieveByAssignee"));
        assertSame(list, data.get("overtimeTop"));
        assertSame(list, data.get("dueSoon"));
        assertSame(list, data.get("transferStat"));
    }
}
