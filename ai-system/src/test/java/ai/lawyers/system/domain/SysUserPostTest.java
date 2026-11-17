package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 用户岗位关联实体测试：属性读写与字符串输出。
 *
 * @author ai-lawyers
 */
class SysUserPostTest
{
    @Test
    void 属性读写()
    {
        SysUserPost userPost = new SysUserPost();
        userPost.setUserId(100L);
        userPost.setPostId(6L);

        assertEquals(100L, userPost.getUserId());
        assertEquals(6L, userPost.getPostId());
    }

    @Test
    void 默认值为空()
    {
        SysUserPost userPost = new SysUserPost();

        assertNull(userPost.getUserId());
        assertNull(userPost.getPostId());
    }

    @Test
    void 字符串输出包含关联标识()
    {
        SysUserPost userPost = new SysUserPost();
        userPost.setUserId(101L);
        userPost.setPostId(7L);

        String text = userPost.toString();
        assertTrue(text.contains("userId"));
        assertTrue(text.contains("101"));
    }
}
