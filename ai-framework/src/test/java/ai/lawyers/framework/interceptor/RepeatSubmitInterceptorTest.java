package ai.lawyers.framework.interceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import ai.lawyers.common.annotation.RepeatSubmit;

/**
 * 重复提交拦截器测试：非控制器放行、无注解放行、命中注解后的拦截与自定义提示。
 *
 * @author ai-lawyers
 */
class RepeatSubmitInterceptorTest
{
    /** 测试用控制器 */
    static class TestController
    {
        @RepeatSubmit
        public void submit()
        {
        }

        @RepeatSubmit(message = "请勿重复提交")
        public void submitWithMessage()
        {
        }

        public void other()
        {
        }
    }

    private boolean repeat;

    /** 由测试控制判定结果的拦截器实现 */
    private final RepeatSubmitInterceptor interceptor = new RepeatSubmitInterceptor()
    {
        @Override
        public boolean isRepeatSubmit(HttpServletRequest request, RepeatSubmit annotation)
        {
            return repeat;
        }
    };

    @Test
    void preHandle_非控制器方法直接放行() throws Exception
    {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), "resource"));
    }

    @Test
    void preHandle_方法无重复提交注解放行() throws Exception
    {
        HandlerMethod handler = handler("other");

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler));
    }

    @Test
    void preHandle_未判定重复时放行() throws Exception
    {
        repeat = false;
        HandlerMethod handler = handler("submit");

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler));
    }

    @Test
    void preHandle_判定重复时返回默认提示并拦截() throws Exception
    {
        repeat = true;
        HandlerMethod handler = handler("submit");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, handler));

        String body = response.getContentAsString();
        assertTrue(body.contains("不允许重复提交，请稍候再试"), body);
    }

    @Test
    void preHandle_使用注解自定义提示() throws Exception
    {
        repeat = true;
        HandlerMethod handler = handler("submitWithMessage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, handler));

        assertTrue(response.getContentAsString().contains("请勿重复提交"));
    }

    /** 构造指定方法对应的 HandlerMethod */
    private HandlerMethod handler(String methodName) throws Exception
    {
        TestController controller = new TestController();
        return new HandlerMethod(controller, TestController.class.getMethod(methodName));
    }
}
