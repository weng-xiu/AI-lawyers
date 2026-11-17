package ai.lawyers.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 用户状态枚举测试：状态码与描述映射。
 *
 * @author ai-lawyers
 */
class UserStatusTest
{
    @Test
    void 状态码与描述一一对应()
    {
        assertEquals("0", UserStatus.OK.getCode());
        assertEquals("正常", UserStatus.OK.getInfo());
        assertEquals("1", UserStatus.DISABLE.getCode());
        assertEquals("停用", UserStatus.DISABLE.getInfo());
        assertEquals("2", UserStatus.DELETED.getCode());
        assertEquals("删除", UserStatus.DELETED.getInfo());
    }

    @Test
    void 按名称查找状态()
    {
        assertEquals(UserStatus.OK, UserStatus.valueOf("OK"));
        assertEquals(UserStatus.DISABLE, UserStatus.valueOf("DISABLE"));
        assertEquals(3, UserStatus.values().length);
    }
}
