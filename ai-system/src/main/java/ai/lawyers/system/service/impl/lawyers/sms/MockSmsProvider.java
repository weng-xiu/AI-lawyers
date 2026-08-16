package ai.lawyers.system.service.impl.lawyers.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.sms.SmsRequest;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.service.lawyers.sms.ISmsProvider;

/**
 * 本地模拟短信供应商：不产生真实短信，仅记录日志并返回回执ID，用于无凭证环境联调。
 *
 * @author ai-lawyers
 */
@Service("mockSmsProvider")
public class MockSmsProvider implements ISmsProvider
{
    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    @Override
    public String code()
    {
        return "mock";
    }

    @Override
    public SmsResult send(SmsRequest request)
    {
        log.info("[MOCK SMS] 发送至 {} 签名={} 模板CODE={} 内容={}",
                request.getPhone(), request.getSignName(), request.getTemplateCode(), request.getContent());
        return SmsResult.success("MOCK" + System.currentTimeMillis(), request.getContent());
    }
}
