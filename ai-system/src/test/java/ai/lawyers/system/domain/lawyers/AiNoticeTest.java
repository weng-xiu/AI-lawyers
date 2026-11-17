package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 工作台公告实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiNoticeTest
{
    @Test
    void 属性读写()
    {
        Date publishTime = new Date();
        AiNotice notice = new AiNotice();
        notice.setNoticeId(1L);
        notice.setNoticeTitle("系统维护通知");
        notice.setNoticeContent("今晚 22:00 进行系统升级");
        notice.setNoticeType("1");
        notice.setPublishTime(publishTime);
        notice.setStatus("1");
        notice.setIsTop("1");

        assertEquals(1L, notice.getNoticeId());
        assertEquals("系统维护通知", notice.getNoticeTitle());
        assertEquals("今晚 22:00 进行系统升级", notice.getNoticeContent());
        assertEquals("1", notice.getNoticeType());
        assertEquals(publishTime, notice.getPublishTime());
        assertEquals("1", notice.getStatus());
        assertEquals("1", notice.getIsTop());
    }

    @Test
    void 默认值为空()
    {
        AiNotice notice = new AiNotice();

        assertNull(notice.getNoticeId());
        assertNull(notice.getNoticeTitle());
        assertNull(notice.getNoticeContent());
        assertNull(notice.getNoticeType());
        assertNull(notice.getPublishTime());
        assertNull(notice.getStatus());
        assertNull(notice.getIsTop());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiNotice notice = new AiNotice();
        notice.setNoticeTitle("公告标题");
        notice.setStatus("0");

        String text = notice.toString();
        assertTrue(text.contains("公告标题"));
        assertTrue(text.contains("noticeTitle"));
    }
}
