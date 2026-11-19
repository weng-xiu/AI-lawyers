package ai.lawyers.web.controller.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ai.lawyers.common.core.domain.R;

/**
 * swagger 用户测试接口单元测试
 *
 * @author ai-lawyers
 */
class TestControllerTest
{
    private final TestController controller = new TestController();

    @Test
    void 用户列表与详情查询()
    {
        R<List<UserEntity>> listResult = controller.userList();
        assertEquals(200, listResult.getCode());
        assertTrue(listResult.getData().stream().anyMatch(user -> "admin".equals(user.getUsername())));

        R<UserEntity> found = controller.getUser(1);
        assertEquals(200, found.getCode());
        assertEquals("admin", found.getData().getUsername());
        assertEquals("15888888888", found.getData().getMobile());

        R<UserEntity> missing = controller.getUser(9999);
        assertEquals(500, missing.getCode());
        assertEquals("用户不存在", missing.getMsg());
    }

    @Test
    void 新增用户校验与保存()
    {
        assertEquals("用户ID不能为空", controller.save(null).getMsg());
        assertEquals("用户ID不能为空", controller.save(new UserEntity()).getMsg());

        UserEntity user = new UserEntity(100, "tester", "pwd", "13800000000");
        assertEquals(200, controller.save(user).getCode());
        assertNotNull(controller.getUser(100).getData());
        assertEquals("tester", controller.getUser(100).getData().getUsername());

        assertEquals(200, controller.delete(100).getCode());
    }

    @Test
    void 更新用户校验与更新()
    {
        assertEquals("用户ID不能为空", controller.update(null).getMsg());
        assertEquals("用户不存在", controller.update(new UserEntity(200, "x", "x", "x")).getMsg());

        assertEquals(200, controller.save(new UserEntity(201, "old", "pwd", "1")).getCode());
        assertEquals(200, controller.update(new UserEntity(201, "new", "pwd2", "2")).getCode());
        assertEquals("new", controller.getUser(201).getData().getUsername());
        assertEquals("2", controller.getUser(201).getData().getMobile());

        assertEquals(200, controller.delete(201).getCode());
    }

    @Test
    void 删除用户不存在提示()
    {
        assertEquals("用户不存在", controller.delete(9999).getMsg());
    }
}
