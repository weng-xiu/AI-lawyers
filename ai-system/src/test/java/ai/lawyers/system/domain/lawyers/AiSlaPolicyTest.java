package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 工单 SLA 策略实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiSlaPolicyTest
{
    @Test
    void 属性读写()
    {
        AiSlaPolicy policy = new AiSlaPolicy();
        policy.setPolicyId(1L);
        policy.setPolicyName("紧急工单策略");
        policy.setBizType("TICKET");
        policy.setPriority("1");
        policy.setRespondMinutes(15);
        policy.setResolveMinutes(240);
        policy.setWarnThreshold(80);
        policy.setEscalateRoles("admin,manager");
        policy.setStatus("0");

        assertEquals(1L, policy.getPolicyId());
        assertEquals("紧急工单策略", policy.getPolicyName());
        assertEquals("TICKET", policy.getBizType());
        assertEquals("1", policy.getPriority());
        assertEquals(15, policy.getRespondMinutes());
        assertEquals(240, policy.getResolveMinutes());
        assertEquals(80, policy.getWarnThreshold());
        assertEquals("admin,manager", policy.getEscalateRoles());
        assertEquals("0", policy.getStatus());
    }

    @Test
    void 默认值为空()
    {
        AiSlaPolicy policy = new AiSlaPolicy();

        assertNull(policy.getPolicyId());
        assertNull(policy.getPolicyName());
        assertNull(policy.getBizType());
        assertNull(policy.getPriority());
        assertNull(policy.getRespondMinutes());
        assertNull(policy.getResolveMinutes());
        assertNull(policy.getWarnThreshold());
        assertNull(policy.getEscalateRoles());
        assertNull(policy.getStatus());
    }

    @Test
    void 字符串输出包含策略名称与业务类型()
    {
        AiSlaPolicy policy = new AiSlaPolicy();
        policy.setPolicyName("法律援助策略");
        policy.setBizType("LEGAL_AID");

        String text = policy.toString();
        assertTrue(text.contains("法律援助策略"));
        assertTrue(text.contains("LEGAL_AID"));
    }
}
