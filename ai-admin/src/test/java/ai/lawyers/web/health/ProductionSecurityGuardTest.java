package ai.lawyers.web.health;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-3：生产安全自检单测——默认弱口令命中、强配置通过、auth-key 空仅告警、启用条件判定。
 */
public class ProductionSecurityGuardTest
{
    /** 构造开发默认配置（全默认值） */
    private Map<String, String> defaultProps()
    {
        Map<String, String> props = new HashMap<>();
        props.put("token.secret", ProductionSecurityGuard.DEFAULT_TOKEN_SECRET);
        props.put("spring.datasource.druid.master.password", "root");
        props.put("spring.datasource.druid.master.username", ProductionSecurityGuard.DEFAULT_DB_USERNAME);
        props.put("spring.datasource.druid.statViewServlet.enabled", "true");
        props.put("spring.datasource.druid.statViewServlet.login-password",
                ProductionSecurityGuard.DEFAULT_DRUID_CONSOLE_PASSWORD);
        props.put("call.pbx.media.auth-key", "");
        props.put("call.gateway.freeswitch.eslPassword", ProductionSecurityGuard.DEFAULT_ESL_PASSWORD);
        props.put("call.gateway.asterisk.amiPassword", ProductionSecurityGuard.DEFAULT_AMI_PASSWORD);
        props.put("spring.data.redis.password", "");
        props.put("call.outbound.simulateAnswer", "true");
        props.put("APP_SECRET_KEY", "");
        return props;
    }

    @Test
    public void defaultsProduceAllViolations()
    {
        List<String> violations = ProductionSecurityGuard.findViolations(defaultProps()::get);
        assertEquals(10, violations.size());
        assertTrue(violations.get(0).contains("token.secret"));
        assertTrue(violations.get(1).contains("数据库主库密码"));
        assertTrue(violations.get(2).contains("数据库账号"));
        assertTrue(violations.get(3).contains("Druid"));
        assertTrue(violations.get(4).contains("短信上行回调"));
        assertTrue(violations.get(5).contains("ESL"));
        assertTrue(violations.get(6).contains("AMI"));
        assertTrue(violations.get(7).contains("Redis"));
        assertTrue(violations.get(8).contains("模拟接通"));
        assertTrue(violations.get(9).contains("APP_SECRET_KEY"));
    }

    /** P3-②：Compose 路径——TOKEN_SECRET 未设置时注入的兜底值必须被拒绝 */
    @Test
    public void composeDefaultTokenSecretIsRejected()
    {
        Map<String, String> props = defaultProps();
        props.put("token.secret", ProductionSecurityGuard.DEFAULT_TOKEN_SECRET);

        List<String> violations = ProductionSecurityGuard.findViolations(props::get);
        assertTrue(violations.get(0).contains("token.secret"));
    }

    /** P3-②：裸机 java -jar 路径——application.yml 兜底值（含历史 `:+` 写法产生的带 + 值）必须被拒绝 */
    @Test
    public void bareMetalYmlDefaultTokenSecretIsRejected()
    {
        Map<String, String> props = defaultProps();
        props.put("token.secret", ProductionSecurityGuard.DEFAULT_TOKEN_SECRET_YML);
        assertTrue(ProductionSecurityGuard.findViolations(props::get).get(0).contains("token.secret"));

        // 回归：Shell 的 `${TOKEN_SECRET:+xxx}` 在 Spring 下未注入时会取到 "+xxx"，同样不得放行
        props.put("token.secret", "+" + ProductionSecurityGuard.DEFAULT_TOKEN_SECRET_YML);
        assertTrue(ProductionSecurityGuard.findViolations(props::get).get(0).contains("token.secret"));
    }

    @Test
    public void strongConfigAndConsoleDisabledPass()
    {
        Map<String, String> props = strongBaseProps();
        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");

        assertTrue(ProductionSecurityGuard.findViolations(props::get).isEmpty());
    }

    /** P3-⑩：短信上行回调零鉴权必须阻断启动；token 或有效签名任一配置即通过 */
    @Test
    public void smsUpstreamWithoutAuthIsRejected()
    {
        Map<String, String> props = strongBaseProps();
        List<String> violations = ProductionSecurityGuard.findViolations(props::get);
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("短信上行回调"));

        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");
        assertTrue(ProductionSecurityGuard.findViolations(props::get).isEmpty());

        // 改用签名通道：开关打开 + 强密钥（默认开发密钥不算有效配置）
        props.remove("call.sms.upstream.token");
        props.put("call.callback.sign-enabled", "true");
        props.put("call.callback.sign-secret", "strong-callback-sign-secret-32bytes");
        assertTrue(ProductionSecurityGuard.findViolations(props::get).isEmpty());

        // 默认开发密钥不得视为已配置
        props.put("call.callback.sign-secret", ProductionSecurityGuard.DEFAULT_CALLBACK_SIGN_SECRET);
        assertFalse(ProductionSecurityGuard.findViolations(props::get).isEmpty());
    }

    /** 构造除短信上行鉴权外全部合规的配置 */
    private Map<String, String> strongBaseProps()
    {
        Map<String, String> props = defaultProps();
        props.put("token.secret", "a-very-strong-random-jwt-secret-key-0123456789");
        props.put("spring.datasource.druid.master.password", "Str0ng!Db#Pass");
        props.put("spring.datasource.druid.master.username", "ai_law");
        props.put("spring.datasource.druid.statViewServlet.enabled", "false");
        props.put("call.gateway.freeswitch.eslPassword", "Str0ng!Esl#Pass");
        props.put("call.gateway.asterisk.amiPassword", "Str0ng!Ami#Pass");
        props.put("spring.data.redis.password", "Str0ng!Redis#Pass");
        props.put("call.outbound.simulateAnswer", "false");
        props.put("APP_SECRET_KEY", "a-very-strong-app-secret-key-32bytes-0123456789");
        return props;
    }

    /** P3-③：ESL / AMI 沿用内置默认口令必须阻断启动 */
    @Test
    public void defaultGatewayPasswordsAreRejected()
    {
        Map<String, String> props = strongBaseProps();
        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");
        props.put("call.gateway.freeswitch.eslPassword", ProductionSecurityGuard.DEFAULT_ESL_PASSWORD);
        props.put("call.gateway.asterisk.amiPassword", ProductionSecurityGuard.DEFAULT_AMI_PASSWORD);

        List<String> violations = ProductionSecurityGuard.findViolations(props::get);
        assertEquals(2, violations.size());
        assertTrue(violations.get(0).contains("ESL"));
        assertTrue(violations.get(1).contains("AMI"));
    }

    /** P3-①：Redis 未设密码必须阻断启动 */
    @Test
    public void redisWithoutPasswordIsRejected()
    {
        Map<String, String> props = strongBaseProps();
        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");
        props.put("spring.data.redis.password", "");

        List<String> violations = ProductionSecurityGuard.findViolations(props::get);
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("Redis"));
    }

    /** P3-⑤：生产环境模拟接通必须阻断启动 */
    @Test
    public void simulateAnswerIsRejected()
    {
        Map<String, String> props = strongBaseProps();
        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");
        props.put("call.outbound.simulateAnswer", "true");

        List<String> violations = ProductionSecurityGuard.findViolations(props::get);
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("模拟接通"));
    }

    /** P3-①：生产不得使用数据库 root 账号（空账号同样阻断） */
    @Test
    public void rootDbUsernameIsRejected()
    {
        Map<String, String> props = strongBaseProps();
        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");

        props.put("spring.datasource.druid.master.username", "root");
        List<String> violations = ProductionSecurityGuard.findViolations(props::get);
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("数据库账号"));

        props.put("spring.datasource.druid.master.username", "");
        assertTrue(ProductionSecurityGuard.findViolations(props::get).get(0).contains("数据库账号"));
    }

    /** P3-⑧：凭据加密根密钥缺失/开发兜底必须阻断启动，-Dapp.secret.key 通道同样有效 */
    @Test
    public void appSecretKeyMissingOrDevDefaultIsRejected()
    {
        Map<String, String> props = strongBaseProps();
        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");
        props.put("APP_SECRET_KEY", "");
        assertTrue(ProductionSecurityGuard.findViolations(props::get).get(0).contains("APP_SECRET_KEY"));

        props.put("APP_SECRET_KEY", ProductionSecurityGuard.DEFAULT_APP_SECRET_KEY);
        assertTrue(ProductionSecurityGuard.findViolations(props::get).get(0).contains("APP_SECRET_KEY"));

        props.put("APP_SECRET_KEY", "");
        props.put("app.secret.key", "a-very-strong-app-secret-key-32bytes-0123456789");
        assertTrue(ProductionSecurityGuard.findViolations(props::get).isEmpty());
    }

    @Test
    public void blankAuthKeyWarnsButDoesNotBlock()
    {
        Map<String, String> props = strongBaseProps();
        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");

        assertTrue(ProductionSecurityGuard.findViolations(props::get).isEmpty());
        List<String> warnings = ProductionSecurityGuard.findWarnings(props::get);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("PBX"));
        assertTrue(warnings.get(1).contains("CALLBACK_SIGN_SECRET"));
    }

    @Test
    public void strongCallbackSecretSuppressesWarning()
    {
        Map<String, String> props = strongBaseProps();
        props.put("call.sms.upstream.token", "strong-sms-upstream-token-32bytes");
        props.put("call.pbx.media.auth-key", "strong-pbx-auth-key-32bytes");
        props.put("call.callback.sign-secret", "strong-callback-sign-secret-32bytes");

        assertTrue(ProductionSecurityGuard.findWarnings(props::get).isEmpty());
    }

    @Test
    public void enforcementTriggerRules()
    {
        assertTrue(ProductionSecurityGuard.enforcementEnabled(new String[] {"prod"}, null));
        assertTrue(ProductionSecurityGuard.enforcementEnabled(new String[] {"druid"}, "true"));
        assertFalse(ProductionSecurityGuard.enforcementEnabled(new String[] {"druid"}, "false"));
        assertFalse(ProductionSecurityGuard.enforcementEnabled(new String[] {}, null));
        assertFalse(ProductionSecurityGuard.enforcementEnabled(null, null));
    }
}
