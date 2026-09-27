package ai.lawyers.system.service.lawyers.trunk.gateway.impl;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.enums.DialStatusEnum;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.PooledEslClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B3 命令面池化：FreeSwitchGatewayAdapter 走 PooledEslClient 长连接的行为测试。
 *
 * <p>覆盖：池化路径成功/无响应失败语义、hangup/bridge 复用、loopback 重试透传、
 * 池关闭时回退短连接（不可达端口快速失败）。</p>
 */
class FreeSwitchGatewayAdapterTest
{
    private FreeSwitchGatewayAdapter adapter;
    private PooledEslClient pool;

    @BeforeEach
    void setUp() throws Exception
    {
        adapter = new FreeSwitchGatewayAdapter();
        pool = Mockito.mock(PooledEslClient.class);
        setField(adapter, "eslPort", 8021);
        setField(adapter, "eslPassword", "ClueCon");
        setField(adapter, "timeout", 800);
        setField(adapter, "context", "default");
        setField(adapter, "pooledEslClient", pool);
    }

    private AiCallTrunk trunk(String host)
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkCode("TRUNK_CM_01");
        trunk.setGatewayHost(host);
        trunk.setProtocol("SIP");
        return trunk;
    }

    private DialRequest request()
    {
        DialRequest req = new DialRequest();
        req.setCalleeNumber("13800138000");
        req.setRingTimeout(30);
        return req;
    }

    @Test
    void originate_pooled_success() throws Exception
    {
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendCommand(eq("10.0.0.1"), startsWith("bgapi originate {")))
                .thenReturn("Content-Type: command/reply\nReply-Text: +OK 7e5e1f4a\n");

        DialResult result = adapter.originate(trunk("10.0.0.1"), request());

        assertEquals(DialStatusEnum.DIALING.getCode(), result.getDialStatus());
        assertNotNull(result.getCallUuid());
        verify(pool).sendCommand(eq("10.0.0.1"),
                Mockito.argThat(c -> c.contains("sofia/gateway/TRUNK_CM_01/13800138000")));
    }

    @Test
    void originate_pooled_noResponse_gatewayError() throws Exception
    {
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendCommand(anyString(), anyString())).thenReturn(null);

        DialResult result = adapter.originate(trunk("10.0.0.1"), request());

        assertEquals("GATEWAY_ERROR", result.getErrorCode());
        assertEquals(DialStatusEnum.FAILED.getCode(), result.getDialStatus());
    }

    @Test
    void originate_loopback_invalidGateway_retriesLoopback() throws Exception
    {
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendCommand(eq("127.0.0.1"), anyString()))
                .thenReturn("Content-Type: command/reply\nReply-Text: -ERR INVALID_GATEWAY\n")
                .thenReturn("Content-Type: command/reply\nReply-Text: +OK 9c2d\n");

        DialResult result = adapter.originate(trunk("127.0.0.1"), request());

        assertEquals(DialStatusEnum.DIALING.getCode(), result.getDialStatus());
        verify(pool, Mockito.times(2)).sendCommand(eq("127.0.0.1"), anyString());
        verify(pool).sendCommand(eq("127.0.0.1"), Mockito.argThat(c -> c.contains("loopback/13800138000")));
    }

    @Test
    void hangup_pooled_trueAndFalse()
    {
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendCommand("10.0.0.1", "api uuid_kill u-1"))
                .thenReturn("Content-Type: command/reply\nReply-Text: +OK\n");
        assertTrue(adapter.hangup(trunk("10.0.0.1"), "u-1"));

        when(pool.sendCommand("10.0.0.1", "api uuid_kill u-2")).thenReturn(null);
        assertFalse(adapter.hangup(trunk("10.0.0.1"), "u-2"));
    }

    @Test
    void bridgeToAgent_pooled_commandRouted()
    {
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendCommand("10.0.0.1", "api uuid_transfer u-3 -both user/1001 XML default"))
                .thenReturn("Content-Type: command/reply\nReply-Text: +OK\n");

        assertTrue(adapter.bridgeToAgent(trunk("10.0.0.1"), "u-3", "1001"));
        verify(pool).sendCommand("10.0.0.1", "api uuid_transfer u-3 -both user/1001 XML default");
    }

    @Test
    void originate_poolDisabled_fallsBackToShortConnection() throws Exception
    {
        when(pool.isEnabled()).thenReturn(false);
        // 指向未监听端口：短连接 connect 立即被拒 → GATEWAY_ERROR（与历史行为一致）
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0))
        {
            deadPort = probe.getLocalPort();
        }
        setField(adapter, "eslPort", deadPort);

        DialResult result = adapter.originate(trunk("127.0.0.1"), request());

        assertEquals("GATEWAY_ERROR", result.getErrorCode());
        assertEquals(DialStatusEnum.FAILED.getCode(), result.getDialStatus());
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
