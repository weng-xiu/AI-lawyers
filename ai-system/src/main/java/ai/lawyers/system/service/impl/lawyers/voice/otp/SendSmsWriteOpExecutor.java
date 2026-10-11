package ai.lawyers.system.service.impl.lawyers.voice.otp;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.service.lawyers.sms.ISmsService;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpAction;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpExecutor;
import ai.lawyers.system.service.lawyers.voice.otp.WriteOpSession;

/**
 * P3-#12：代发短信执行器（白名单 {@link WriteOpAction#SEND_SMS}）。
 *
 * <p>payload 契约：{@code {"templateId": 12, "params": {"k": "v"}}}——
 * templateId 必填；params 模板变量可选。收件号码强制取会话锚定的
 * {@code callerNumber}（payload 不允许指定号码，防代发第三方）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class SendSmsWriteOpExecutor implements WriteOpExecutor
{
    private static final Logger log = LoggerFactory.getLogger(SendSmsWriteOpExecutor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired(required = false)
    private ISmsService smsService;

    /** 测试注入用 */
    void setSmsService(ISmsService smsService)
    {
        this.smsService = smsService;
    }

    @Override
    public WriteOpAction action()
    {
        return WriteOpAction.SEND_SMS;
    }

    @Override
    public ExecutionResult execute(WriteOpSession session)
    {
        if (smsService == null)
        {
            return ExecutionResult.fail("短信服务未就绪");
        }
        Long templateId;
        Map<String, String> params;
        try
        {
            JsonNode payload = MAPPER.readTree(session.getPayloadJson() == null
                    ? "{}" : session.getPayloadJson());
            if (!payload.hasNonNull("templateId") || !payload.get("templateId").canConvertToLong()
                    || payload.get("templateId").asLong() <= 0)
            {
                return ExecutionResult.fail("payload 缺少有效 templateId");
            }
            templateId = payload.get("templateId").asLong();
            params = MAPPER.convertValue(payload.path("params"),
                    MAPPER.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        }
        catch (Exception e)
        {
            return ExecutionResult.fail("payload JSON 非法: " + e.getMessage());
        }
        SmsResult r = smsService.send(session.getCallerNumber(), templateId, params,
                session.getSessionId(), session.getRecordId());
        if (r == null || !r.isSuccess())
        {
            return ExecutionResult.fail("短信发送失败: " + (r == null ? "无结果" : r.getMessage()));
        }
        log.info("机器人代发短信完成 phone=*** msgId={} templateId={}", r.getMsgId(), templateId);
        return ExecutionResult.ok("短信已发送", r.getMsgId());
    }
}
