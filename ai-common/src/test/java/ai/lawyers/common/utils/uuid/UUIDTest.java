package ai.lawyers.common.utils.uuid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

/**
 * UUID 测试：随机生成、名称生成、字符串解析与格式化、比较与时间字段。
 *
 * @author ai-lawyers
 */
class UUIDTest
{
    @Test
    void randomUUID_生成版本4且变体符合IETF()
    {
        UUID secure = UUID.randomUUID();
        UUID fast = UUID.fastUUID();

        assertEquals(4, secure.version());
        assertEquals(4, fast.version());
        // 变体号为 2 表示 IETF RFC 4122
        assertEquals(2, secure.variant());
        assertEquals(2, fast.variant());
        assertNotEquals(secure, UUID.randomUUID());
    }

    @Test
    void nameUUIDFromBytes_相同输入结果一致且版本为3()
    {
        byte[] name = "ai-lawyers".getBytes();

        UUID first = UUID.nameUUIDFromBytes(name);
        UUID second = UUID.nameUUIDFromBytes(name);

        assertEquals(first, second);
        assertEquals(3, first.version());
        assertNotEquals(first, UUID.nameUUIDFromBytes("other".getBytes()));
    }

    @Test
    void toString_标准格式与简单格式()
    {
        String standard = UUID.randomUUID().toString();
        String simple = UUID.fromString(standard).toString(true);

        assertEquals(36, standard.length());
        assertEquals(32, simple.length());
        assertTrue(standard.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertFalse(simple.contains("-"));
        assertEquals(standard.replace("-", ""), simple);
    }

    @Test
    void toString_全零位()
    {
        UUID zero = new UUID(0L, 0L);

        assertEquals("00000000-0000-0000-0000-000000000000", zero.toString());
        assertEquals("00000000000000000000000000000000", zero.toString(true));
        assertEquals(0, zero.version());
    }

    @Test
    void fromString_与toString互逆()
    {
        UUID origin = UUID.randomUUID();

        assertEquals(origin, UUID.fromString(origin.toString()));
        assertEquals(origin.getMostSignificantBits(), UUID.fromString(origin.toString()).getMostSignificantBits());
        assertEquals(origin.getLeastSignificantBits(), UUID.fromString(origin.toString()).getLeastSignificantBits());

        // 全 1 位同样可以往返
        UUID allOnes = new UUID(-1L, -1L);
        assertEquals(allOnes, UUID.fromString(allOnes.toString()));
    }

    @Test
    void fromString_非法字符串抛出异常()
    {
        assertThrows(IllegalArgumentException.class, () -> UUID.fromString("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> UUID.fromString("12345678-1234-1234-1234"));
    }

    @Test
    void equals_hashCode与compareTo()
    {
        UUID one = new UUID(1L, 2L);
        UUID same = new UUID(1L, 2L);
        UUID higher = new UUID(2L, 0L);
        UUID lowerLeast = new UUID(1L, 1L);

        assertEquals(one, same);
        assertEquals(one.hashCode(), same.hashCode());
        assertNotEquals(one, higher);
        assertFalse(one.equals(null));
        assertFalse(one.equals("not-uuid"));

        assertEquals(0, one.compareTo(same));
        assertEquals(-1, one.compareTo(higher));
        assertEquals(1, higher.compareTo(one));
        assertEquals(1, one.compareTo(lowerLeast));
    }

    @Test
    void 时间字段_非时间版本抛出异常()
    {
        UUID random = UUID.randomUUID();

        assertThrows(UnsupportedOperationException.class, random::timestamp);
        assertThrows(UnsupportedOperationException.class, random::clockSequence);
        assertThrows(UnsupportedOperationException.class, random::node);
    }

    @Test
    void 时间字段_版本1可解析()
    {
        // 构造一个版本号为 1 的 UUID
        UUID timeBased = new UUID(0x0000000000001000L, 0x0000000000000001L);

        assertEquals(1, timeBased.version());
        assertEquals(0L, timeBased.timestamp());
        assertEquals(0, timeBased.clockSequence());
        assertEquals(1L, timeBased.node());
    }

    @Test
    void 随机数生成器_返回可用实例()
    {
        SecureRandom secureRandom = UUID.getSecureRandom();
        ThreadLocalRandom random = UUID.getRandom();

        assertNotNull(secureRandom);
        assertNotNull(random);
    }
}
