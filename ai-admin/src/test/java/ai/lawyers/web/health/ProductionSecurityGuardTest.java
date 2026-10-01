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
        props.put("spring.datasource.druid.statViewServlet.enabled", "true");
        props.put("spring.datasource.druid.statViewServlet.login-password",
                ProductionSecurityGuard.DEFAULT_DRUID_CONSOLE_PASSWORD);
        props.put("call.pbx.media.auth-key", "");
        return props;
    }

    @Test
    public void defaultsProduceAllViolations()
    {
        List<String> violations = ProductionSecurityGuard.findViolations(defaultProps()::get);
        assertEquals(3, violations.size());
        assertTrue(violations.get(0).contains("token.secret"));
        assertTrue(violations.get(1).contains("数据库"));
        assertTrue(violations.get(2).contains("Druid"));
    }

    @Test
    public void strongConfigAndConsoleDisabledPass()
    {
        Map<String, String> props = defaultProps();
        props.put("token.secret", "a-very-strong-random-jwt-secret-key-0123456789");
        props.put("spring.datasource.druid.master.password", "Str0ng!Db#Pass");
        props.put("spring.datasource.druid.statViewServlet.enabled", "false");

        assertTrue(ProductionSecurityGuard.findViolations(props::get).isEmpty());
    }

    @Test
    public void blankAuthKeyWarnsButDoesNotBlock()
    {
        Map<String, String> props = defaultProps();
        props.put("token.secret", "a-very-strong-random-jwt-secret-key-0123456789");
        props.put("spring.datasource.druid.master.password", "Str0ng!Db#Pass");
        props.put("spring.datasource.druid.statViewServlet.enabled", "false");

        assertTrue(ProductionSecurityGuard.findViolations(props::get).isEmpty());
        List<String> warnings = ProductionSecurityGuard.findWarnings(props::get);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("PBX"));
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
