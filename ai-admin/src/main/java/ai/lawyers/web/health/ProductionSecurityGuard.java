package ai.lawyers.web.health;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * P0-3（V2.58）：生产环境默认弱口令 fail-fast 启动自检。
 *
 * <p>启用条件（满足其一）：① Spring profile 含 {@code prod}；
 * ② 配置 {@code app.security.guard=true}（环境变量 {@code APP_SECURITY_GUARD}）。
 * 默认关闭，本地/docker 开发环境零影响；生产部署须显式开启。</p>
 *
 * <p>命中以下任一默认值/空值即拒绝启动：JWT {@code token.secret}、
 * 主库密码 root、Druid 控制台开启且仍为默认口令、短信上行回调签名/token 均未配置、
 * ESL/AMI 网关口令仍为默认值、Redis 未设密码、外呼模拟接通仍开启、
 * 凭据加密根密钥 {@code APP_SECRET_KEY} 缺失。
 * {@code call.pbx.media.auth-key} 为空不阻断（仅限完全隔离内网），但强 WARN 告警。</p>
 */
@Component
public class ProductionSecurityGuard implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(ProductionSecurityGuard.class);

    /** JWT 默认密钥（docker-compose.yml 的 TOKEN_SECRET 兜底值，两者必须保持一致） */
    public static final String DEFAULT_TOKEN_SECRET =
            "Z2xwLWExMmwtbGF3eWVycy1qd3Qtc2VjcmV0LWtleS0yMDI2LWNoYW5nZS1tZS1pbi1wcm9kLTA5ODc2NTQz";

    /**
     * P3-② 裸机 {@code java -jar} 路径的 application.yml 兜底密钥。
     *
     * <p>历史上这里写作 {@code ${TOKEN_SECRET:+h5L5...}}（Shell 语法），Spring 只按 {@code :} 切分默认值，
     * 未注入时实际取到带前导 {@code +} 的串，与 Compose 兜底值不同，导致 guard 漏检。现已改回
     * {@code ${TOKEN_SECRET:h5L5...}} 并把该值纳入违规判定；带前导 {@code +} 的历史写法同样视为违规，
     * 防止再有人改回 Shell 语法后重新出现漏检。</p>
     */
    public static final String DEFAULT_TOKEN_SECRET_YML =
            "h5L5r+irKjj1DKlQKkO0yE+cVAB/VT5O8Zq+MbIGSWwYSEltV2q3Ke6xjZayQlscgoJ2jZC4jhJ+P414YNX1g==";
    public static final String DEFAULT_DB_PASSWORD = "root";
    public static final String DEFAULT_DRUID_CONSOLE_PASSWORD = "Druid@2026!ChangeMe";
    public static final String DEFAULT_CALLBACK_SIGN_SECRET = "ai-lawyers-callback-dev-secret-change-me-2026";
    /** FreeSWITCH ESL 默认口令（application.yml / docker-compose.yml 兜底值） */
    public static final String DEFAULT_ESL_PASSWORD = "ClueCon";
    /** Asterisk AMI 默认口令（application.yml / docker-compose.yml 兜底值） */
    public static final String DEFAULT_AMI_PASSWORD = "amp111";
    /** 凭据加密开发兜底密钥（SecretCryptoUtils / PiiCryptoUtils 共用） */
    public static final String DEFAULT_APP_SECRET_KEY = "ai-lawyers-dev-secret-change-me-in-production-2026";

    private final Environment environment;

    public ProductionSecurityGuard(Environment environment)
    {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        if (!enforcementEnabled(environment.getActiveProfiles(),
                environment.getProperty("app.security.guard")))
        {
            return;
        }
        Function<String, String> props = environment::getProperty;
        List<String> violations = findViolations(props);
        if (!violations.isEmpty())
        {
            throw new IllegalStateException("生产安全自检未通过，拒绝启动（请通过环境变量注入强配置后重试）：\n  - "
                    + String.join("\n  - ", violations));
        }
        for (String warning : findWarnings(props))
        {
            log.warn("[生产安全自检] {}", warning);
        }
        log.info("[生产安全自检] JWT/数据库/Redis/Druid/网关口令、回调鉴权与根密钥检查全部通过");
    }

    /**
     * 是否启用强制自检：profile 含 prod 或 app.security.guard=true。
     */
    public static boolean enforcementEnabled(String[] activeProfiles, String guardFlag)
    {
        if (activeProfiles != null)
        {
            for (String profile : activeProfiles)
            {
                if ("prod".equalsIgnoreCase(profile))
                {
                    return true;
                }
            }
        }
        return "true".equalsIgnoreCase(guardFlag);
    }

    /**
     * 收集阻断性违规项（包级可见便于单测，属性键与 yml 松散绑定一致）。
     */
    static List<String> findViolations(Function<String, String> props)
    {
        List<String> violations = new ArrayList<>();

        String tokenSecret = props.apply("token.secret");
        if (isBlank(tokenSecret) || DEFAULT_TOKEN_SECRET.equals(tokenSecret) || DEFAULT_TOKEN_SECRET_YML.equals(tokenSecret)
                || ("+" + DEFAULT_TOKEN_SECRET_YML).equals(tokenSecret))
        {
            violations.add("token.secret 为空或仍为内置默认值（Compose 与裸机 yml 两套兜底均已禁用），须经 TOKEN_SECRET 注入 >=32 字节随机密钥");
        }

        String dbPassword = props.apply("spring.datasource.druid.master.password");
        if (isBlank(dbPassword) || DEFAULT_DB_PASSWORD.equals(dbPassword))
        {
            violations.add("数据库主库密码为空或仍为弱口令 root，须经 DB_PASSWORD 注入强密码");
        }

        boolean consoleEnabled = Boolean.parseBoolean(
                props.apply("spring.datasource.druid.statViewServlet.enabled"));
        if (consoleEnabled)
        {
            String consolePassword = props.apply(
                    "spring.datasource.druid.statViewServlet.login-password");
            if (isBlank(consolePassword) || DEFAULT_DRUID_CONSOLE_PASSWORD.equals(consolePassword))
            {
                violations.add("Druid 控制台已开启但口令为空或仍为默认值，须经 DRUID_CONSOLE_PASSWORD 注入强口令"
                        + "（或置 DRUID_CONSOLE_ENABLED=false 关闭控制台）");
            }
        }

        // P3-⑩：短信上行回调不得零鉴权（签名与 token 双空时 UpstreamAuthVerifier 在开发环境放行）
        boolean signReady = Boolean.parseBoolean(props.apply("call.callback.sign-enabled"))
                && !isBlank(props.apply("call.callback.sign-secret"))
                && !DEFAULT_CALLBACK_SIGN_SECRET.equals(props.apply("call.callback.sign-secret"));
        boolean tokenReady = !isBlank(props.apply("call.sms.upstream.token"));
        if (!signReady && !tokenReady)
        {
            violations.add("短信上行回调鉴权未配置：call.sms.upstream.token 为空且回调签名未启用/密钥为空或默认值，"
                    + "须经 SMS_UPSTREAM_TOKEN 注入，或开启 CALLBACK_SIGN_ENABLED=true 并注入 CALLBACK_SIGN_SECRET");
        }

        // P3-③：FreeSWITCH ESL / Asterisk AMI 网关口令不得沿用内置默认值
        String eslPassword = props.apply("call.gateway.freeswitch.eslPassword");
        if (isBlank(eslPassword) || DEFAULT_ESL_PASSWORD.equals(eslPassword))
        {
            violations.add("FreeSWITCH ESL 口令为空或仍为默认值 ClueCon，须经 ESL_PASSWORD 注入强口令");
        }
        String amiPassword = props.apply("call.gateway.asterisk.amiPassword");
        if (isBlank(amiPassword) || DEFAULT_AMI_PASSWORD.equals(amiPassword))
        {
            violations.add("Asterisk AMI 口令为空或仍为默认值 amp111，须经 AMI_PASSWORD 注入强口令");
        }

        // P3-①：Redis 无 requirepass 时直接拒绝启动
        if (isBlank(props.apply("spring.data.redis.password")))
        {
            violations.add("Redis 未设置密码（spring.data.redis.password 为空），须经 REDIS_PASSWORD 注入强密码并开启 requirepass");
        }

        // P3-⑤：生产环境不得模拟接通，否则会写入虚假话单与统计
        if (Boolean.parseBoolean(props.apply("call.outbound.simulateAnswer")))
        {
            violations.add("外呼模拟接通已开启（call.outbound.simulateAnswer=true），生产环境会生成虚假话单/统计，"
                    + "须经 CALL_OUTBOUND_SIMULATEANSWER=false 关闭");
        }

        // P3-⑧：凭据加密根密钥缺失时 SecretCryptoUtils 会静默回落到开发密钥
        String appSecretKey = props.apply("APP_SECRET_KEY");
        if (isBlank(appSecretKey))
        {
            appSecretKey = props.apply("app.secret.key");
        }
        if (isBlank(appSecretKey) || DEFAULT_APP_SECRET_KEY.equals(appSecretKey))
        {
            violations.add("凭据加密根密钥缺失或仍为开发兜底值，须经环境变量 APP_SECRET_KEY（或 -Dapp.secret.key）"
                    + "注入 >=32 字节随机串");
        }
        return violations;
    }

    /**
     * 收集非阻断性告警项。
     */
    static List<String> findWarnings(Function<String, String> props)
    {
        List<String> warnings = new ArrayList<>();
        if (isBlank(props.apply("call.pbx.media.auth-key")))
        {
            warnings.add("PBX 媒体 fork 共享密钥为空（不校验来源），仅限完全隔离内网 + IP 白名单场景；"
                    + "否则须经 CALL_PBX_MEDIA_AUTH_KEY 注入强随机密钥");
        }
        String callbackSecret = props.apply("call.callback.sign-secret");
        if (isBlank(callbackSecret) || DEFAULT_CALLBACK_SIGN_SECRET.equals(callbackSecret))
        {
            warnings.add("网关回调签名密钥为空或仍为内置默认值，须经 CALLBACK_SIGN_SECRET 注入 >=32 字节随机串");
        }
        return warnings;
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
