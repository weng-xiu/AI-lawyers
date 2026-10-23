package ai.lawyers.system.service.lawyers.monitor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.lawyers.system.domain.lawyers.monitor.FrontendReportPayload;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * P9 前端可观测上报服务。
 *
 * <p>接收前端批量上报的错误与 Web Vitals，做三件事：</p>
 * <ol>
 *   <li><b>校验与截断</b>：白名单过滤事件类型 / 指标名，字段长度截断，单次事件数封顶，
 *       防止异常客户端放大内存、日志和 Prometheus 标签基数；</li>
 *   <li><b>指标埋点</b>：错误 → {@code hotline_frontend_error_total}，Web Vitals →
 *       {@code hotline_frontend_web_vital} 分布，接入 Grafana；</li>
 *   <li><b>日志去重</b>：同一错误（type + route + message）60 秒内只打一条 WARN，
 *       避免同一页面异常风暴刷爆日志。</li>
 * </ol>
 *
 * <p>本服务不写业务库，匿名接口可安全暴露；所有异常在内部吞掉，不影响调用方。</p>
 *
 * @author ai-lawyers
 */
@Service
public class FrontendMonitorService
{
    private static final Logger log = LoggerFactory.getLogger(FrontendMonitorService.class);

    /** 单次上报最多处理的事件数（超出部分丢弃） */
    public static final int MAX_EVENTS_PER_REPORT = 50;

    /** 字段长度上限（字符） */
    static final int MAX_MESSAGE = 500;
    static final int MAX_STACK = 2000;
    static final int MAX_ROUTE = 200;
    static final int MAX_URL = 300;

    /** 错误日志去重窗口（毫秒） */
    static final long DEDUP_WINDOW_MS = 60_000L;

    /** 去重缓存条数上限，超过后清理过期项（仍超限则整体清空） */
    static final int DEDUP_CACHE_LIMIT = 2000;

    private static final Pattern WHITESPACE = Pattern.compile("[\\r\\n\\t]+");

    /** 错误类型白名单，未知类型统一归为 other */
    private static final Set<String> ERROR_TYPES = Set.of("vue", "window", "promise", "resource");

    /** Web Vitals 指标白名单 → 单位（ms 为毫秒，score 为无量纲） */
    private static final Map<String, String> VITAL_UNITS = Map.of(
            "lcp", "ms", "fcp", "ms", "ttfb", "ms", "inp", "ms", "cls", "score");

    @Autowired
    private HotlineMetrics hotlineMetrics;

    /** 错误日志去重：type|route|message → 最近一次打印时间 */
    private final Map<String, Long> recentErrors = new ConcurrentHashMap<>();

    /**
     * 处理一次上报。
     *
     * @param payload 上报载荷
     * @param clientIp 客户端 IP（仅用于日志定位）
     * @return 实际受理的事件数（无效事件不计）
     */
    public int report(FrontendReportPayload payload, String clientIp)
    {
        if (payload == null)
        {
            return 0;
        }
        List<FrontendReportPayload.Event> events = payload.getEvents();
        if (events == null || events.isEmpty())
        {
            return 0;
        }
        int limit = Math.min(events.size(), MAX_EVENTS_PER_REPORT);
        if (events.size() > MAX_EVENTS_PER_REPORT)
        {
            log.debug("[P9前端上报] 事件数 {} 超上限 {}，仅处理前 {} 条", events.size(), MAX_EVENTS_PER_REPORT, limit);
        }
        int accepted = 0;
        for (int i = 0; i < limit; i++)
        {
            if (handleEvent(payload, events.get(i), clientIp))
            {
                accepted++;
            }
        }
        return accepted;
    }

    /** 分发单条事件；返回 false 表示无效事件（未埋点） */
    private boolean handleEvent(FrontendReportPayload payload, FrontendReportPayload.Event event, String clientIp)
    {
        if (event == null)
        {
            return false;
        }
        String kind = lower(event.getKind());
        if ("vital".equals(kind))
        {
            return handleVital(event);
        }
        if (!"error".equals(kind))
        {
            return false;
        }
        return handleError(payload, event, clientIp);
    }

    /** 错误事件：计数 + 去重 WARN 日志 */
    private boolean handleError(FrontendReportPayload payload, FrontendReportPayload.Event event, String clientIp)
    {
        String type = lower(event.getType());
        if (!ERROR_TYPES.contains(type))
        {
            type = "other";
        }
        String route = normalizeRoute(event.getRoute());
        String message = truncate(event.getMessage(), MAX_MESSAGE);
        if (message.isEmpty())
        {
            message = "unknown";
        }
        hotlineMetrics.incrementFrontendError(type, route);

        String dedupKey = type + '|' + route + '|' + message;
        long now = System.currentTimeMillis();
        Long last = recentErrors.get(dedupKey);
        if (last != null && now - last < DEDUP_WINDOW_MS)
        {
            return true;
        }
        recentErrors.put(dedupKey, now);
        evictExpired(now);
        log.warn("[P9前端错误] app={} session={} type={} route={} ip={} source={} url={} msg={} stack={}",
                truncate(payload.getApp(), 40), truncate(payload.getSessionId(), 64), type, route,
                truncate(clientIp, 64), truncate(event.getSource(), 200), truncate(event.getUrl(), MAX_URL),
                message, truncate(event.getStack(), MAX_STACK));
        return true;
    }

    /** 指标事件：白名单 + 数值合法性校验后记录分布 */
    private boolean handleVital(FrontendReportPayload.Event event)
    {
        String metric = lower(event.getMetric());
        String unit = VITAL_UNITS.get(metric);
        Double value = event.getValue();
        if (unit == null || value == null || value.isNaN() || value.isInfinite() || value < 0)
        {
            return false;
        }
        // CLS 为无量纲得分，保留 3 位小数；其余为毫秒，取整
        double normalized = "score".equals(unit) ? Math.round(value * 1000d) / 1000d : Math.round(value);
        hotlineMetrics.recordFrontendWebVital(metric, normalized, unit);
        return true;
    }

    /**
     * 归一化路由：去查询串与哈希、数值段折叠为 {@code *}、截断长度。
     *
     * <p>例如 {@code /lawyers/ticket/edit/123?x=1} → {@code /lawyers/ticket/edit/*}，
     * 避免每条记录一个路由标签导致 Prometheus 时间序列膨胀。</p>
     */
    static String normalizeRoute(String route)
    {
        String path = truncate(route, MAX_ROUTE);
        int cut = path.indexOf('?');
        if (cut >= 0)
        {
            path = path.substring(0, cut);
        }
        cut = path.indexOf('#');
        if (cut >= 0)
        {
            path = path.substring(0, cut);
        }
        path = path.replaceAll("/\\d+(?=/|$)", "/*");
        if (path.isEmpty())
        {
            return "/";
        }
        return path.length() > 80 ? path.substring(0, 80) : path;
    }

    /** 截断字段并压平换行，避免日志注入与超长行 */
    static String truncate(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        String text = WHITESPACE.matcher(value).replaceAll(" ").trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String lower(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 去重缓存超限时清理过期项；若全部未过期则整体清空（宁可少去重，不可无限增长） */
    private void evictExpired(long now)
    {
        if (recentErrors.size() <= DEDUP_CACHE_LIMIT)
        {
            return;
        }
        recentErrors.entrySet().removeIf(entry -> now - entry.getValue() >= DEDUP_WINDOW_MS);
        if (recentErrors.size() > DEDUP_CACHE_LIMIT)
        {
            recentErrors.clear();
        }
    }
}
