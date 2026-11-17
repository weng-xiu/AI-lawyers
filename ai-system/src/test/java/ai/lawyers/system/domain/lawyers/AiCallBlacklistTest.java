package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 通话黑白名单实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallBlacklistTest
{
    @Test
    void 属性读写()
    {
        Date start = new Date();
        Date end = new Date(start.getTime() + 86400000L);
        AiCallBlacklist blacklist = new AiCallBlacklist();
        blacklist.setId(1L);
        blacklist.setPhoneNumber("13700000000");
        blacklist.setListType(1);
        blacklist.setReason("恶意骚扰");
        blacklist.setStatus(1);
        blacklist.setEffectiveStart(start);
        blacklist.setEffectiveEnd(end);

        assertEquals(1L, blacklist.getId());
        assertEquals("13700000000", blacklist.getPhoneNumber());
        assertEquals(1, blacklist.getListType());
        assertEquals("恶意骚扰", blacklist.getReason());
        assertEquals(1, blacklist.getStatus());
        assertEquals(start, blacklist.getEffectiveStart());
        assertEquals(end, blacklist.getEffectiveEnd());
    }

    @Test
    void 默认值为空()
    {
        AiCallBlacklist blacklist = new AiCallBlacklist();

        assertNull(blacklist.getId());
        assertNull(blacklist.getPhoneNumber());
        assertNull(blacklist.getListType());
        assertNull(blacklist.getReason());
        assertNull(blacklist.getStatus());
        assertNull(blacklist.getEffectiveStart());
        assertNull(blacklist.getEffectiveEnd());
    }

    @Test
    void 字符串输出包含号码与名单类型()
    {
        AiCallBlacklist blacklist = new AiCallBlacklist();
        blacklist.setPhoneNumber("13600000000");
        blacklist.setListType(2);

        String text = blacklist.toString();
        assertTrue(text.contains("13600000000"));
        assertTrue(text.contains("listType"));
    }
}
