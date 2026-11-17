package ai.lawyers.common.utils.uuid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ID 生成工具测试：标准与简化 UUID 的格式和唯一性。
 *
 * @author ai-lawyers
 */
class IdUtilsTest
{
    @Test
    void randomUUID_带横线且长度36()
    {
        String id = IdUtils.randomUUID();

        assertEquals(36, id.length());
        assertEquals(4, id.chars().filter(c -> c == '-').count());
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertNotEquals(id, IdUtils.randomUUID());
    }

    @Test
    void simpleUUID_无横线且长度32()
    {
        String id = IdUtils.simpleUUID();

        assertEquals(32, id.length());
        assertFalse(id.contains("-"));
        assertTrue(id.matches("[0-9a-f]{32}"));
        assertNotEquals(id, IdUtils.simpleUUID());
    }

    @Test
    void fastUUID_带横线且长度36()
    {
        String id = IdUtils.fastUUID();

        assertEquals(36, id.length());
        assertEquals(4, id.chars().filter(c -> c == '-').count());
        assertNotEquals(id, IdUtils.fastUUID());
    }

    @Test
    void fastSimpleUUID_无横线且长度32()
    {
        String id = IdUtils.fastSimpleUUID();

        assertEquals(32, id.length());
        assertFalse(id.contains("-"));
        assertNotEquals(id, IdUtils.fastSimpleUUID());
    }
}
