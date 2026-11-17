package ai.lawyers.system.domain.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 呼叫队列只读视图测试：属性读写与静态成员列表。
 *
 * @author ai-lawyers
 */
class PjsipQueueTest
{
    @Test
    void 属性读写()
    {
        PjsipQueue queue = new PjsipQueue();
        queue.setName("legal_service");
        queue.setStrategy("ringall");
        queue.setTimeout(20);
        queue.setWrapupTime(10);
        queue.setRingInUse("no");
        queue.setMusicClass("default");
        queue.setServiceLevel(15);
        queue.setMaxlen(0);

        assertEquals("legal_service", queue.getName());
        assertEquals("ringall", queue.getStrategy());
        assertEquals(20, queue.getTimeout());
        assertEquals(10, queue.getWrapupTime());
        assertEquals("no", queue.getRingInUse());
        assertEquals("default", queue.getMusicClass());
        assertEquals(15, queue.getServiceLevel());
        assertEquals(0, queue.getMaxlen());
    }

    @Test
    void 静态成员列表()
    {
        PjsipQueue queue = new PjsipQueue();
        queue.setMembers(List.of("PJSIP/1001", "PJSIP/1002,penalty=1"));

        assertEquals(2, queue.getMembers().size());
        assertTrue(queue.getMembers().contains("PJSIP/1001"));
    }

    @Test
    void 默认值为空()
    {
        PjsipQueue queue = new PjsipQueue();

        assertNull(queue.getName());
        assertNull(queue.getTimeout());
        assertNull(queue.getMembers());
    }
}
