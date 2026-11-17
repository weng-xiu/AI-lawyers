package ai.lawyers.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * HTTP 请求方式枚举测试：名称解析、未知值与匹配判定。
 *
 * @author ai-lawyers
 */
class HttpMethodTest
{
    @Test
    void 解析标准请求方式()
    {
        assertEquals(HttpMethod.GET, HttpMethod.resolve("GET"));
        assertEquals(HttpMethod.POST, HttpMethod.resolve("POST"));
        assertEquals(HttpMethod.DELETE, HttpMethod.resolve("DELETE"));
        assertEquals(8, HttpMethod.values().length);
    }

    @Test
    void 未知或空请求方式返回空()
    {
        assertNull(HttpMethod.resolve(null));
        assertNull(HttpMethod.resolve("get"));
        assertNull(HttpMethod.resolve("CONNECT"));
    }

    @Test
    void matches区分大小写并识别空值()
    {
        assertTrue(HttpMethod.PUT.matches("PUT"));
        assertFalse(HttpMethod.PUT.matches("put"));
        assertFalse(HttpMethod.PUT.matches(null));
        assertFalse(HttpMethod.GET.matches("POST"));
    }
}
