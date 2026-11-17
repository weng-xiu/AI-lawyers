package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 运营商中继线路实体测试：属性读写、剩余并发计算与默认值。
 *
 * @author ai-lawyers
 */
class AiCallTrunkTest
{
    @Test
    void 属性读写()
    {
        Date now = new Date();
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkId(1L);
        trunk.setTrunkCode("CU-SIP-01");
        trunk.setTrunkName("联通 SIP 主线路");
        trunk.setCarrier("CU");
        trunk.setLineType("1");
        trunk.setVendor("华为");
        trunk.setProtocol("SIP");
        trunk.setGatewayHost("10.0.0.1");
        trunk.setGatewayPort(5060);
        trunk.setTransport("UDP");
        trunk.setMaxConcurrent(100);
        trunk.setCurrentConcurrent(30);
        trunk.setPriority(1);
        trunk.setWeight(50);
        trunk.setIsBackup("0");
        trunk.setHealthStatus("1");
        trunk.setEnableFlag("1");
        trunk.setLastCheckTime(now);
        trunk.setFailCount(0);
        trunk.setConnectRate(new BigDecimal("85.50"));
        trunk.setTotalCalls(1000L);
        trunk.setSuccessCalls(855L);
        trunk.setDelFlag("0");

        assertEquals(1L, trunk.getTrunkId());
        assertEquals("CU-SIP-01", trunk.getTrunkCode());
        assertEquals("联通 SIP 主线路", trunk.getTrunkName());
        assertEquals("CU", trunk.getCarrier());
        assertEquals("1", trunk.getLineType());
        assertEquals("华为", trunk.getVendor());
        assertEquals("SIP", trunk.getProtocol());
        assertEquals("10.0.0.1", trunk.getGatewayHost());
        assertEquals(5060, trunk.getGatewayPort());
        assertEquals("UDP", trunk.getTransport());
        assertEquals(100, trunk.getMaxConcurrent());
        assertEquals(30, trunk.getCurrentConcurrent());
        assertEquals(1, trunk.getPriority());
        assertEquals(50, trunk.getWeight());
        assertEquals("0", trunk.getIsBackup());
        assertEquals("1", trunk.getHealthStatus());
        assertEquals("1", trunk.getEnableFlag());
        assertEquals(now, trunk.getLastCheckTime());
        assertEquals(0, trunk.getFailCount());
        assertEquals(new BigDecimal("85.50"), trunk.getConnectRate());
        assertEquals(1000L, trunk.getTotalCalls());
        assertEquals(855L, trunk.getSuccessCalls());
        assertEquals("0", trunk.getDelFlag());
    }

    @Test
    void 剩余可用并发数等于最大并发减当前并发()
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setMaxConcurrent(100);
        trunk.setCurrentConcurrent(30);

        assertEquals(70, trunk.getAvailableConcurrent());
    }

    @Test
    void 并发字段为空时剩余可用并发为零()
    {
        AiCallTrunk trunk = new AiCallTrunk();

        assertEquals(0, trunk.getAvailableConcurrent());

        trunk.setMaxConcurrent(10);
        assertEquals(10, trunk.getAvailableConcurrent());
    }

    @Test
    void 当前并发超出最大值时剩余可用并发不为负()
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setMaxConcurrent(10);
        trunk.setCurrentConcurrent(15);

        assertEquals(0, trunk.getAvailableConcurrent());
    }

    @Test
    void 默认值为空()
    {
        AiCallTrunk trunk = new AiCallTrunk();

        assertNull(trunk.getTrunkId());
        assertNull(trunk.getTrunkCode());
        assertNull(trunk.getCarrier());
        assertNull(trunk.getMaxConcurrent());
        assertNull(trunk.getCurrentConcurrent());
        assertNull(trunk.getHealthStatus());
        assertNull(trunk.getConnectRate());
        assertNull(trunk.getDelFlag());
    }

    @Test
    void 字符串输出包含线路编码与健康状态()
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkCode("CM-SIP-02");
        trunk.setHealthStatus("3");

        String text = trunk.toString();
        assertTrue(text.contains("CM-SIP-02"));
        assertTrue(text.contains("healthStatus"));
    }
}
