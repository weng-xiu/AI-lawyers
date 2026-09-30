package ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.trunk.PjsipRegistration;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.PooledAmiClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P3-B5（V2.53）：PJSIP 实时注册态服务单元测试——EndpointList 文本解析、
 * 池未启用/AMI 无响应/错误响应三种降级路径。
 */
class PjsipRegistrationServiceTest
{
    /** 模拟 PJSIPShowEndpoints 多事件收集文本（真实 Asterisk 输出形态） */
    private static final String COLLECTED = ""
            + "Response: Success\n"
            + "EventList: start\n"
            + "Message: Following Endpoints\n"
            + "\n"
            + "Event: EndpointList\n"
            + "ObjectType: endpoint\n"
            + "ObjectName: 6001\n"
            + "DeviceState: Not in use\n"
            + "ActiveChannels: 0\n"
            + "Contacts: sip:6001@10.10.0.5:5060,Avail\n"
            + "\n"
            + "Event: EndpointList\n"
            + "ObjectType: endpoint\n"
            + "ObjectName: 6002\n"
            + "DeviceState: Unavailable\n"
            + "ActiveChannels: 2\n"
            + "Contacts: sip:6002@10.10.0.6:5060,Unavail sip:6002@10.10.0.7:5060,NonQualified\n"
            + "\n"
            + "Event: EndpointListComplete\n"
            + "EventList: Complete\n"
            + "\n";

    private PjsipRegistrationService newService(PooledAmiClient pool, String host) throws Exception
    {
        PjsipRegistrationService s = new PjsipRegistrationService();
        ReflectionTestUtils.setField(s, "pooledAmiClient", pool);
        ReflectionTestUtils.setField(s, "amiHost", host);
        return s;
    }

    @Test
    void parseEndpointList_twoEndpointsWithContacts()
    {
        List<PjsipRegistration> list = PjsipRegistrationService.parseEndpointList(COLLECTED);
        assertEquals(2, list.size(), "应解析出两个 EndpointList 事件（Complete/Response 块不产出）");

        PjsipRegistration r1 = list.get(0);
        assertEquals("6001", r1.getName());
        assertEquals("Not in use", r1.getDeviceState());
        assertEquals(Integer.valueOf(0), r1.getActiveChannels());
        assertEquals(1, r1.getContacts().size());
        assertEquals("sip:6001@10.10.0.5:5060", r1.getContacts().get(0).getUri());
        assertEquals("Avail", r1.getContacts().get(0).getStatus());
        assertTrue(r1.getRawContacts().contains("Avail"), "原始 Contacts 行应保留兜底");

        PjsipRegistration r2 = list.get(1);
        assertEquals("6002", r2.getName());
        assertEquals("Unavailable", r2.getDeviceState());
        assertEquals(Integer.valueOf(2), r2.getActiveChannels());
        assertEquals(2, r2.getContacts().size(), "多条 contact 以空格分隔应全部解析");
        assertEquals("Unavail", r2.getContacts().get(0).getStatus());
        assertEquals("NonQualified", r2.getContacts().get(1).getStatus());
    }

    @Test
    void parseEndpointList_emptyAndMalformed_graceful()
    {
        assertTrue(PjsipRegistrationService.parseEndpointList(null).isEmpty());
        assertTrue(PjsipRegistrationService.parseEndpointList("").isEmpty());
        // 只有响应块没有事件块 → 空列表不抛异常
        assertTrue(PjsipRegistrationService.parseEndpointList("Response: Success\nEventList: start\n\n").isEmpty());
        // 非 EndpointList 事件不产出
        assertTrue(PjsipRegistrationService.parseEndpointList(
                "Event: Hangup\nChannel: SIP/x-1\n\nEvent: EndpointListComplete\n\n").isEmpty());
    }

    @Test
    void listRegistrations_disabledPool_degradesGracefully() throws Exception
    {
        PooledAmiClient pool = Mockito.mock(PooledAmiClient.class);
        when(pool.isEnabled()).thenReturn(false);
        PjsipRegistrationService service = newService(pool, "10.0.0.9");

        Map<String, Object> result = service.listRegistrations();
        assertEquals(false, result.get("available"));
        assertEquals("10.0.0.9", result.get("host"));
        assertTrue(result.get("error").toString().contains("未启用"));
        Mockito.verify(pool, Mockito.never()).sendActionCollectEvents(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void listRegistrations_amiNoResponse_degradesGracefully() throws Exception
    {
        PooledAmiClient pool = Mockito.mock(PooledAmiClient.class);
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendActionCollectEvents(eq("10.0.0.9"), anyString(),
                eq(PjsipRegistrationService.COMPLETE_EVENT), anyLong())).thenReturn(null);
        PjsipRegistrationService service = newService(pool, "10.0.0.9");

        Map<String, Object> result = service.listRegistrations();
        assertEquals(false, result.get("available"));
        assertTrue(result.get("error").toString().contains("AMI 无响应"));
        assertNull(result.get("registrations"));
    }

    @Test
    void listRegistrations_success_parsesRegistrations() throws Exception
    {
        PooledAmiClient pool = Mockito.mock(PooledAmiClient.class);
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendActionCollectEvents(anyString(), anyString(),
                eq(PjsipRegistrationService.COMPLETE_EVENT), anyLong())).thenReturn(COLLECTED);
        PjsipRegistrationService service = newService(pool, "10.0.0.9");

        Map<String, Object> result = service.listRegistrations();
        assertEquals(true, result.get("available"));
        assertEquals(2, result.get("endpointCount"));
        List<?> regs = (List<?>) result.get("registrations");
        assertEquals(2, regs.size());
    }

    @Test
    void listRegistrations_amiErrorResponse_degradesWithMessage() throws Exception
    {
        PooledAmiClient pool = Mockito.mock(PooledAmiClient.class);
        when(pool.isEnabled()).thenReturn(true);
        when(pool.sendActionCollectEvents(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("Response: Error\nMessage: Permission denied\n\n");
        PjsipRegistrationService service = newService(pool, "10.0.0.9");

        Map<String, Object> result = service.listRegistrations();
        assertEquals(false, result.get("available"));
        assertTrue(result.get("error").toString().contains("Permission denied"));
        assertFalse((Boolean) result.get("available"));
    }
}
