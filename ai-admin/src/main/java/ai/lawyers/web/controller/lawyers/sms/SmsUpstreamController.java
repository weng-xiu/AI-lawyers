package ai.lawyers.web.controller.lawyers.sms;

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
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;
import ai.lawyers.system.service.lawyers.sms.UpstreamAuthVerifier;

/**
 * W4：短信上行回调——用户回复"T/TD/退订"自动写入退订名单（list_type=3），
 * 实现"一处退订，呼叫+短信均禁止"（《通信短信息服务管理规定》退订合规收口）。
 *
 * <p>兼容主流短信服务商上行报文字段：阿里云（phone_number/content）、腾讯云（mobile/text）、
 * 中国移动（msgContent/mobile）等；收到退订指令后调 {@link IAiCallBlacklistService#addUnsubscribe}。</p>
 *
 * <p>安全：优先 HMAC-SHA256 签名校验（X-Callback-Timestamp / X-Callback-Sign，与网关回调一致）；
 * 服务商无法携带自定义 header 时可用 token 校验（{@code call.sms.upstream.token}，header/query/body 三通道）。
 * 生产环境必须配置其一，否则启动后本接口对未校验请求告警放行（仅开发联调用）。</p>
 *
 * <p>频率控制：同号码每分钟退订频控（{@code call.sms.upstream.rate-limit-per-min}，默认 10），防恶意刷退订。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/sms/upstream")
public class SmsUpstreamController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(SmsUpstreamController.class);

    /** 退订指令关键字（忽略首尾空白与大小写）：T / TD / 退订 / TD退订 / T退订 / 停止推送 / 拒收 / STOP */
    private static final String UNSUBSCRIBE_REGEX = "(?i)^\\s*(T|TD|TD退订|T退订|退订|停止推送|拒收|STOP)\\s*$";

    /** 同号码退订频控 key 前缀 */
    private static final String RATE_KEY_PREFIX = "sms:unsub:rl:";

    @Autowired(required = false)
    private IAiCallBlacklistService blacklistService;

    @Autowired(required = false)
    private RedisCache redisCache;

    @Value("${call.sms.upstream.enabled:true}")
    private boolean upstreamEnabled;

    @Value("${call.sms.upstream.token:}")
    private String upstreamToken;

    /** 签名开关复用网关回调配置（生产开启） */
    @Value("${call.callback.sign-enabled:false}")
    private boolean signEnabled;

    @Value("${call.callback.sign-secret:}")
    private String signSecret;

    @Value("${call.sms.upstream.rate-limit-per-min:10}")
    private int rateLimitPerMin;

    /**
     * 短信上行回调入口（短信服务商推送地址：{@code POST /lawyers/sms/upstream}）。
     *
     * <p>请求体示例（阿里云）：{ "phone_number":"13800138000", "content":"T" }</p>
     * <p>请求体示例（腾讯云）：{ "mobile":"13800138000", "text":"TD" }</p>
     */
    @Anonymous
    @PostMapping
    public AjaxResult receive(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request)
    {
        // 开关关闭时静默成功，避免服务商重试风暴
        if (!upstreamEnabled)
        {
            return success();
        }
        if (body == null || body.isEmpty())
        {
            return AjaxResult.error("请求体不能为空");
        }
        String phone = pick(body, "phone_number", "phone", "mobile", "phoneNumber", "tel");
        String content = pick(body, "content", "text", "message", "msgContent", "smsContent");
        if (StringUtils.isEmpty(phone) || StringUtils.isEmpty(content))
        {
            return AjaxResult.error("缺少 phone/content 字段");
        }
        // 安全校验：签名优先，其次 token
        if (!verify(request, body, phone, content))
        {
            log.warn("短信上行回调校验失败 phone={} ip={}", phone, request.getRemoteAddr());
            return AjaxResult.error(403, "上行回调校验失败");
        }
        // 仅退订指令才处理；其他上行内容（如关键字查询）直接成功忽略
        if (!content.trim().matches(UNSUBSCRIBE_REGEX))
        {
            return success();
        }
        // 同号码频控，防恶意刷退订
        if (rateLimitPerMin > 0 && !tryAcquire(phone))
        {
            log.warn("短信上行退订触发频控 phone={}", phone);
            return AjaxResult.error(429, "退订频率超限");
        }
        boolean ok = blacklistService != null && blacklistService.addUnsubscribe(phone, "短信回复T退订");
        log.info("短信上行退订结果 phone={} success={}", phone, ok);
        // 无论结果如何均返回成功，避免服务商重试造成重复退订
        return success();
    }

    /**
     * 安全校验：生产必须开启 HMAC 签名或配置 token 二者其一（双配时任一通道通过即放行）。
     * 决策逻辑见 {@link UpstreamAuthVerifier}，本方法仅负责请求参数提取与未配置告警。
     *
     * @return true 校验通过
     */
    private boolean verify(HttpServletRequest request, Map<String, Object> body, String phone, String content)
    {
        if (!signEnabled && StringUtils.isEmpty(upstreamToken))
        {
            log.warn("短信上行回调未配置签名/令牌校验，生产环境必须设置 CALLBACK_SIGN_ENABLED=true 或 SMS_UPSTREAM_TOKEN");
            return true;
        }
        return UpstreamAuthVerifier.verify(signEnabled, signSecret, upstreamToken,
                request.getHeader("X-Upstream-Token"), request.getParameter("token"), body.get("token"),
                request.getHeader("X-Callback-Timestamp"), request.getHeader("X-Callback-Sign"),
                phone, content);
    }

    /** 同号码退订频控（Redis INCR + 60s 过期；Redis 故障放行并告警） */
    private boolean tryAcquire(String phone)
    {
        String key = RATE_KEY_PREFIX + phone;
        try
        {
            Long count = redisCache.redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L)
            {
                redisCache.expire(key, 60, TimeUnit.SECONDS);
            }
            return count == null || count <= rateLimitPerMin;
        }
        catch (Exception e)
        {
            log.warn("退订频控 Redis 异常，本次放行 phone={} error={}", phone, e.getMessage());
            return true;
        }
    }

    /** 兼容多服务商上行字段名，按序取首个非空值 */
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
