package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 通知公告实体测试：属性读写与字符串输出。
 *
 * @author ai-lawyers
 */
class SysNoticeTest
{
    @Test
    void 属性读写()
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(10L);
        notice.setNoticeTitle("系统维护通知");
        notice.setNoticeType("1");
        notice.setNoticeContent("今晚 22:00 进行系统维护");
        notice.setStatus("0");

        assertEquals(10L, notice.getNoticeId());
        assertEquals("系统维护通知", notice.getNoticeTitle());
        assertEquals("1", notice.getNoticeType());
        assertEquals("今晚 22:00 进行系统维护", notice.getNoticeContent());
        assertEquals("0", notice.getStatus());
    }

    @Test
    void 默认值为空()
    {
        SysNotice notice = new SysNotice();

        assertNull(notice.getNoticeId());
        assertNull(notice.getNoticeTitle());
        assertNull(notice.getNoticeType());
        assertNull(notice.getNoticeContent());
        assertNull(notice.getStatus());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(11L);
        notice.setNoticeTitle("新功能上线");
        notice.setNoticeType("2");

        String text = notice.toString();
        assertTrue(text.contains("新功能上线"));
        assertTrue(text.contains("noticeType"));
    }
}
