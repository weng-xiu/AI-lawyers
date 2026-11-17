package ai.lawyers.framework.security.handle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

/**
 * 认证失败处理测试：返回 401 码的 JSON 提示与请求地址。
 *
 * @author ai-lawyers
 */
class AuthenticationEntryPointImplTest
{
    private final AuthenticationEntryPointImpl entryPoint = new AuthenticationEntryPointImpl();

    @Test
    void commence_返回未认证JSON信息() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, mock(AuthenticationException.class));

        // ServletUtils.renderString 固定写 200，业务码放在 JSON 内
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        assertEquals("utf-8", response.getCharacterEncoding());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":401"), body);
        assertTrue(body.contains("请求访问：/system/user/list"), body);
    }

    @Test
    void commence_异常对象为空时同样返回提示() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/call");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, null);

        assertTrue(response.getContentAsString().contains("/api/call"));
    }
}
