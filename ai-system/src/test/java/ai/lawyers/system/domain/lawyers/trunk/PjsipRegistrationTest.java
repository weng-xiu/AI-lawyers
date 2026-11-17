package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PJSIP 端点实时注册态测试：属性读写与 contact 结构。
 *
 * @author ai-lawyers
 */
class PjsipRegistrationTest
{
    @Test
    void 属性读写()
    {
        PjsipRegistration registration = new PjsipRegistration();
        registration.setName("1001");
        registration.setDeviceState("In use");
        registration.setActiveChannels(2);
        registration.setRawContacts("sip:1001@192.168.1.10:5060;Avail");

        assertEquals("1001", registration.getName());
        assertEquals("In use", registration.getDeviceState());
        assertEquals(2, registration.getActiveChannels());
        assertEquals("sip:1001@192.168.1.10:5060;Avail", registration.getRawContacts());
    }

    @Test
    void 联系人实时状态()
    {
        PjsipRegistration.Contact contact = new PjsipRegistration.Contact();
        contact.setUri("sip:1001@192.168.1.10:5060");
        contact.setStatus("Avail");

        PjsipRegistration registration = new PjsipRegistration();
        registration.setContacts(List.of(contact));

        assertEquals(1, registration.getContacts().size());
        assertEquals("sip:1001@192.168.1.10:5060", registration.getContacts().get(0).getUri());
        assertEquals("Avail", registration.getContacts().get(0).getStatus());
    }

    @Test
    void 默认值为空()
    {
        PjsipRegistration registration = new PjsipRegistration();
        PjsipRegistration.Contact contact = new PjsipRegistration.Contact();

        assertNull(registration.getName());
        assertNull(registration.getActiveChannels());
        assertNull(registration.getContacts());
        assertNull(contact.getUri());
        assertNull(contact.getStatus());
    }
}
