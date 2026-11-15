package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiNotice;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.mapper.lawyers.AiCallLedgerMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiNoticeMapper;
import ai.lawyers.system.mapper.lawyers.AiTodoMapper;

/**
 * 工作台服务单测。
 *
 * <p>覆盖工作台统计的今日通话/时长/满意度与在线时长计算、待办查询、最近通话与公告的默认条数。</p>
 *
 * @author ai-lawyers
 */
class WorkbenchServiceImplTest
{
    private WorkbenchServiceImpl service;

    @Mock private AiCallRecordMapper aiCallRecordMapper;
    @Mock private AiCallLedgerMapper aiCallLedgerMapper;
    @Mock private AiCallAgentStatusMapper aiCallAgentStatusMapper;
    @Mock private AiTodoMapper aiTodoMapper;
    @Mock private AiNoticeMapper aiNoticeMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new WorkbenchServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallRecordMapper", aiCallRecordMapper);
        ReflectionTestUtils.setField(service, "aiCallLedgerMapper", aiCallLedgerMapper);
        ReflectionTestUtils.setField(service, "aiCallAgentStatusMapper", aiCallAgentStatusMapper);
        ReflectionTestUtils.setField(service, "aiTodoMapper", aiTodoMapper);
        ReflectionTestUtils.setField(service, "aiNoticeMapper", aiNoticeMapper);
    }

    @Test
    void getWorkbenchStats_aggregatesMetrics()
    {
        Map<String, Object> callStats = new HashMap<>();
        callStats.put("todayCalls", 120);
        callStats.put("todayServiceDuration", 600L);
        when(aiCallRecordMapper.selectTodayCallStats()).thenReturn(callStats);

        Map<String, Object> satStats = new HashMap<>();
        satStats.put("avgSatisfaction", 4.25);
        satStats.put("todayLedgerCount", "5");
        when(aiCallLedgerMapper.selectTodaySatisfactionStats()).thenReturn(satStats);

        AiCallAgentStatus agent = new AiCallAgentStatus();
        agent.setStatus("1");
        agent.setLoginTime(new Date(System.currentTimeMillis() - 5 * 60 * 1000L));
        when(aiCallAgentStatusMapper.selectAiCallAgentStatusByUserId(9L)).thenReturn(agent);

        Map<String, Object> stats = service.getWorkbenchStats(9L);

        assertEquals(120L, stats.get("todayCalls"));
        assertEquals(10L, stats.get("serviceDuration"));
        assertEquals(4.3, stats.get("satisfaction"));
        assertEquals(5L, stats.get("todayLedgerCount"));
        assertEquals("1", stats.get("agentStatus"));
        long onlineDuration = (Long) stats.get("onlineDuration");
        assertTrue(onlineDuration >= 4L);
    }

    @Test
    void getWorkbenchStats_nullStatsAndUser_usesDefaults()
    {
        when(aiCallRecordMapper.selectTodayCallStats()).thenReturn(null);
        when(aiCallLedgerMapper.selectTodaySatisfactionStats()).thenReturn(null);

        Map<String, Object> stats = service.getWorkbenchStats(null);

        assertEquals(0L, stats.get("todayCalls"));
        assertEquals(0L, stats.get("serviceDuration"));
        assertEquals(0.0, stats.get("satisfaction"));
        assertEquals(0L, stats.get("todayLedgerCount"));
        assertEquals(0L, stats.get("onlineDuration"));
        assertEquals("0", stats.get("agentStatus"));
        verify(aiCallAgentStatusMapper, never()).selectAiCallAgentStatusByUserId(anyLong());
    }

    @Test
    void getWorkbenchStats_invalidNumbers_fallBackToZero()
    {
        Map<String, Object> callStats = new HashMap<>();
        callStats.put("todayCalls", "abc");
        callStats.put("todayServiceDuration", null);
        when(aiCallRecordMapper.selectTodayCallStats()).thenReturn(callStats);
        Map<String, Object> satStats = new HashMap<>();
        satStats.put("avgSatisfaction", "not-number");
        when(aiCallLedgerMapper.selectTodaySatisfactionStats()).thenReturn(satStats);

        Map<String, Object> stats = service.getWorkbenchStats(null);

        assertEquals(0L, stats.get("todayCalls"));
        assertEquals(0.0, stats.get("satisfaction"));
    }

    @Test
    void getWorkbenchTodos_nullUser_returnsEmpty()
    {
        assertTrue(service.getWorkbenchTodos(null).isEmpty());
        verify(aiTodoMapper, never()).selectAiTodoListByUserId(anyLong());
    }

    @Test
    void getWorkbenchTodos_delegates()
    {
        List<AiTodo> todos = Collections.singletonList(new AiTodo());
        when(aiTodoMapper.selectAiTodoListByUserId(9L)).thenReturn(todos);

        assertSame(todos, service.getWorkbenchTodos(9L));
    }

    @Test
    void getWorkbenchRecentCalls_defaultLimit()
    {
        List<AiCallRecord> records = Collections.singletonList(new AiCallRecord());
        when(aiCallRecordMapper.selectRecentCalls(5)).thenReturn(records);

        assertSame(records, service.getWorkbenchRecentCalls(null));
        assertSame(records, service.getWorkbenchRecentCalls(0));
    }

    @Test
    void getWorkbenchRecentCalls_explicitLimit()
    {
        List<AiCallRecord> records = Collections.singletonList(new AiCallRecord());
        when(aiCallRecordMapper.selectRecentCalls(10)).thenReturn(records);

        assertSame(records, service.getWorkbenchRecentCalls(10));
    }

    @Test
    void getWorkbenchNotices_defaultLimit()
    {
        List<AiNotice> notices = Collections.singletonList(new AiNotice());
        when(aiNoticeMapper.selectPublishedNotices(5)).thenReturn(notices);

        assertSame(notices, service.getWorkbenchNotices(null));
    }

    @Test
    void getWorkbenchNotices_explicitLimit()
    {
        List<AiNotice> notices = Collections.singletonList(new AiNotice());
        when(aiNoticeMapper.selectPublishedNotices(3)).thenReturn(notices);

        assertSame(notices, service.getWorkbenchNotices(3));
    }
}
