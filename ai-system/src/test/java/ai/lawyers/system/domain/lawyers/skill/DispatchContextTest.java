package ai.lawyers.system.domain.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 坐席分配上下文测试：属性读写、默认开关与工厂方法。
 *
 * @author ai-lawyers
 */
class DispatchContextTest
{
    @Test
    void 属性读写()
    {
        DispatchContext ctx = new DispatchContext();
        ctx.setSessionId("sess-001");
        ctx.setRecordId(1L);
        ctx.setCallerNumber("13800000000");
        ctx.setPriority(10);
        ctx.setEnqueueIfNoAgent(false);
        ctx.setAllowOverflow(false);

        assertEquals("sess-001", ctx.getSessionId());
        assertEquals(1L, ctx.getRecordId());
        assertEquals("13800000000", ctx.getCallerNumber());
        assertEquals(10, ctx.getPriority());
        assertFalse(ctx.isEnqueueIfNoAgent());
        assertFalse(ctx.isAllowOverflow());
    }

    @Test
    void 默认值为空且开关开启()
    {
        DispatchContext ctx = new DispatchContext();

        assertNull(ctx.getSessionId());
        assertNull(ctx.getRecordId());
        assertNull(ctx.getCallerNumber());
        assertNull(ctx.getPriority());
        assertTrue(ctx.isEnqueueIfNoAgent());
        assertTrue(ctx.isAllowOverflow());
    }

    @Test
    void 工厂方法初始化关键字段()
    {
        DispatchContext ctx = DispatchContext.of("sess-002", 2L, "13900000000");

        assertEquals("sess-002", ctx.getSessionId());
        assertEquals(2L, ctx.getRecordId());
        assertEquals("13900000000", ctx.getCallerNumber());
        assertEquals(0, ctx.getPriority());
        assertTrue(ctx.isEnqueueIfNoAgent());
        assertTrue(ctx.isAllowOverflow());
    }
}
