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
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.PooledAmiClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B3 命令面池化：AsteriskGatewayAdapter 走 PooledAmiClient 长连接的行为测试。
 *
 * <p>覆盖：池化路径成功/无响应失败语义、hangup/redirect 复用、
 * 池关闭时回退短连接（不可达端口快速失败）。</p>
 */
class AsteriskGatewayAdapterTest
{
    private AsteriskGatewayAdapter adapter;
    private PooledAmiClient pool;

    @BeforeEach
    void setUp() throws Exception
    {
        adapter = new AsteriskGatewayAdapter();
        pool = Mockito.mock(PooledAmiClient.class);
        setField(adapter, "amiPort", 5038);
        setField(adapter, "amiUser", "admin");
        setField(adapter, "amiPassword", "amp111");
        setField(adapter, "timeout", 800);
        setField(adapter, "context", "from-internal");
        setField(adapter, "pooledAmiClient", pool);
    }

    private AiCallTrunk trunk(String host)
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkCode("TRUNK_AST");
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
        when(pool.sendAction(eq("10.0.0.1"), Mockito.contains("Action: Originate")))
                .thenReturn("Response: Success\nMessage: Originate successfully queued\n");

        DialResult result = adapter.originate(trunk("10.0.0.1"), request());

        assertEquals(DialStatusEnum.DIALING.getCode(), result.getDialStatus());
        assertNotNull(result.getCallUuid());
        verify(pool).sendAction(eq("10.0.0.1"),
                Mockito.argThat(a -> a.contains("Channel: SIP/TRUNK_AST/13800138000")));
    }

    @Test
    void originate_pooled_noResponse_gatewayError() throws Exception
    {
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendAction(anyString(), anyString())).thenReturn(null);

        DialResult result = adapter.originate(trunk("10.0.0.1"), request());

        assertEquals("GATEWAY_ERROR", result.getErrorCode());
        assertEquals(DialStatusEnum.FAILED.getCode(), result.getDialStatus());
    }

    @Test
    void hangup_pooled_trueAndFalse()
    {
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendAction(eq("10.0.0.1"), Mockito.contains("Action: Hangup")))
                .thenReturn("Response: Success\n");
        assertTrue(adapter.hangup(trunk("10.0.0.1"), "SIP/TRUNK_AST-0001"));

        when(pool.sendAction(eq("10.0.0.1"), Mockito.contains("Action: Hangup")))
                .thenReturn("Response: Error\nMessage: No such channel\n");
        assertFalse(adapter.hangup(trunk("10.0.0.1"), "SIP/TRUNK_AST-0002"));
    }

    @Test
    void bridgeToAgent_pooled_commandRouted()
    {
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendAction(eq("10.0.0.1"), Mockito.contains("Action: Redirect")))
                .thenReturn("Response: Success\n");

        assertTrue(adapter.bridgeToAgent(trunk("10.0.0.1"), "SIP/TRUNK_AST-0001", "1001"));
        verify(pool).sendAction(eq("10.0.0.1"),
                Mockito.argThat(a -> a.contains("Exten: 1001") && a.contains("Priority: 1")));
    }

    @Test
    void originate_poolDisabled_fallsBackToShortConnection() throws Exception
    {
        when(pool.isEnabled()).thenReturn(false);
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0))
        {
            deadPort = probe.getLocalPort();
        }
        setField(adapter, "amiPort", deadPort);

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
