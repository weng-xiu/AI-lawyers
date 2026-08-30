package ai.lawyers.system.service.impl.lawyers.sms;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.sms.AiSmsConfig;
import ai.lawyers.system.domain.lawyers.sms.AiSmsLog;
import ai.lawyers.system.domain.lawyers.sms.AiSmsTemplate;
import ai.lawyers.system.domain.lawyers.sms.SmsRequest;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsConfigMapper;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsLogMapper;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsTemplateMapper;
import ai.lawyers.system.service.lawyers.sms.ISmsProvider;
import ai.lawyers.system.service.lawyers.sms.ISmsService;

/**
 * 短信发送实现：模板/通道加载、日限流、变量渲染、供应商选择、发送留痕。
 *
 * @author ai-lawyers
 */
@Service
public class SmsServiceImpl implements ISmsService
{
    private static final Logger log = LoggerFactory.getLogger(SmsServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private AiSmsTemplateMapper templateMapper;

    @Autowired
    private AiSmsConfigMapper configMapper;

    @Autowired
    private AiSmsLogMapper logMapper;

    @Autowired
    private Map<String, ISmsProvider> smsProviders;

    @Autowired
    private RedisCache redisCache;

    /** Redis 单号码日限流 key 前缀 */
    private static final String DAILY_LIMIT_KEY_PREFIX = "sms:limit:";

    @Override
    public SmsResult send(String phone, Long templateId, Map<String, String> params,
                          String sessionId, Long recordId)
    {
        AiSmsTemplate template = templateMapper.selectAiSmsTemplateByTemplateId(templateId);
        if (template == null)
        {
            return fail(phone, templateId, null, "短信模板不存在", params, sessionId, recordId);
        }
        AiSmsConfig config = configMapper.selectAiSmsConfigByConfigId(template.getConfigId());
        if (config == null || !"1".equals(config.getStatus()))
        {
            return fail(phone, templateId, template.getConfigId(), "短信通道不存在或已停用",
                    params, sessionId, recordId);
        }

        // 单号码日发送上限（防骚扰）。C6：改为 Redis INCR 原子计数，避免"先查 count 后发送"的并发竞态导致超发；
        // Redis 不可用时降级为 DB 当日计数兜底（弱一致，但保证功能可用）。
        Integer dailyLimit = config.getDailyLimit();
        if (dailyLimit != null && dailyLimit > 0 && !tryAcquireDailyQuota(phone, dailyLimit))
        {
            return fail(phone, templateId, config.getConfigId(),
                    "超过单号码日发送上限(" + dailyLimit + ")", params, sessionId, recordId);
        }

        // 渲染模板变量
        String content = renderContent(template.getContent(), params);

        SmsRequest request = new SmsRequest();
        request.setPhone(phone);
        request.setSignName(config.getSignName());
        request.setTemplateCode(template.getProviderTemplateCode());
        request.setContent(content);

        try
        {
            // 选择供应商并发送（resolveProvider 在供应商缺失时抛 ServiceException，一并留痕）
            ISmsProvider provider = resolveProvider(config.getProvider());
            SmsResult result = provider.send(request);
            if (result == null)
            {
                result = SmsResult.fail("供应商返回空结果");
            }
            insertLog(phone, templateId, config.getConfigId(), params, content,
                    result.isSuccess() ? "1" : "2",
                    result.getMsgId(), result.getMessage(), sessionId, recordId);
            result.setContent(content);
            return result;
        }
        catch (Exception e)
        {
            log.warn("短信发送异常 phone={} error={}", phone, e.getMessage());
            insertLog(phone, templateId, config.getConfigId(), params, content,
                    "2", null, e.getMessage(), sessionId, recordId);
            return SmsResult.fail(e.getMessage());
        }
    }

    /**
     * 原子抢占单号码当日发送配额。
     * C6：以 Redis INCR 计数，首次计数设置到当日 24 点过期；返回 true 表示获得本次发送配额。
     * Redis 异常时降级为 DB 当日计数（selectTodayCountByPhone）兜底，保证服务可用性。
     */
    private boolean tryAcquireDailyQuota(String phone, int dailyLimit)
    {
        String key = DAILY_LIMIT_KEY_PREFIX + phone + ":" + LocalDate.now();
        try
        {
            Long count = redisCache.redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L)
            {
                // 首次计数：设置当日 24 点过期，避免 key 无限堆积
                LocalDateTime midnight = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIN);
                long secondsToMidnight = Duration.between(LocalDateTime.now(), midnight).getSeconds();
                redisCache.expire(key, secondsToMidnight > 0 ? secondsToMidnight : 1);
            }
            return count != null && count <= dailyLimit;
        }
        catch (Exception e)
        {
            log.warn("Redis 日限流计数失败，降级为 DB 当日计数兜底 phone={} error={}", phone, e.getMessage());
            try
            {
                int todayCount = logMapper.selectTodayCountByPhone(phone);
                return todayCount < dailyLimit;
            }
            catch (Exception ex)
            {
                // Redis 与 DB 均不可用时，为不阻断业务放行（宁可漏限不可误停），但需留痕告警
                log.error("短信限流降级兜底也失败，本次放行 phone={} error={}", phone, ex.getMessage());
                return true;
            }
        }
    }

    private ISmsProvider resolveProvider(String providerCode)
    {
        if (StringUtils.isNotEmpty(providerCode))
        {
            ISmsProvider provider = smsProviders.get(providerCode.toLowerCase() + "SmsProvider");
            if (provider != null)
            {
                return provider;
            }
            // L4：配置了供应商但无对应实现，属配置/部署错误，必须显式失败，避免静默走 mock 造成"假发送"
            throw new ServiceException("短信供应商未配置或未启用：" + providerCode
                    + "，请检查短信通道配置与供应商实现");
        }
        // 未指定供应商时，mock 仅作为开发联调兜底；生产应在通道中显式配置真实供应商
        ISmsProvider mock = smsProviders.get("mockSmsProvider");
        if (mock == null)
        {
            throw new ServiceException("未配置短信供应商，且无可用 mock 通道，短信无法发送");
        }
        log.warn("短信通道未指定供应商，回退 mockSmsProvider，仅可用于开发联调，生产环境请配置真实供应商");
        return mock;
    }

    private String renderContent(String templateContent, Map<String, String> params)
    {
        String content = templateContent == null ? "" : templateContent;
        if (params != null && !params.isEmpty())
        {
            for (Map.Entry<String, String> entry : params.entrySet())
            {
                String value = entry.getValue() == null ? "" : entry.getValue();
                content = content.replace("${" + entry.getKey() + "}", value);
            }
        }
        return content;
    }

    private SmsResult fail(String phone, Long templateId, Long configId, String reason,
                           Map<String, String> params, String sessionId, Long recordId)
    {
        insertLog(phone, templateId, configId, params, null, "2", null, reason, sessionId, recordId);
        return SmsResult.fail(reason);
    }

    private void insertLog(String phone, Long templateId, Long configId, Map<String, String> params,
                           String content, String sendStatus, String msgId, String failReason,
                           String sessionId, Long recordId)
    {
        AiSmsLog smsLog = new AiSmsLog();
        smsLog.setPhone(phone);
        smsLog.setTemplateId(templateId);
        smsLog.setConfigId(configId);
        smsLog.setParamsJson(toJson(params));
        smsLog.setContent(content);
        smsLog.setSendStatus(sendStatus);
        smsLog.setProviderMsgId(msgId);
        smsLog.setFailReason(failReason);
        smsLog.setSessionId(sessionId);
        smsLog.setRecordId(recordId);
        logMapper.insertAiSmsLog(smsLog);
    }

    private String toJson(Map<String, String> params)
    {
        if (params == null)
        {
            return null;
        }
        try
        {
            return MAPPER.writeValueAsString(params);
        }
        catch (Exception e)
        {
            return params.toString();
        }
    }
}
