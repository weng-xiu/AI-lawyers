package ai.lawyers.framework.interceptor;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.MdcUtils;

/**
 * T5-1 全链路 traceId 过滤器。
 *
 * <p>每个 HTTP 请求入口生成（或复用上游传入的）traceId 放入 SLF4J MDC，
 * logback pattern 以 %X{traceId} 输出，使「来电—IVR—AI—坐席—工单」各环节
 * 日志可凭同一 traceId 串联。异步线程（@Async / Stream 消费者 / 自建线程池）
 * 通过 {@link MdcUtils#wrap(Runnable)} 或 ThreadPoolConfig 的 TaskDecorator 透传。</p>
 *
 * <p>上游（网关/ELB）若带 X-Trace-Id 请求头则直接复用，便于跨系统串联；
 * 否则生成 16 位短 UUID。响应头回写 X-Trace-Id，方便前端/排障定位。</p>
 *
 * @author ai-lawyers
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter
{
    /** 上游透传/响应回写的请求头名 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        // 优先复用上游网关注入的 traceId，保证跨系统链路连续
        String traceId = req.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.trim().isEmpty())
        {
            traceId = req.getHeader(MdcUtils.TRACE_ID);
        }
        if (traceId == null || traceId.trim().isEmpty())
        {
            traceId = generateTraceId();
        }
        MDC.put(MdcUtils.TRACE_ID, traceId);
        resp.setHeader(TRACE_ID_HEADER, traceId);
        try
        {
            chain.doFilter(request, response);
        }
        finally
        {
            // 请求结束清理，避免线程池复用线程时 traceId 串号
            MDC.remove(MdcUtils.TRACE_ID);
        }
    }

    /** 生成 16 位无横线短 UUID，兼顾可读性与唯一性 */
    public static String generateTraceId()
    {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
