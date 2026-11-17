package ai.lawyers.framework.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ai.lawyers.common.utils.MdcUtils;

/**
 * 全链路 traceId 过滤器测试：上游透传、自动生成、兼容旧头、响应回写与 MDC 清理。
 *
 * @author ai-lawyers
 */
class TraceIdFilterTest
{
    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc()
    {
        MDC.remove(MdcUtils.TRACE_ID);
    }

    @Test
    void doFilter_复用上游traceId并回写响应头() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "upstream-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> inChain.set(MDC.get(MdcUtils.TRACE_ID)));

        assertEquals("upstream-123", inChain.get());
        assertEquals("upstream-123", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        // 请求结束后必须清理，避免线程复用串号
        assertNull(MDC.get(MdcUtils.TRACE_ID));
    }

    @Test
    void doFilter_无上游头时生成16位traceId() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> inChain.set(MDC.get(MdcUtils.TRACE_ID)));

        assertTrue(inChain.get().matches("[0-9a-f]{16}"), inChain.get());
        assertEquals(inChain.get(), response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    void doFilter_兼容traceId请求头() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcUtils.TRACE_ID, "legacy-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, resp) -> inChain.set(MDC.get(MdcUtils.TRACE_ID)));

        assertEquals("legacy-trace", inChain.get());
        assertEquals("legacy-trace", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    void doFilter_链路异常时仍清理MDC()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class,
                () -> filter.doFilter(request, response, (req, resp) -> {
                    throw new ServletException("boom");
                }));
        assertNull(MDC.get(MdcUtils.TRACE_ID));
    }

    @Test
    void generateTraceId_每次生成不同短UUID()
    {
        String first = TraceIdFilter.generateTraceId();
        String second = TraceIdFilter.generateTraceId();

        assertEquals(16, first.length());
        assertTrue(first.matches("[0-9a-f]{16}"));
        assertNotEquals(first, second);
    }
}
