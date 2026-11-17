package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 岗位实体测试：属性读写、默认标识与字符串输出。
 *
 * @author ai-lawyers
 */
class SysPostTest
{
    @Test
    void 属性读写()
    {
        SysPost post = new SysPost();
        post.setPostId(3L);
        post.setPostCode("legal_consultant");
        post.setPostName("法务顾问");
        post.setPostSort(1);
        post.setStatus("0");
        post.setFlag(true);

        assertEquals(3L, post.getPostId());
        assertEquals("legal_consultant", post.getPostCode());
        assertEquals("法务顾问", post.getPostName());
        assertEquals(1, post.getPostSort());
        assertEquals("0", post.getStatus());
        assertTrue(post.isFlag());
    }

    @Test
    void 默认标识为不存在()
    {
        SysPost post = new SysPost();

        assertNull(post.getPostId());
        assertNull(post.getPostSort());
        assertFalse(post.isFlag());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        SysPost post = new SysPost();
        post.setPostCode("agent_operator");
        post.setPostName("话务员");

        String text = post.toString();
        assertTrue(text.contains("agent_operator"));
        assertTrue(text.contains("话务员"));
    }
}
