package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PJSIP 端点只读视图测试：属性读写与静态 contact 列表。
 *
 * @author ai-lawyers
 */
class PjsipEndpointTest
{
    @Test
    void 属性读写()
    {
        PjsipEndpoint endpoint = new PjsipEndpoint();
        endpoint.setName("1001");
        endpoint.setContext("from-internal");
        endpoint.setCallerId("1001");
        endpoint.setAors("1001");
        endpoint.setAllow("ulaw,alaw");
        endpoint.setTransport("transport-udp");
        endpoint.setDirectMedia("no");
        endpoint.setForceRport("yes");
        endpoint.setRewriteContact("yes");
        endpoint.setIceSupport("no");
        endpoint.setMediaEncryption("no");

        assertEquals("1001", endpoint.getName());
        assertEquals("from-internal", endpoint.getContext());
        assertEquals("1001", endpoint.getCallerId());
        assertEquals("1001", endpoint.getAors());
        assertEquals("ulaw,alaw", endpoint.getAllow());
        assertEquals("transport-udp", endpoint.getTransport());
        assertEquals("no", endpoint.getDirectMedia());
        assertEquals("yes", endpoint.getForceRport());
        assertEquals("yes", endpoint.getRewriteContact());
        assertEquals("no", endpoint.getIceSupport());
        assertEquals("no", endpoint.getMediaEncryption());
    }

    @Test
    void 静态联系人列表()
    {
        PjsipEndpoint endpoint = new PjsipEndpoint();
        endpoint.setContacts(List.of("sip:1001@192.168.1.10:5060"));

        assertEquals(1, endpoint.getContacts().size());
        assertEquals("sip:1001@192.168.1.10:5060", endpoint.getContacts().get(0));
        assertTrue(endpoint.getContacts().contains("sip:1001@192.168.1.10:5060"));
    }

    @Test
    void 默认值为空()
    {
        PjsipEndpoint endpoint = new PjsipEndpoint();

        assertNull(endpoint.getName());
        assertNull(endpoint.getAors());
        assertNull(endpoint.getContacts());
    }
}
