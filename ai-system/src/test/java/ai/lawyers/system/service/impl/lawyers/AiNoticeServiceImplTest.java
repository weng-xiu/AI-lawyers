package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiNotice;
import ai.lawyers.system.mapper.lawyers.AiNoticeMapper;

/**
 * 公告服务单测。
 *
 * <p>覆盖公告查询、新增时状态与置顶默认值、发布时发布时间回填、修改时发布时间补全与删除委托。</p>
 *
 * @author ai-lawyers
 */
class AiNoticeServiceImplTest
{
    private AiNoticeServiceImpl service;

    @Mock private AiNoticeMapper aiNoticeMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiNoticeServiceImpl();
        ReflectionTestUtils.setField(service, "aiNoticeMapper", aiNoticeMapper);
    }

    @Test
    void selectAiNoticeByNoticeId_delegates()
    {
        AiNotice expected = new AiNotice();
        when(aiNoticeMapper.selectAiNoticeByNoticeId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiNoticeByNoticeId(1L));
    }

    @Test
    void selectAiNoticeList_delegates()
    {
        AiNotice query = new AiNotice();
        List<AiNotice> list = Collections.singletonList(new AiNotice());
        when(aiNoticeMapper.selectAiNoticeList(query)).thenReturn(list);

        assertSame(list, service.selectAiNoticeList(query));
    }

    @Test
    void selectPublishedNotices_delegates()
    {
        List<AiNotice> list = Collections.singletonList(new AiNotice());
        when(aiNoticeMapper.selectPublishedNotices(5)).thenReturn(list);

        assertSame(list, service.selectPublishedNotices(5));
    }

    @Test
    void insertAiNotice_fillsDefaultStatusAndTop()
    {
        AiNotice notice = new AiNotice();
        when(aiNoticeMapper.insertAiNotice(notice)).thenReturn(1);

        assertEquals(1, service.insertAiNotice(notice));
        assertEquals("0", notice.getStatus());
        assertEquals("0", notice.getIsTop());
        assertNull(notice.getPublishTime());
    }

    @Test
    void insertAiNotice_published_fillsPublishTime()
    {
        AiNotice notice = new AiNotice();
        notice.setStatus("1");
        when(aiNoticeMapper.insertAiNotice(notice)).thenReturn(1);

        assertEquals(1, service.insertAiNotice(notice));
        assertNotNull(notice.getPublishTime());
    }

    @Test
    void insertAiNotice_publishedWithTime_keepsTime()
    {
        Date publishTime = new Date(1000L);
        AiNotice notice = new AiNotice();
        notice.setStatus("1");
        notice.setPublishTime(publishTime);
        when(aiNoticeMapper.insertAiNotice(notice)).thenReturn(1);

        assertEquals(1, service.insertAiNotice(notice));
        assertSame(publishTime, notice.getPublishTime());
    }

    @Test
    void updateAiNotice_publishedWithoutExistTime_fillsPublishTime()
    {
        AiNotice notice = new AiNotice();
        notice.setNoticeId(1L);
        notice.setStatus("1");
        when(aiNoticeMapper.selectAiNoticeByNoticeId(1L)).thenReturn(new AiNotice());
        when(aiNoticeMapper.updateAiNotice(notice)).thenReturn(1);

        assertEquals(1, service.updateAiNotice(notice));
        assertNotNull(notice.getPublishTime());
    }

    @Test
    void updateAiNotice_publishedWithExistTime_keepsNull()
    {
        AiNotice exist = new AiNotice();
        exist.setPublishTime(new Date(1000L));
        AiNotice notice = new AiNotice();
        notice.setNoticeId(1L);
        notice.setStatus("1");
        when(aiNoticeMapper.selectAiNoticeByNoticeId(1L)).thenReturn(exist);
        when(aiNoticeMapper.updateAiNotice(notice)).thenReturn(1);

        assertEquals(1, service.updateAiNotice(notice));
        assertNull(notice.getPublishTime());
    }

    @Test
    void updateAiNotice_notPublished_skipsExistQuery()
    {
        AiNotice notice = new AiNotice();
        notice.setNoticeId(1L);
        notice.setStatus("0");
        when(aiNoticeMapper.updateAiNotice(notice)).thenReturn(1);

        assertEquals(1, service.updateAiNotice(notice));
        verify(aiNoticeMapper, never()).selectAiNoticeByNoticeId(any());
    }

    @Test
    void deleteAiNoticeByNoticeId_delegates()
    {
        when(aiNoticeMapper.deleteAiNoticeByNoticeId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiNoticeByNoticeId(1L));
    }

    @Test
    void deleteAiNoticeByNoticeIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiNoticeMapper.deleteAiNoticeByNoticeIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiNoticeByNoticeIds(ids));
    }
}
