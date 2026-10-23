package ai.lawyers.web.controller.lawyers;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.annotation.Anonymous;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.monitor.FrontendReportPayload;
import ai.lawyers.system.service.lawyers.monitor.FrontendMonitorService;

/**
 * P9 前端可观测上报端点。
 *
 * <p>前端用 {@code navigator.sendBeacon} 以 {@code text/plain} 发送 JSON：
 * 简单请求不触发 CORS 预检，页面卸载时也能可靠发出；因此后端按字符串接收后
 * 自行反序列化（不走 Jackson 消息转换器，避免 text/plain 与对象绑定冲突）。</p>
 *
 * <p>安全约束：{@code @Anonymous} 匿名开放但只做校验与指标埋点，不写业务库；
 * 请求体上限 64KB，字段白名单/截断/日志去重由
 * {@link FrontendMonitorService} 收口。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/monitor/frontend")
public class FrontendMonitorController
{
    private static final Logger log = LoggerFactory.getLogger(FrontendMonitorController.class);

    /** 请求体上限：避免异常客户端用超大 JSON 放大内存与日志 */
    private static final long MAX_BODY_BYTES = 64L * 1024L;

    @Autowired
    private FrontendMonitorService frontendMonitorService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 接收前端错误与 Web Vitals 批量上报。
     *
     * @return data 为实际受理的事件数；格式非法返回 400，超限返回 413
     */
    @Anonymous
    @PostMapping(value = "/report", consumes = MediaType.TEXT_PLAIN_VALUE)
    public AjaxResult report(@RequestBody(required = false) String body, HttpServletRequest request)
    {
        if (request.getContentLengthLong() > MAX_BODY_BYTES)
        {
            return AjaxResult.error(413, "上报内容过大");
        }
        if (body == null || body.isEmpty())
        {
            return AjaxResult.error(400, "上报内容为空");
        }
        FrontendReportPayload payload;
        try
        {
            payload = objectMapper.readValue(body, FrontendReportPayload.class);
        }
        catch (Exception e)
        {
            log.debug("[P9前端上报] 非法 JSON，ip={}，已丢弃", request.getRemoteAddr());
            return AjaxResult.error(400, "上报内容非法");
        }
        int accepted = frontendMonitorService.report(payload, request.getRemoteAddr());
        return AjaxResult.success(accepted);
    }
}
