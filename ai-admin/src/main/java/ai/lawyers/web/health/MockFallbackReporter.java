package ai.lawyers.web.health;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.sms.ISmsProvider;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;

/**
 * P3-⑪：生产启动时显式列出当前实际落到 Mock 的短信供应商、语音网关与语音引擎。
 *
 * <p>与 {@link ProductionSecurityGuard} 共用启用条件（profile 含 {@code prod} 或
 * {@code app.security.guard=true}），仅在强制执行时输出，避免开发环境噪声。
 * SMS/Gateway 未配置真实实现时不再只是 WARN 后静默兜底，而是在启动日志集中暴露。</p>
 */
@Component
public class MockFallbackReporter implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(MockFallbackReporter.class);

    private static final String MOCK_SMS_CODE = "mock";
    private static final String SIMULATOR_VENDOR = "SIMULATOR";

    private final Environment environment;
    private final CallGatewayFactory gatewayFactory;
    private final Map<String, ISmsProvider> smsProviders;

    @Autowired
    public MockFallbackReporter(Environment environment, CallGatewayFactory gatewayFactory,
            Map<String, ISmsProvider> smsProviders)
    {
        this.environment = environment;
        this.gatewayFactory = gatewayFactory;
        this.smsProviders = smsProviders;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        if (!ProductionSecurityGuard.enforcementEnabled(environment.getActiveProfiles(),
                environment.getProperty("app.security.guard")))
        {
            return;
        }
        List<String> mocks = detect(environment::getProperty,
                gatewayFactory.getAll().keySet(),
                smsProviders.values().stream().map(ISmsProvider::code).collect(Collectors.toList()));
        if (mocks.isEmpty())
        {
            log.info("[生产安全自检] 未发现落到 Mock 的短信/网关/语音实现");
            return;
        }
        log.warn("[生产安全自检] 以下实现当前落到 Mock，请确认是否符合生产预期：\n  - {}",
                String.join("\n  - ", mocks));
    }

    /**
     * 根据配置与已注册实现推导 Mock 兜底清单（包级可见便于单测）。
     */
    static List<String> detect(Function<String, String> props, Set<String> gatewayVendors,
            Collection<String> smsCodes)
    {
        List<String> mocks = new ArrayList<>();

        if (smsCodes == null || smsCodes.isEmpty())
        {
            mocks.add("短信：未注册任何供应商实现，短信发送将失败");
        }
        else if (smsCodes.stream().allMatch(code -> MOCK_SMS_CODE.equalsIgnoreCase(code)))
        {
            mocks.add("短信：仅注册 Mock 供应商（mockSmsProvider），发送不会产生真实短信");
        }

        if (gatewayVendors == null || gatewayVendors.isEmpty())
        {
            mocks.add("语音网关：未注册任何适配器，外呼功能不可用");
        }
        else if (gatewayVendors.size() == 1
                && gatewayVendors.stream().anyMatch(SIMULATOR_VENDOR::equalsIgnoreCase))
        {
            mocks.add("语音网关：仅注册 SIMULATOR，外呼为模拟拨号（无真实呼叫）；"
                    + "线路 vendor 配错时也会回退到该模拟器");
        }

        String engine = normalize(props.apply("voice.engine"));
        if (engine.isEmpty() || "mock".equals(engine) || "ali".equals(engine) || "dianxin".equals(engine))
        {
            mocks.add("语音引擎：voice.engine=" + (engine.isEmpty() ? "未配置" : engine)
                    + "，TTS 使用 Mock（当前仅 dashscope 有真实 TTS 实现）");
        }
        else if ("dashscope".equals(engine) && isBlank(props.apply("voice.dashscope-api-key")))
        {
            mocks.add("语音引擎：voice.engine=dashscope 但 API Key 为空，TTS 调用将降级 Mock");
        }
        if (isBlank(props.apply("voice.asr-base-url")))
        {
            mocks.add("语音识别：voice.asr-base-url 为空，ASR 使用 Mock");
        }
        return mocks;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
