package ai.lawyers.system.service.impl.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
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
import ai.lawyers.system.mapper.lawyers.quality.AiAgentStatusLogMapper;
import ai.lawyers.system.mapper.lawyers.stat.DashboardMapper;
import ai.lawyers.system.service.lawyers.voice.copilot.IAiCopilotFeedbackService;

/**
 * B4 运营大屏聚合 Service 单测。
 *
 * <p>覆盖预聚合/实时双路由、SLA 服务水平与放弃率、F10 业务指标映射（语种/关怀/
 * 条线/渠道/公众端/Copilot）以及坐席状态时长聚合与异常隔离。</p>
 *
 * @author ai-lawyers
 */
class DashboardServiceImplTest
{
    private DashboardServiceImpl service;

    @Mock private DashboardMapper dashboardMapper;
    @Mock private AiAgentStatusLogMapper agentStatusLogMapper;
    @Mock private IAiCopilotFeedbackService copilotFeedbackService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new DashboardServiceImpl();
        ReflectionTestUtils.setField(service, "dashboardMapper", dashboardMapper);
        ReflectionTestUtils.setField(service, "agentStatusLogMapper", agentStatusLogMapper);
        ReflectionTestUtils.setField(service, "copilotFeedbackService", copilotFeedbackService);
        ReflectionTestUtils.setField(service, "preaggEnabled", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDashboardData_preagg_routesToPreaggAndComputesSla()
    {
        givenPreaggBase();

        Map<String, Object> data = service.getDashboardData(begin(), end());

        assertEquals(12, data.size());
        assertEquals(10L, ((Map<String, Object>) data.get("callSummary")).get("totalCalls"));
        Map<String, Object> sla = (Map<String, Object>) data.get("slaSummary");
        assertEquals(80.0, sla.get("serviceLevel"));
        assertEquals(10.0, sla.get("abandonRate"));
        assertTrue(((List<?>) data.get("callTrend")).isEmpty());
        verify(dashboardMapper, never()).selectCallSummary(any(), any());
        verify(dashboardMapper, never()).selectSlaSummary(any(), any(), anyInt());
    }

    @Test
    void getDashboardData_preaggDisabled_routesToRealtime()
    {
        ReflectionTestUtils.setField(service, "preaggEnabled", false);
        givenRealtimeBase();

        Map<String, Object> data = service.getDashboardData(begin(), end());

        Map<String, Object> sla = castMap(data.get("slaSummary"));
        assertEquals(50.0, sla.get("serviceLevel"));
        assertEquals(25.0, sla.get("abandonRate"));
        verify(dashboardMapper, never()).selectCallSummaryPreagg(any(), any());
        verify(dashboardMapper, never()).selectSlaSummaryPreagg(any(), any());
        verify(dashboardMapper).selectSlaSummary(any(), any(), eq(20));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDashboardData_buildsBizMetrics()
    {
        givenPreaggBase();
        when(dashboardMapper.selectLanguageDistPreagg(any(), any())).thenReturn(Arrays.asList(
                language("zh-CN", 5), language("en-US", 2), language(null, "x")));
        Map<String, Object> care = new HashMap<>();
        care.put("totalCalls", 10);
        care.put("careCalls", 1);
        care.put("totalCallers", 4);
        care.put("careCallers", 1);
        when(dashboardMapper.selectCareUsage(any(), any())).thenReturn(care);
        when(dashboardMapper.selectTransferLineStatsPreagg(any(), any())).thenReturn(Arrays.asList(
                transfer("LEGAL_AID", 10, 8), transfer("MYSTERY", 0, 0)));
        when(dashboardMapper.selectChannelSessionsPreagg(any(), any())).thenReturn(
                Collections.singletonList(channel("PHONE")));
        when(dashboardMapper.selectChannelBinds()).thenReturn(Collections.singletonList(channel("WEB")));
        when(dashboardMapper.selectPortalSatisfactionPreagg(any(), any())).thenReturn(new HashMap<>());

        Map<String, Object> biz = castMap(service.getDashboardData(begin(), end()).get("bizMetrics"));

        List<Map<String, Object>> langs = (List<Map<String, Object>>) biz.get("languageDist");
        assertEquals("普通话", langs.get(0).get("langName"));
        assertEquals("en-US", langs.get(1).get("langName"));
        assertEquals("", langs.get(2).get("lang"));
        assertEquals(0L, langs.get(2).get("value"));

        Map<String, Object> careOut = (Map<String, Object>) biz.get("careUsage");
        assertEquals(10.0, careOut.get("careCallRate"));
        assertEquals(25.0, careOut.get("careCallerRate"));

        List<Map<String, Object>> lines = (List<Map<String, Object>>) biz.get("transferLines");
        assertEquals("法律援助", lines.get(0).get("lineName"));
        assertEquals(80.0, lines.get(0).get("closeRate"));
        assertEquals("MYSTERY", lines.get(1).get("lineName"));
        assertEquals(0.0, lines.get(1).get("closeRate"));

        List<Map<String, Object>> sessions = (List<Map<String, Object>>) biz.get("channelSessions");
        assertEquals("热线电话", sessions.get(0).get("channelName"));
        List<Map<String, Object>> binds = (List<Map<String, Object>>) biz.get("channelBinds");
        assertEquals("网站", binds.get(0).get("channelName"));

        assertTrue(castMap(biz.get("portalSatisfaction")).isEmpty());
        assertSame(copilotMap, biz.get("copilotAdoption"));
    }

    @Test
    void getDashboardData_aggregatesAgentStatusDuration()
    {
        givenPreaggBase();
        when(agentStatusLogMapper.sumStatusDuration(anyString(), anyString())).thenReturn(Arrays.asList(
                duration(120, "1", "1"),
                duration(60, "1", "5"),
                duration(30, "1", "0"),
                duration(90, "2", "0")));

        Map<String, Object> result = castMap(service.getDashboardData(begin(), end()).get("agentStatusDuration"));

        assertEquals(120L, result.get("talkSeconds"));
        assertEquals(60L, result.get("afterWorkSeconds"));
        assertEquals(30L, result.get("readySeconds"));
        assertEquals(90L, result.get("busySeconds"));
        assertEquals(2L, result.get("talkMinutes"));
        assertEquals(1L, result.get("afterWorkMinutes"));
        assertEquals(1L, result.get("readyMinutes"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDashboardData_statusLogFailure_isIsolated()
    {
        givenPreaggBase();
        when(agentStatusLogMapper.sumStatusDuration(anyString(), anyString()))
                .thenThrow(new RuntimeException("table missing"));

        Map<String, Object> result = castMap(service.getDashboardData(begin(), end()).get("agentStatusDuration"));

        assertEquals(0L, result.get("talkSeconds"));
        assertEquals(0L, result.get("busySeconds"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDashboardData_nullStatusRows_producesZeros()
    {
        givenPreaggBase();
        when(agentStatusLogMapper.sumStatusDuration(anyString(), anyString())).thenReturn(null);

        Map<String, Object> result = castMap(service.getDashboardData(begin(), end()).get("agentStatusDuration"));

        assertEquals(0L, result.get("talkSeconds"));
        assertEquals(0L, result.get("readySeconds"));
    }

    @Test
    void getDashboardData_nullMapsAreReplaced()
    {
        givenPreaggBase();

        Map<String, Object> data = service.getDashboardData(begin(), end());

        assertTrue(castMap(data.get("agentStatusSummary")).isEmpty());
        assertTrue(((List<?>) data.get("agentLoad")).isEmpty());
        assertFalse(data.isEmpty());
    }

    // ---------------- 辅助 ----------------

    private final Map<String, Object> copilotMap = Collections.singletonMap("adoptRate", 40.0);

    private void givenPreaggBase()
    {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCalls", 10L);
        when(dashboardMapper.selectCallSummaryPreagg(any(), any())).thenReturn(summary);
        when(dashboardMapper.selectCallTrendPreagg(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectCategoryPiePreagg(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectSatisfactionSummaryPreagg(any(), any())).thenReturn(new HashMap<>());
        Map<String, Object> sla = new HashMap<>();
        sla.put("answeredCount", 10);
        sla.put("withinThreshold", 8);
        sla.put("abandonedCount", 2);
        sla.put("totalQueued", 20);
        when(dashboardMapper.selectSlaSummaryPreagg(any(), any())).thenReturn(sla);
        when(dashboardMapper.selectAiRatio(any(), any())).thenReturn(new HashMap<>());
        when(dashboardMapper.selectAgentLoad()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectQueueNow()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectOutboundProgress()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectAgentStatusSummary()).thenReturn(null);
        when(agentStatusLogMapper.sumStatusDuration(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(dashboardMapper.selectLanguageDistPreagg(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectCareUsage(any(), any())).thenReturn(new HashMap<>());
        when(dashboardMapper.selectTransferLineStatsPreagg(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectChannelSessionsPreagg(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectChannelBinds()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectPortalSatisfactionPreagg(any(), any())).thenReturn(new HashMap<>());
        when(copilotFeedbackService.adoptionTotal(any(), any())).thenReturn(copilotMap);
    }

    private void givenRealtimeBase()
    {
        when(dashboardMapper.selectCallSummary(any(), any())).thenReturn(new HashMap<>());
        when(dashboardMapper.selectCallTrend(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectCategoryPie(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectSatisfactionSummary(any(), any())).thenReturn(new HashMap<>());
        Map<String, Object> sla = new HashMap<>();
        sla.put("answeredCount", 4);
        sla.put("withinThreshold", 2);
        sla.put("abandonedCount", 1);
        sla.put("totalQueued", 4);
        when(dashboardMapper.selectSlaSummary(any(), any(), anyInt())).thenReturn(sla);
        when(dashboardMapper.selectAiRatio(any(), any())).thenReturn(new HashMap<>());
        when(dashboardMapper.selectAgentLoad()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectQueueNow()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectOutboundProgress()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectAgentStatusSummary()).thenReturn(new HashMap<>());
        when(agentStatusLogMapper.sumStatusDuration(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(dashboardMapper.selectLanguageDist(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectCareUsage(any(), any())).thenReturn(new HashMap<>());
        when(dashboardMapper.selectTransferLineStats(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectChannelSessions(any(), any())).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectChannelBinds()).thenReturn(Collections.emptyList());
        when(dashboardMapper.selectPortalSatisfaction(any(), any())).thenReturn(new HashMap<>());
        when(copilotFeedbackService.adoptionTotal(any(), any())).thenReturn(copilotMap);
    }

    private Date begin()
    {
        return new Date(System.currentTimeMillis() - 86400_000L);
    }

    private Date end()
    {
        return new Date();
    }

    private Map<String, Object> language(String lang, Object value)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("lang", lang);
        row.put("value", value);
        return row;
    }

    private Map<String, Object> transfer(String line, long transferCount, long closedCount)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("line", line);
        row.put("transferCount", transferCount);
        row.put("closedCount", closedCount);
        return row;
    }

    private Map<String, Object> channel(String channel)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("channel", channel);
        return row;
    }

    private Map<String, Object> duration(long seconds, String toStatus, String toCallStatus)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("durationSum", seconds);
        row.put("toStatus", toStatus);
        row.put("toCallStatus", toCallStatus);
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value)
    {
        return (Map<String, Object>) value;
    }
}
