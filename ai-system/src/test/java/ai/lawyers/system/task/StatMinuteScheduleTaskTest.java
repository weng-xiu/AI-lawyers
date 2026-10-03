package ai.lawyers.system.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import ai.lawyers.system.domain.lawyers.stat.AiStatMinute;
import ai.lawyers.system.mapper.lawyers.stat.AiStatMinuteMapper;

/**
 * {@link StatMinuteScheduleTask} 单元测试：验证分钟聚合产出的 30 个 ALL 指标与 5 类维度行。
 *
 * @author ai-lawyers
 */
class StatMinuteScheduleTaskTest
{
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AiStatMinuteMapper aiStatMinuteMapper;

    @InjectMocks
    private StatMinuteScheduleTask task;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void aggregateMinute_writesAllMetricsAndDimensions()
    {
        // 7 次 queryForMap 按调用顺序返回（第二参数为 Object... varargs，须显式匹配两个 String）
        when(jdbcTemplate.queryForMap(anyString(), any(String.class), any(String.class)))
                // 呼叫
                .thenReturn(row("total", 10L, "answered", 7L, "missed", 2L,
                        "transferred", 1L, "durationSum", 350L))
                // 外呼
                .thenReturn(row("total", 4L, "waitSum", 12000L, "waitCount", 3L))
                // 排队 SLA
                .thenReturn(row("total", 6L, "abandon", 1L, "answered", 5L,
                        "waitSum", 240L, "within20", 4L))
                // 工单
                .thenReturn(row("total", 3L, "closed", 2L, "overdue", 1L, "closeSecSum", 7200L))
                // 质检
                .thenReturn(row("total", 5L, "scoreSum", 430L, "scoreCount", 5L,
                        "reviewed", 3L, "pending", 2L, "risk", 1L))
                // 话单满意度
                .thenReturn(row("scoreSum", 240L, "cnt", 3L))
                // 公众端评价
                .thenReturn(row("total", 2L, "overallSum", 9L, "professionalismSum", 8L,
                        "responsivenessSum", 10L, "qualitySum", 7L));

        // 5 次 queryForList：坐席/分类/语种/条线/渠道
        when(jdbcTemplate.queryForList(anyString(), any(String.class), any(String.class)))
                .thenReturn(Arrays.asList(
                        row("dim", 1001L, "total", 5L, "answered", 3L, "durationSum", 200L),
                        row("dim", 1002L, "total", 2L, "answered", 2L, "durationSum", 80L)))
                .thenReturn(Arrays.asList(
                        row("dim", 10L, "total", 6L),
                        row("dim", -1L, "total", 4L)))
                .thenReturn(Arrays.asList(
                        row("dim", "zh-CN", "total", 7L),
                        row("dim", "en-US", "total", 3L)))
                .thenReturn(Arrays.asList(
                        row("dim", "police", "total", 2L, "closed", 1L, "closeSecSum", 300L),
                        row("dim", "court", "total", 1L, "closed", 0L, "closeSecSum", 0L)))
                .thenReturn(Arrays.asList(
                        row("dim", "web", "total", 8L),
                        row("dim", "wechat", "total", 2L)));

        int count = task.aggregateMinute(LocalDateTime.of(2026, 10, 3, 10, 15, 42));

        // 30 ALL + 坐席6 + 分类2 + 语种2 + 条线6 + 渠道2 = 48
        assertThat(count).isEqualTo(48);
        verify(jdbcTemplate, times(7)).queryForMap(anyString(), any(String.class), any(String.class));
        verify(jdbcTemplate, times(5)).queryForList(anyString(), any(String.class), any(String.class));

        ArgumentCaptor<List<AiStatMinute>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiStatMinuteMapper).batchUpsert(captor.capture());
        List<AiStatMinute> rows = captor.getValue();

        // 前 30 行为 ALL，键顺序与取值固定
        List<AiStatMinute> allRows = rows.subList(0, 30);
        assertThat(allRows).extracting(AiStatMinute::getMetricKey).containsExactly(
                "call_total", "call_answered", "call_missed", "call_transferred",
                "answered_duration_sum",
                "outbound_total", "queue_wait_ms_sum", "queue_wait_count",
                "queue_total", "queue_abandon", "queue_answered",
                "queue_answered_wait_sum", "queue_within20",
                "ticket_total", "ticket_closed", "ticket_overdue", "ticket_close_sec_sum",
                "quality_total", "quality_score_sum", "quality_score_count",
                "quality_reviewed", "quality_pending", "quality_risk",
                "satisfaction_score_sum", "satisfaction_count",
                "eval_total", "eval_overall_sum", "eval_professionalism_sum",
                "eval_responsiveness_sum", "eval_quality_sum");
        assertThat(allRows).allSatisfy(r -> {
            assertThat(r.getDimension()).isEqualTo("ALL");
            assertThat(r.getStatTime().toInstant().atZone(ZoneId.systemDefault()).getSecond()).isZero();
        });
        // 抽查取值
        assertThat(allRows.get(0).getMetricValue()).isEqualByComparingTo("10");
        assertThat(allRows.get(4).getMetricValue()).isEqualByComparingTo("350");
        assertThat(allRows.get(12).getMetricValue()).isEqualByComparingTo("4");
        assertThat(allRows.get(23).getMetricValue()).isEqualByComparingTo("240");
        assertThat(allRows.get(29).getMetricValue()).isEqualByComparingTo("7");

        // 维度行：前缀 + 指标键
        List<AiStatMinute> dimRows = rows.subList(30, rows.size());
        assertThat(dimRows).extracting(AiStatMinute::getDimension).containsExactly(
                "agent:1001", "agent:1001", "agent:1001",
                "agent:1002", "agent:1002", "agent:1002",
                "category:10", "category:NONE",
                "lang:zh-CN", "lang:en-US",
                "line:police", "line:police", "line:police",
                "line:court", "line:court", "line:court",
                "channel:web", "channel:wechat");
        assertThat(dimRows).extracting(AiStatMinute::getMetricKey).containsExactly(
                "call_total", "call_answered", "answered_duration_sum",
                "call_total", "call_answered", "answered_duration_sum",
                "call_total", "call_total",
                "call_total", "call_total",
                "transfer_total", "closed_count", "close_sec_sum",
                "transfer_total", "closed_count", "close_sec_sum",
                "session_total", "session_total");
    }

    private static Map<String, Object> row(Object... kv)
    {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2)
        {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
