package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.SysNotice;
import ai.lawyers.system.mapper.SysNoticeMapper;

/**
 * 通知公告服务单测。
 *
 * <p>覆盖公告的查询、新增、修改与删除委托。</p>
 *
 * @author ai-lawyers
 */
class SysNoticeServiceImplTest
{
    private SysNoticeServiceImpl service;

    @Mock private SysNoticeMapper noticeMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysNoticeServiceImpl();
        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
    }

    @Test
    void selectNoticeById_delegates()
    {
        SysNotice expected = new SysNotice();
        when(noticeMapper.selectNoticeById(1L)).thenReturn(expected);

        assertSame(expected, service.selectNoticeById(1L));
    }

    @Test
    void selectNoticeList_delegates()
    {
        SysNotice query = new SysNotice();
        List<SysNotice> list = Collections.singletonList(new SysNotice());
        when(noticeMapper.selectNoticeList(query)).thenReturn(list);

        assertSame(list, service.selectNoticeList(query));
    }

    @Test
    void insertNotice_delegates()
    {
        SysNotice notice = new SysNotice();
        when(noticeMapper.insertNotice(notice)).thenReturn(1);

        assertEquals(1, service.insertNotice(notice));
    }

    @Test
    void updateNotice_delegates()
    {
        SysNotice notice = new SysNotice();
        when(noticeMapper.updateNotice(notice)).thenReturn(1);

        assertEquals(1, service.updateNotice(notice));
    }

    @Test
    void deleteNoticeById_delegates()
    {
        when(noticeMapper.deleteNoticeById(1L)).thenReturn(1);

        assertEquals(1, service.deleteNoticeById(1L));
    }

    @Test
    void deleteNoticeByIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(noticeMapper.deleteNoticeByIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteNoticeByIds(ids));
        verify(noticeMapper).deleteNoticeByIds(ids);
    }
}
