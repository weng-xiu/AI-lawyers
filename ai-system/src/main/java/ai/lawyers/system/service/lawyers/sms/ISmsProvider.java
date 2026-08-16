package ai.lawyers.system.service.lawyers.sms;

import ai.lawyers.system.domain.lawyers.sms.SmsRequest;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;

/**
 * 短信供应商统一接口。与语音引擎 Provider 模式一致，按 provider 编码选择实现。
 *
 * @author ai-lawyers
 */
public interface ISmsProvider
{
    /** 供应商编码：mock / aliyun / tencent */
    String code();

    /** 发送短信（内容已渲染好变量） */
    SmsResult send(SmsRequest request);
}
