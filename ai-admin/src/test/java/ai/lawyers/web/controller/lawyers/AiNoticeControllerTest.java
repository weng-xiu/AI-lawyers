package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiNotice;
import ai.lawyers.system.service.lawyers.IAiNoticeService;

/**
 * 工作台公告接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiNoticeControllerTest
{
    @Mock
    private IAiNoticeService aiNoticeService;

    @InjectMocks
    private AiNoticeController controller;

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
        AiNotice query = new AiNotice();
        List<AiNotice> notices = new ArrayList<AiNotice>();
        notices.add(new AiNotice());
        when(aiNoticeService.selectAiNoticeList(query)).thenReturn(notices);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(notices, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        AiNotice notice = new AiNotice();
        notice.setNoticeTitle("值班通知");
        when(aiNoticeService.selectAiNoticeList(any(AiNotice.class))).thenReturn(List.of(notice));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiNotice());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiNotice notice = new AiNotice();
        notice.setNoticeId(5L);
        when(aiNoticeService.selectAiNoticeByNoticeId(5L)).thenReturn(notice);

        AjaxResult result = controller.getInfo(5L);

        assertSame(notice, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写入操作人()
    {
        AiNotice notice = new AiNotice();
        when(aiNoticeService.insertAiNotice(any(AiNotice.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.add(notice);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", notice.getCreateBy());
    }

    @Test
    void 发布公告置为已发布并记录时间()
    {
        when(aiNoticeService.updateAiNotice(any(AiNotice.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            AjaxResult result = controller.publish(8L);
            assertEquals(200, result.get(AjaxResult.CODE_TAG));
        }
        ArgumentCaptor<AiNotice> captor = ArgumentCaptor.forClass(AiNotice.class);
        verify(aiNoticeService).updateAiNotice(captor.capture());
        assertEquals(8L, captor.getValue().getNoticeId());
        assertEquals("1", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getPublishTime());
        assertEquals("admin", captor.getValue().getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] noticeIds = new Long[] { 1L, 2L };
        when(aiNoticeService.deleteAiNoticeByNoticeIds(noticeIds)).thenReturn(2);

        AjaxResult result = controller.remove(noticeIds);

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(aiNoticeService).deleteAiNoticeByNoticeIds(noticeIds);
    }
}
