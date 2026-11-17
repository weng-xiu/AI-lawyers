package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import ai.lawyers.common.utils.spring.SpringUtils;

/**
 * 国际化消息工具测试：委托 MessageSource 并传递当前 Locale。
 *
 * @author ai-lawyers
 */
class MessageUtilsTest
{
    private final List<Object[]> invocations = new ArrayList<>();

    private Object oldBeanFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        MessageSource messageSource = (MessageSource) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { MessageSource.class },
                (proxy, method, args) -> {
                    if ("getMessage".equals(method.getName()))
                    {
                        invocations.add(args);
                        return "translated:" + args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        oldBeanFactory = getBeanFactoryField().get(null);
        ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { ConfigurableListableBeanFactory.class },
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] == MessageSource.class)
                    {
                        return messageSource;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        getBeanFactoryField().set(null, beanFactory);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        getBeanFactoryField().set(null, oldBeanFactory);
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void message_携带消息键与参数()
    {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        String result = MessageUtils.message("user.login.fail", "张三", 3);

        assertEquals("translated:user.login.fail", result);
        assertEquals(1, invocations.size());
        assertEquals("user.login.fail", invocations.get(0)[0]);
        assertArrayEquals(new Object[] { "张三", 3 }, (Object[]) invocations.get(0)[1]);
        assertEquals(Locale.SIMPLIFIED_CHINESE, invocations.get(0)[2]);
    }

    @Test
    void message_无参数()
    {
        String result = MessageUtils.message("user.not.login");

        assertEquals("translated:user.not.login", result);
        assertArrayEquals(new Object[0], (Object[]) invocations.get(0)[1]);
    }

    private static Field getBeanFactoryField() throws NoSuchFieldException
    {
        Field field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        return field;
    }
}
