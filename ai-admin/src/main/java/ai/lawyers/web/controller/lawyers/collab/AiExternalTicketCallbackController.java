package ai.lawyers.web.controller.lawyers.collab;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Anonymous;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.DesensitizedUtil;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.CallbackSignUtils;
import ai.lawyers.system.domain.lawyers.AiTicketTransfer;
import ai.lawyers.system.service.lawyers.IAiTicketTransferService;

/**
 * F3 跨域转办对外回调/来单接口（匿名 + 签名 + 幂等 + 频控）。
 *
 * <p>安全收口：</p>
 * <ul>
 *   <li>HMAC-SHA256：X-Callback-Timestamp / X-Callback-Sign，指纹为 idempotentKey
 *       （回退 externalType:externalTicketNo），±5 分钟窗口防重放；</li>
 *   <li>无法携带自定义头时允许 X-Collab-Token（header/query/body）兜底；</li>
 *   <li>生产必须开启签名或配置令牌（{@code call.collab.sign-enabled=true}），未配置时告警放行仅限联调；</li>
 *   <li>同指纹每分钟 30 次频控，防伪造回调刷库；</li>
 *   <li>业务层按 idempotentKey / 条线+外部工单号双重幂等。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/external/ticket")
public class AiExternalTicketCallbackController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(AiExternalTicketCallbackController.class);

    private static final String RATE_KEY_PREFIX = "collab:cb:rl:";

    private static final int RATE_LIMIT_PER_MIN = 30;

    @Autowired
    private IAiTicketTransferService ticketTransferService;

    @Autowired(required = false)
    private RedisCache redisCache;

    @Value("${call.collab.sign-enabled:false}")
    private boolean signEnabled;

    @Value("${call.collab.sign-secret:}")
    private String signSecret;

    @Value("${call.collab.token:}")
    private String collabToken;

    /**
     * 外部机构受理结果回写。
     * 报文：{ idempotentKey, externalType, externalTicketNo, externalStatus, ... }
     */
    @Anonymous
    @PostMapping("/callback")
    public AjaxResult callback(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request)
    {
        if (body == null || body.isEmpty())
        {
            return AjaxResult.error("请求体不能为空");
        }
        String idempotentKey = pick(body, "idempotentKey", "idempotent_key");
        String externalType = pick(body, "externalType", "external_type");
        String externalTicketNo = pick(body, "externalTicketNo", "external_ticket_no");
        String externalStatus = pick(body, "externalStatus", "external_status", "status");
        if (StringUtils.isEmpty(externalStatus))
        {
            return AjaxResult.error("缺少 externalStatus 字段");
        }
        if (StringUtils.isEmpty(idempotentKey)
                && (StringUtils.isEmpty(externalType) || StringUtils.isEmpty(externalTicketNo)))
        {
            return AjaxResult.error("缺少幂等键或条线+外部工单号");
        }
        String fingerprint = StringUtils.isNotEmpty(idempotentKey)
                ? idempotentKey : externalType + ":" + externalTicketNo;
        if (!verify(request, body, fingerprint))
        {
            log.warn("[F3] 转办回调校验失败 fp={} ip={}", fingerprint, request.getRemoteAddr());
            return AjaxResult.error(403, "回调校验失败");
        }
        if (!tryAcquire(fingerprint))
        {
            return AjaxResult.error(429, "请求频率超限");
        }
        AiTicketTransfer transfer = ticketTransferService.handleCallback(idempotentKey, externalType,
                externalTicketNo, externalStatus, body.toString());
        if (transfer == null)
        {
            // 定位不到对应流水：返回 404 语义错误，便于外部侧排查（不泄露工单细节）
            return AjaxResult.error("未找到对应转办记录");
        }
        return success();
    }

    /**
     * 外部渠道转入来单（如 12345 转来法律诉求）。
     */
    @Anonymous
    @PostMapping("/receive")
    public AjaxResult receive(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request)
    {
        if (body == null || body.isEmpty())
        {
            return AjaxResult.error("请求体不能为空");
        }
        String externalType = pick(body, "externalType", "external_type");
        String externalTicketNo = pick(body, "externalTicketNo", "external_ticket_no");
        if (StringUtils.isEmpty(externalType) || StringUtils.isEmpty(externalTicketNo))
        {
            return AjaxResult.error("缺少 externalType/externalTicketNo 字段");
        }
        String idempotentKey = pick(body, "idempotentKey", "idempotent_key");
        String fingerprint = StringUtils.isNotEmpty(idempotentKey)
                ? idempotentKey : externalType + ":" + externalTicketNo;
        if (!verify(request, body, fingerprint))
        {
            log.warn("[F3] 外部来单校验失败 fp={} ip={}", fingerprint, request.getRemoteAddr());
            return AjaxResult.error(403, "来单校验失败");
        }
        if (!tryAcquire(fingerprint))
        {
            return AjaxResult.error(429, "请求频率超限");
        }
        String callerNumber = pick(body, "callerNumber", "caller_number", "phone", "mobile");
        AiTicketTransfer transfer = ticketTransferService.receiveInbound(
                externalType, externalTicketNo, idempotentKey,
                callerNumber,
                pick(body, "callerName", "caller_name"),
                pick(body, "title"),
                pick(body, "content", "text", "message"),
                pick(body, "priority"),
                body.toString());
        log.info("[F3] 外部来单登记成功 type={} extNo={} caller={}",
                externalType, externalTicketNo,
                StringUtils.isNotEmpty(callerNumber) ? DesensitizedUtil.mobilePhone(callerNumber) : "-");
        return AjaxResult.success("来单已受理", transfer.getTicketNo());
    }

    /**
     * 签名优先、令牌兜底；两者均未配置时告警放行（仅限开发联调）。
     */
    private boolean verify(HttpServletRequest request, Map<String, Object> body, String fingerprint)
    {
        if (!signEnabled && StringUtils.isEmpty(collabToken))
        {
            log.warn("[F3] 转办回调未配置签名/令牌，生产环境必须设置 call.collab.sign-enabled=true");
            return true;
        }
        if (signEnabled)
        {
            String ts = request.getHeader("X-Callback-Timestamp");
            String sign = request.getHeader("X-Callback-Sign");
            if (CallbackSignUtils.verify(signSecret, fingerprint, ts, sign))
            {
                return true;
            }
        }
        String token = request.getHeader("X-Collab-Token");
        if (StringUtils.isEmpty(token))
        {
            token = request.getParameter("token");
        }
        if (StringUtils.isEmpty(token))
        {
            token = body.get("token") == null ? null : String.valueOf(body.get("token"));
        }
        return StringUtils.isNotEmpty(collabToken) && collabToken.equals(token);
    }

    /** 同指纹频控（Redis INCR + 60s；Redis 故障放行并告警） */
    private boolean tryAcquire(String fingerprint)
    {
        if (redisCache == null)
        {
            return true;
        }
        String key = RATE_KEY_PREFIX + fingerprint;
        try
        {
            Long count = redisCache.redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L)
            {
                redisCache.expire(key, 60, TimeUnit.SECONDS);
            }
            return count == null || count <= RATE_LIMIT_PER_MIN;
        }
        catch (Exception e)
        {
            log.warn("[F3] 回调频控 Redis 异常，本次放行 error={}", e.getMessage());
            return true;
        }
    }

    private String pick(Map<String, Object> body, String... keys)
    {
        for (String key : keys)
        {
            Object v = body.get(key);
            if (v != null && StringUtils.isNotEmpty(String.valueOf(v)))
            {
                return String.valueOf(v);
            }
        }
        return null;
    }
}
