package ai.lawyers.system.service.impl.lawyers.sms;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        // 单号码日发送上限（防骚扰）
        Integer dailyLimit = config.getDailyLimit();
        if (dailyLimit != null && dailyLimit > 0)
        {
            int todayCount = logMapper.selectTodayCountByPhone(phone);
            if (todayCount >= dailyLimit)
            {
                return fail(phone, templateId, config.getConfigId(),
                        "超过单号码日发送上限(" + dailyLimit + ")", params, sessionId, recordId);
            }
        }

        // 渲染模板变量
        String content = renderContent(template.getContent(), params);

        // 选择供应商并发送
        ISmsProvider provider = resolveProvider(config.getProvider());
        SmsRequest request = new SmsRequest();
        request.setPhone(phone);
        request.setSignName(config.getSignName());
        request.setTemplateCode(template.getProviderTemplateCode());
        request.setContent(content);

        try
        {
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

    private ISmsProvider resolveProvider(String providerCode)
    {
        if (StringUtils.isNotEmpty(providerCode))
        {
            ISmsProvider provider = smsProviders.get(providerCode.toLowerCase() + "SmsProvider");
            if (provider != null)
            {
                return provider;
            }
        }
        return smsProviders.get("mockSmsProvider");
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
