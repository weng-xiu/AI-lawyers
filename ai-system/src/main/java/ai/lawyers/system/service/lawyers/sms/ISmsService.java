package ai.lawyers.system.service.lawyers.sms;

import java.util.Map;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;

/**
 * 短信发送核心接口。
 *
 * @author ai-lawyers
 */
public interface ISmsService
{
    /**
     * 发送短信：加载模板/通道、日限流、渲染模板变量、调用供应商、写发送记录。
     *
     * @param phone      接收号码
     * @param templateId 模板ID
     * @param params     模板变量
     * @param sessionId  IVR会话ID（可空）
     * @param recordId   通话记录ID（可空）
     */
    SmsResult send(String phone, Long templateId, Map<String, String> params,
                   String sessionId, Long recordId);
}
