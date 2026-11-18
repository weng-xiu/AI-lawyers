package ai.lawyers.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.SysNotice;
import ai.lawyers.system.service.ISysNoticeService;

/**
 * 通知公告接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysNoticeControllerTest
{
    @Mock
    private ISysNoticeService noticeService;

    @InjectMocks
    private SysNoticeController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysNotice query = new SysNotice();
        List<SysNotice> notices = new ArrayList<SysNotice>();
        notices.add(new SysNotice());
        when(noticeService.selectNoticeList(query)).thenReturn(notices);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(notices, table.getRows());
    }

    @Test
    void 详情透传服务结果()
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(3L);
        when(noticeService.selectNoticeById(3L)).thenReturn(notice);

        AjaxResult result = controller.getInfo(3L);

        assertSame(notice, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        SysNotice notice = new SysNotice();
        when(noticeService.insertNotice(any(SysNotice.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(notice);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", notice.getCreateBy());
    }

    @Test
    void 修改写入更新人()
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(4L);
        when(noticeService.updateNotice(any(SysNotice.class))).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            AjaxResult result = controller.edit(notice);
            assertEquals(500, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", notice.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] noticeIds = new Long[] { 1L, 2L, 3L };
        when(noticeService.deleteNoticeByIds(noticeIds)).thenReturn(3);

        AjaxResult result = controller.remove(noticeIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(noticeService).deleteNoticeByIds(noticeIds);
    }
}
