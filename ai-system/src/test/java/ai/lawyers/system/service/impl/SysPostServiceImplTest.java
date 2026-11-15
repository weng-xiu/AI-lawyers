package ai.lawyers.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.SysPost;
import ai.lawyers.system.mapper.SysPostMapper;
import ai.lawyers.system.mapper.SysUserPostMapper;

/**
 * 岗位服务单测。
 *
 * <p>覆盖岗位查询、名称/编码唯一性校验、岗位分配数量统计、
 * 删除前的岗位占用校验以及新增修改。</p>
 *
 * @author ai-lawyers
 */
class SysPostServiceImplTest
{
    private SysPostServiceImpl service;

    @Mock private SysPostMapper postMapper;
    @Mock private SysUserPostMapper userPostMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new SysPostServiceImpl();
        ReflectionTestUtils.setField(service, "postMapper", postMapper);
        ReflectionTestUtils.setField(service, "userPostMapper", userPostMapper);
    }

    private static SysPost post(Long postId, String postCode, String postName)
    {
        SysPost post = new SysPost();
        post.setPostId(postId);
        post.setPostCode(postCode);
        post.setPostName(postName);
        return post;
    }

    @Test
    void selectPostList_delegates()
    {
        SysPost query = new SysPost();
        List<SysPost> list = Collections.singletonList(post(1L, "ceo", "董事长"));
        when(postMapper.selectPostList(query)).thenReturn(list);

        assertSame(list, service.selectPostList(query));
    }

    @Test
    void selectPostAll_delegates()
    {
        List<SysPost> list = Collections.singletonList(post(1L, "ceo", "董事长"));
        when(postMapper.selectPostAll()).thenReturn(list);

        assertSame(list, service.selectPostAll());
    }

    @Test
    void selectPostById_delegates()
    {
        SysPost expected = post(1L, "ceo", "董事长");
        when(postMapper.selectPostById(1L)).thenReturn(expected);

        assertSame(expected, service.selectPostById(1L));
    }

    @Test
    void selectPostListByUserId_delegates()
    {
        List<Long> ids = Arrays.asList(1L, 2L);
        when(postMapper.selectPostListByUserId(9L)).thenReturn(ids);

        assertSame(ids, service.selectPostListByUserId(9L));
    }

    @Test
    void checkPostNameUnique_notExists_returnsUnique()
    {
        when(postMapper.checkPostNameUnique("董事长")).thenReturn(null);

        assertTrue(service.checkPostNameUnique(post(null, "ceo", "董事长")));
    }

    @Test
    void checkPostNameUnique_sameId_returnsUnique()
    {
        when(postMapper.checkPostNameUnique("董事长")).thenReturn(post(1L, "ceo", "董事长"));

        assertTrue(service.checkPostNameUnique(post(1L, "ceo", "董事长")));
    }

    @Test
    void checkPostNameUnique_otherId_returnsNotUnique()
    {
        when(postMapper.checkPostNameUnique("董事长")).thenReturn(post(2L, "ceo", "董事长"));

        assertFalse(service.checkPostNameUnique(post(1L, "ceo", "董事长")));
    }

    @Test
    void checkPostCodeUnique_notExists_returnsUnique()
    {
        when(postMapper.checkPostCodeUnique("ceo")).thenReturn(null);

        assertTrue(service.checkPostCodeUnique(post(null, "ceo", "董事长")));
    }

    @Test
    void checkPostCodeUnique_sameId_returnsUnique()
    {
        when(postMapper.checkPostCodeUnique("ceo")).thenReturn(post(1L, "ceo", "董事长"));

        assertTrue(service.checkPostCodeUnique(post(1L, "ceo", "董事长")));
    }

    @Test
    void checkPostCodeUnique_otherId_returnsNotUnique()
    {
        when(postMapper.checkPostCodeUnique("ceo")).thenReturn(post(2L, "ceo", "董事长"));

        assertFalse(service.checkPostCodeUnique(post(1L, "ceo", "董事长")));
    }

    @Test
    void countUserPostById_delegates()
    {
        when(userPostMapper.countUserPostById(1L)).thenReturn(3);

        assertEquals(3, service.countUserPostById(1L));
    }

    @Test
    void deletePostById_delegates()
    {
        when(postMapper.deletePostById(1L)).thenReturn(1);

        assertEquals(1, service.deletePostById(1L));
    }

    @Test
    void deletePostByIds_assigned_throws()
    {
        when(postMapper.selectPostById(1L)).thenReturn(post(1L, "ceo", "董事长"));
        when(userPostMapper.countUserPostById(1L)).thenReturn(2);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deletePostByIds(new Long[] { 1L }));
        assertEquals("董事长已分配,不能删除", ex.getMessage());
        verify(postMapper, never()).deletePostByIds(any());
    }

    @Test
    void deletePostByIds_normal_deletes()
    {
        when(postMapper.selectPostById(1L)).thenReturn(post(1L, "ceo", "董事长"));
        when(userPostMapper.countUserPostById(1L)).thenReturn(0);
        when(postMapper.deletePostByIds(any(Long[].class))).thenReturn(1);

        Long[] ids = new Long[] { 1L };

        assertEquals(1, service.deletePostByIds(ids));
        verify(postMapper).deletePostByIds(ids);
    }

    @Test
    void insertPost_delegates()
    {
        SysPost post = post(null, "ceo", "董事长");
        when(postMapper.insertPost(post)).thenReturn(1);

        assertEquals(1, service.insertPost(post));
    }

    @Test
    void updatePost_delegates()
    {
        SysPost post = post(1L, "ceo", "董事长");
        when(postMapper.updatePost(post)).thenReturn(1);

        assertEquals(1, service.updatePost(post));
    }
}
