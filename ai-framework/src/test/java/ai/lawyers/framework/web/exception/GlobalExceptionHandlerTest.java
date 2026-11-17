package ai.lawyers.framework.web.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.exception.DemoModeException;
import ai.lawyers.common.exception.ServiceException;

/**
 * 全局异常处理测试：权限、请求方式、业务异常码、参数异常、运行时异常与校验异常的统一返回。
 *
 * @author ai-lawyers
 */
class GlobalExceptionHandlerTest
{
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

    /** 用于构造 MethodParameter 的示例方法 */
    static class DemoController
    {
        public void detail(String id)
        {
        }
    }

    @Test
    void handleAccessDeniedException_返回403()
    {
        AjaxResult result = handler.handleAccessDeniedException(new AccessDeniedException("denied"), request);

        assertEquals(Integer.valueOf(403), code(result));
        assertEquals("没有权限，请联系管理员授权", msg(result));
    }

    @Test
    void handleHttpRequestMethodNotSupported_返回不支持提示()
    {
        AjaxResult result = handler.handleHttpRequestMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT"), request);

        assertEquals(Integer.valueOf(500), code(result));
        assertTrue(msg(result).contains("PUT"));
    }

    @Test
    void handleServiceException_保留业务错误码()
    {
        AjaxResult result = handler.handleServiceException(new ServiceException("参数错误", 400), request);

        assertEquals(Integer.valueOf(400), code(result));
        assertEquals("参数错误", msg(result));
    }

    @Test
    void handleServiceException_无错误码时使用默认500()
    {
        AjaxResult result = handler.handleServiceException(new ServiceException("业务失败"), request);

        assertEquals(Integer.valueOf(500), code(result));
        assertEquals("业务失败", msg(result));
    }

    @Test
    void handleMissingPathVariableException_提示缺少变量名() throws Exception
    {
        Method method = DemoController.class.getDeclaredMethod("detail", String.class);
        MissingPathVariableException exception = new MissingPathVariableException("id", new MethodParameter(method, 0));

        AjaxResult result = handler.handleMissingPathVariableException(exception, request);

        assertTrue(msg(result).contains("[id]"));
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void handleMethodArgumentTypeMismatchException_清除脚本并提示类型()
    {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getValue()).thenReturn("<script>abc</script>");
        when(exception.getName()).thenReturn("age");
        when(exception.getRequiredType()).thenReturn((Class) Integer.class);

        AjaxResult result = handler.handleMethodArgumentTypeMismatchException(exception, request);

        String message = msg(result);
        assertTrue(message.contains("age"), message);
        assertTrue(message.contains("java.lang.Integer"), message);
        // 输入值需要经过 HTML 清理，不能保留脚本标签
        assertTrue(message.contains("abc"), message);
        assertTrue(!message.contains("<script>"), message);
    }

    @Test
    void handleRuntimeException_返回异常信息()
    {
        AjaxResult result = handler.handleRuntimeException(new IllegalStateException("boom"), request);

        assertEquals(Integer.valueOf(500), code(result));
        assertEquals("boom", msg(result));
    }

    @Test
    void handleException_返回异常信息()
    {
        AjaxResult result = handler.handleException(new Exception("io error"), request);

        assertEquals(Integer.valueOf(500), code(result));
        assertEquals("io error", msg(result));
    }

    @Test
    void handleBindException_返回首个校验提示()
    {
        ObjectError error = mock(ObjectError.class);
        when(error.getDefaultMessage()).thenReturn("用户名不能为空");
        BindException exception = mock(BindException.class);
        when(exception.getAllErrors()).thenReturn(Collections.singletonList(error));

        AjaxResult result = handler.handleBindException(exception);

        assertEquals("用户名不能为空", msg(result));
    }

    @Test
    void handleMethodArgumentNotValidException_返回字段校验提示()
    {
        FieldError fieldError = mock(FieldError.class);
        when(fieldError.getDefaultMessage()).thenReturn("手机号格式不正确");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldError()).thenReturn(fieldError);
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        Object result = handler.handleMethodArgumentNotValidException(exception);

        assertEquals("手机号格式不正确", msg((AjaxResult) result));
    }

    @Test
    void handleDemoModeException_返回演示模式提示()
    {
        AjaxResult result = handler.handleDemoModeException(new DemoModeException());

        assertEquals("演示模式，不允许操作", msg(result));
    }

    /** 读取返回体中的业务码 */
    private Integer code(AjaxResult result)
    {
        return (Integer) result.get(AjaxResult.CODE_TAG);
    }

    /** 读取返回体中的提示信息 */
    private String msg(AjaxResult result)
    {
        return (String) result.get(AjaxResult.MSG_TAG);
    }
}
