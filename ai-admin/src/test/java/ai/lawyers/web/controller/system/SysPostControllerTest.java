package ai.lawyers.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.SysPost;
import ai.lawyers.system.service.ISysPostService;

/**
 * 岗位管理接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class SysPostControllerTest
{
    @Mock
    private ISysPostService postService;

    @InjectMocks
    private SysPostController controller;

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

    private SysPost post(String name)
    {
        SysPost post = new SysPost();
        post.setPostName(name);
        return post;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SysPost query = new SysPost();
        List<SysPost> list = new ArrayList<SysPost>();
        list.add(new SysPost());
        when(postService.selectPostList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(postService.selectPostList(any(SysPost.class))).thenReturn(List.of(new SysPost()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new SysPost());

        org.junit.jupiter.api.Assertions.assertTrue(response.getContentType().contains("spreadsheetml"));
    }

    @Test
    void 详情与下拉列表透传服务结果()
    {
        SysPost post = new SysPost();
        post.setPostId(5L);
        List<SysPost> all = new ArrayList<SysPost>();
        when(postService.selectPostById(5L)).thenReturn(post);
        when(postService.selectPostAll()).thenReturn(all);

        assertSame(post, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
        assertSame(all, controller.optionselect().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增岗位校验唯一后写入操作人()
    {
        SysPost post = post("法务专员");
        when(postService.checkPostNameUnique(post)).thenReturn(true);
        when(postService.checkPostCodeUnique(post)).thenReturn(true);
        when(postService.insertPost(post)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("admin"));
            assertEquals(200, controller.add(post).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", post.getCreateBy());
    }

    @Test
    void 新增岗位名称或编码重复返回错误()
    {
        SysPost duplicateName = post("法务专员");
        when(postService.checkPostNameUnique(duplicateName)).thenReturn(false);
        SysPost duplicateCode = post("客服");
        when(postService.checkPostNameUnique(duplicateCode)).thenReturn(true);
        when(postService.checkPostCodeUnique(duplicateCode)).thenReturn(false);

        assertEquals("新增岗位'法务专员'失败，岗位名称已存在",
                controller.add(duplicateName).get(AjaxResult.MSG_TAG));
        assertEquals("新增岗位'客服'失败，岗位编码已存在",
                controller.add(duplicateCode).get(AjaxResult.MSG_TAG));
        verify(postService, never()).insertPost(any(SysPost.class));
    }

    @Test
    void 修改岗位写入更新人()
    {
        SysPost post = post("法务专员");
        when(postService.checkPostNameUnique(post)).thenReturn(true);
        when(postService.checkPostCodeUnique(post)).thenReturn(true);
        when(postService.updatePost(post)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals(500, controller.edit(post).get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", post.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] postIds = new Long[] { 1L, 2L };
        when(postService.deletePostByIds(postIds)).thenReturn(2);

        assertEquals(200, controller.remove(postIds).get(AjaxResult.CODE_TAG));
        verify(postService).deletePostByIds(postIds);
    }
}
