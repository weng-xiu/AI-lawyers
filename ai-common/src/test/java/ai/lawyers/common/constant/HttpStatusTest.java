package ai.lawyers.common.constant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * HTTP 状态码常量测试：常用成功、客户端错误与服务端错误码取值。
 *
 * @author ai-lawyers
 */
class HttpStatusTest
{
    @Test
    void 成功类状态码()
    {
        assertEquals(200, HttpStatus.SUCCESS);
        assertEquals(201, HttpStatus.CREATED);
        assertEquals(202, HttpStatus.ACCEPTED);
        assertEquals(204, HttpStatus.NO_CONTENT);
    }

    @Test
    void 重定向与客户端错误状态码()
    {
        assertEquals(301, HttpStatus.MOVED_PERM);
        assertEquals(304, HttpStatus.NOT_MODIFIED);
        assertEquals(400, HttpStatus.BAD_REQUEST);
        assertEquals(401, HttpStatus.UNAUTHORIZED);
        assertEquals(403, HttpStatus.FORBIDDEN);
        assertEquals(404, HttpStatus.NOT_FOUND);
        assertEquals(405, HttpStatus.BAD_METHOD);
        assertEquals(409, HttpStatus.CONFLICT);
        assertEquals(415, HttpStatus.UNSUPPORTED_TYPE);
    }

    @Test
    void 服务端错误与警告状态码()
    {
        assertEquals(500, HttpStatus.ERROR);
        assertEquals(501, HttpStatus.NOT_IMPLEMENTED);
        assertEquals(601, HttpStatus.WARN);
    }
}
