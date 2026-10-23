package ai.lawyers.system.domain.lawyers.monitor;

import java.util.List;

/**
 * P9 前端可观测上报载荷。
 *
 * <p>前端 {@code frontendMonitor.js} 批量上报两类事件：</p>
 * <ul>
 *   <li>{@code kind=error}：Vue 组件异常 / window.onerror / 未处理 Promise / 静态资源加载失败；</li>
 *   <li>{@code kind=vital}：Core Web Vitals（lcp/fcp/ttfb/inp/cls）。</li>
 * </ul>
 *
 * <p>后端只做字段校验、截断与指标埋点，不落业务库；字段长度/取值范围由
 * {@code FrontendMonitorService} 统一收口。</p>
 *
 * @author ai-lawyers
 */
public class FrontendReportPayload
{
    /** 上报来源，如 ai-ui / aiuser-ui */
    private String app;

    /** 页面会话标识（前端随机生成，用于串联同一次访问的事件） */
    private String sessionId;

    /** 事件列表 */
    private List<Event> events;

    public String getApp()
    {
        return app;
    }

    public void setApp(String app)
    {
        this.app = app;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }

    public List<Event> getEvents()
    {
        return events;
    }

    public void setEvents(List<Event> events)
    {
        this.events = events;
    }

    /**
     * 单条上报事件。错误事件使用 type/message/stack/route/url/source；
     * 指标事件使用 metric/value。两套字段共用该结构以减少前端序列化分支。
     */
    public static class Event
    {
        /** error / vital */
        private String kind;

        /** 错误类型：vue / window / promise / resource */
        private String type;

        /** 错误摘要（已截断） */
        private String message;

        /** 堆栈（已截断） */
        private String stack;

        /** 归一化路由 */
        private String route;

        /** 页面地址（去查询串） */
        private String url;

        /** 组件名或资源地址等附加定位信息 */
        private String source;

        /** Web Vitals 指标名：lcp / fcp / ttfb / inp / cls */
        private String metric;

        /** Web Vitals 数值（ms 或无量纲得分） */
        private Double value;

        /** 前端采集时间戳（毫秒） */
        private Long ts;

        public String getKind()
        {
            return kind;
        }

        public void setKind(String kind)
        {
            this.kind = kind;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public String getStack()
        {
            return stack;
        }

        public void setStack(String stack)
        {
            this.stack = stack;
        }

        public String getRoute()
        {
            return route;
        }

        public void setRoute(String route)
        {
            this.route = route;
        }

        public String getUrl()
        {
            return url;
        }

        public void setUrl(String url)
        {
            this.url = url;
        }

        public String getSource()
        {
            return source;
        }

        public void setSource(String source)
        {
            this.source = source;
        }

        public String getMetric()
        {
            return metric;
        }

        public void setMetric(String metric)
        {
            this.metric = metric;
        }

        public Double getValue()
        {
            return value;
        }

        public void setValue(Double value)
        {
            this.value = value;
        }

        public Long getTs()
        {
            return ts;
        }

        public void setTs(Long ts)
        {
            this.ts = ts;
        }
    }
}
