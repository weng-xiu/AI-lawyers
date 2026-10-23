package ai.lawyers.system.service.lawyers.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.monitor.FrontendReportPayload;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * P9：{@link FrontendMonitorService} 单元测试。
 *
 * <p>覆盖：空载荷防护、错误类型白名单、路由归一化、字段截断、事件数上限、
 * Web Vitals 白名单与数值合法性、未知事件类型丢弃。</p>
 *
 * @author ai-lawyers
 */
class FrontendMonitorServiceTest
{
    private HotlineMetrics metrics;
    private FrontendMonitorService service;

    @BeforeEach
    void setUp()
    {
        metrics = mock(HotlineMetrics.class);
        service = new FrontendMonitorService();
        ReflectionTestUtils.setField(service, "hotlineMetrics", metrics);
    }

    private static FrontendReportPayload payload(FrontendReportPayload.Event... events)
    {
        FrontendReportPayload payload = new FrontendReportPayload();
        payload.setApp("ai-ui");
        payload.setSessionId("s-1");
        List<FrontendReportPayload.Event> list = new ArrayList<>();
        for (FrontendReportPayload.Event event : events)
        {
            list.add(event);
        }
        payload.setEvents(list);
        return payload;
    }

    private static FrontendReportPayload.Event error(String type, String message, String route)
    {
        FrontendReportPayload.Event event = new FrontendReportPayload.Event();
        event.setKind("error");
        event.setType(type);
        event.setMessage(message);
        event.setRoute(route);
        event.setStack("at foo (app.js:1)");
        event.setUrl("https://example.com/lawyers/ticket");
        event.setTs(System.currentTimeMillis());
        return event;
    }

    private static FrontendReportPayload.Event vital(String metric, Double value)
    {
        FrontendReportPayload.Event event = new FrontendReportPayload.Event();
        event.setKind("vital");
        event.setMetric(metric);
        event.setValue(value);
        event.setRoute("/lawyers/workbench");
        event.setTs(System.currentTimeMillis());
        return event;
    }

    @Test
    void report_nullOrEmptyPayload_returnsZero()
    {
        assertThat(service.report(null, "127.0.0.1")).isZero();

        FrontendReportPayload empty = new FrontendReportPayload();
        assertThat(service.report(empty, "127.0.0.1")).isZero();

        empty.setEvents(new ArrayList<>());
        assertThat(service.report(empty, "127.0.0.1")).isZero();

        verify(metrics, never()).incrementFrontendError(anyString(), anyString());
        verify(metrics, never()).recordFrontendWebVital(anyString(), anyDouble(), anyString());
    }

    @Test
    void report_errorEvent_incrementsMetricWithNormalizedRoute()
    {
        int accepted = service.report(payload(error("vue", "Cannot read property 'x' of undefined",
                "/lawyers/ticket/edit/123?tab=log#top")), "10.0.0.8");

        assertThat(accepted).isEqualTo(1);
        verify(metrics).incrementFrontendError("vue", "/lawyers/ticket/edit/*");
    }

    @Test
    void report_unknownErrorType_mapsToOther()
    {
        int accepted = service.report(payload(error("custom", "boom", "/a")), "10.0.0.8");

        assertThat(accepted).isEqualTo(1);
        verify(metrics).incrementFrontendError("other", "/a");
    }

    @Test
    void report_eventsOverLimit_onlyFirst50Handled()
    {
        FrontendReportPayload.Event[] events = new FrontendReportPayload.Event[60];
        for (int i = 0; i < events.length; i++)
        {
            events[i] = error("window", "err-" + i, "/p");
        }

        int accepted = service.report(payload(events), "10.0.0.8");

        assertThat(accepted).isEqualTo(FrontendMonitorService.MAX_EVENTS_PER_REPORT);
        verify(metrics, times(FrontendMonitorService.MAX_EVENTS_PER_REPORT))
                .incrementFrontendError("window", "/p");
    }

    @Test
    void report_vital_recordsWhitelistedMetricsWithUnit()
    {
        int accepted = service.report(payload(
                vital("lcp", 2500.6d),
                vital("cls", 0.12345d),
                vital("unknown-metric", 10d),
                vital("inp", -1d),
                vital("fcp", null)), "10.0.0.8");

        assertThat(accepted).isEqualTo(2);
        verify(metrics).recordFrontendWebVital("lcp", 2501d, "ms");
        verify(metrics).recordFrontendWebVital("cls", 0.123d, "score");
        verify(metrics, never()).recordFrontendWebVital("unknown-metric", 10d, "ms");
        verify(metrics, times(2)).recordFrontendWebVital(anyString(), anyDouble(), anyString());
    }

    @Test
    void report_unknownKindOrNullEvent_skipped()
    {
        FrontendReportPayload.Event unknown = new FrontendReportPayload.Event();
        unknown.setKind("metric");
        unknown.setMessage("x");

        FrontendReportPayload.Event[] events = new FrontendReportPayload.Event[] {unknown, null};
        int accepted = service.report(payload(events), "10.0.0.8");

        assertThat(accepted).isZero();
        verify(metrics, never()).incrementFrontendError(anyString(), anyString());
        verify(metrics, never()).recordFrontendWebVital(anyString(), anyDouble(), anyString());
    }

    @Test
    void report_sameErrorTwice_stillCountsBothTimes()
    {
        FrontendReportPayload.Event event = error("promise", "network timeout", "/lawyers/callCenter/index");

        service.report(payload(event, event), "10.0.0.8");

        // 日志去重只影响 WARN 输出，指标计数不丢
        verify(metrics, times(2)).incrementFrontendError("promise", "/lawyers/callCenter/index");
    }

    @Test
    void normalizeRoute_stripsQueryAndCollapsesNumericSegments()
    {
        assertThat(FrontendMonitorService.normalizeRoute("/lawyers/ticket/42")).isEqualTo("/lawyers/ticket/*");
        assertThat(FrontendMonitorService.normalizeRoute("/lawyers/ticket/42/detail?x=1")).isEqualTo("/lawyers/ticket/*/detail");
        assertThat(FrontendMonitorService.normalizeRoute(null)).isEqualTo("/");
        assertThat(FrontendMonitorService.normalizeRoute("")).isEqualTo("/");
        assertThat(FrontendMonitorService.normalizeRoute("/lawyers/abc123")).isEqualTo("/lawyers/abc123");
    }

    @Test
    void truncate_flattensWhitespaceAndCapsLength()
    {
        assertThat(FrontendMonitorService.truncate("a\nb\tc", 100)).isEqualTo("a b c");
        assertThat(FrontendMonitorService.truncate(null, 10)).isEmpty();
        assertThat(FrontendMonitorService.truncate("0123456789", 5)).isEqualTo("01234");
    }
}
