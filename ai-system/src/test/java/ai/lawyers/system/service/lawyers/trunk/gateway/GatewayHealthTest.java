package ai.lawyers.system.service.lawyers.trunk.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 网关健康探测结果测试：可达/不可达工厂与注册状态。
 *
 * @author ai-lawyers
 */
class GatewayHealthTest
{
    @Test
    void up_标记可达耗时与注册状态()
    {
        GatewayHealth health = GatewayHealth.up(37L);

        assertTrue(health.isReachable());
        assertEquals(37L, health.getLatencyMs());
        assertEquals("REGED", health.getRegisterState());
        assertNull(health.getMessage());
    }

    @Test
    void down_标记不可达并记录失败原因()
    {
        GatewayHealth health = GatewayHealth.down("connect timeout");

        assertFalse(health.isReachable());
        assertEquals("NOREG", health.getRegisterState());
        assertEquals("connect timeout", health.getMessage());
        assertEquals(0L, health.getLatencyMs());
    }

    @Test
    void setter_支持覆写探测字段()
    {
        GatewayHealth health = GatewayHealth.up(10L);

        health.setReachable(false);
        health.setLatencyMs(88L);
        health.setRegisterState("UNKNOWN");
        health.setMessage("handshake failed");

        assertFalse(health.isReachable());
        assertEquals(88L, health.getLatencyMs());
        assertEquals("UNKNOWN", health.getRegisterState());
        assertEquals("handshake failed", health.getMessage());
    }
}
