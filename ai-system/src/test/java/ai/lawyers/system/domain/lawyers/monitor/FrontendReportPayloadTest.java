package ai.lawyers.system.domain.lawyers.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 前端可观测上报载荷测试：外层属性、事件属性读写与默认值。
 *
 * @author ai-lawyers
 */
class FrontendReportPayloadTest
{
    @Test
    void 属性读写()
    {
        FrontendReportPayload.Event event = new FrontendReportPayload.Event();
        event.setKind("error");
        event.setType("vue");
        event.setMessage("渲染异常");
        event.setStack("at App.vue:10");
        event.setRoute("/dashboard");
        event.setUrl("https://ai.example.com/dashboard");
        event.setSource("App.vue");
        event.setMetric("lcp");
        event.setValue(1234.5D);
        event.setTs(1700000000000L);

        assertEquals("error", event.getKind());
        assertEquals("vue", event.getType());
        assertEquals("渲染异常", event.getMessage());
        assertEquals("at App.vue:10", event.getStack());
        assertEquals("/dashboard", event.getRoute());
        assertEquals("https://ai.example.com/dashboard", event.getUrl());
        assertEquals("App.vue", event.getSource());
        assertEquals("lcp", event.getMetric());
        assertEquals(1234.5D, event.getValue().doubleValue());
        assertEquals(1700000000000L, event.getTs());

        FrontendReportPayload payload = new FrontendReportPayload();
        payload.setApp("ai-ui");
        payload.setSessionId("sess-001");
        payload.setEvents(List.of(event));

        assertEquals("ai-ui", payload.getApp());
        assertEquals("sess-001", payload.getSessionId());
        assertEquals(1, payload.getEvents().size());
        assertEquals("error", payload.getEvents().get(0).getKind());
    }

    @Test
    void 默认值为空()
    {
        FrontendReportPayload payload = new FrontendReportPayload();
        FrontendReportPayload.Event event = new FrontendReportPayload.Event();

        assertNull(payload.getApp());
        assertNull(payload.getSessionId());
        assertNull(payload.getEvents());
        assertNull(event.getKind());
        assertNull(event.getValue());
        assertNull(event.getTs());
    }

    @Test
    void 指标事件可独立构造()
    {
        FrontendReportPayload.Event vital = new FrontendReportPayload.Event();
        vital.setKind("vital");
        vital.setMetric("cls");
        vital.setValue(0.05D);

        assertEquals("vital", vital.getKind());
        assertEquals("cls", vital.getMetric());
        assertTrue(vital.getValue() < 0.1D);
        assertNull(vital.getMessage());
    }
}
