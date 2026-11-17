package ai.lawyers.common.utils.bean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

/**
 * Bean 校验工具测试：无违规放行、有违规抛出异常并透传分组。
 *
 * @author ai-lawyers
 */
class BeanValidatorsTest
{
    @Test
    void validateWithException_无违规不抛异常()
    {
        Validator validator = validator(Collections.emptySet());

        assertDoesNotThrow(() -> BeanValidators.validateWithException(validator, new Object()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateWithException_有违规抛出约束异常()
    {
        ConstraintViolation<Object> violation = violation("名称不能为空");
        Set<ConstraintViolation<Object>> violations = Set.of(violation);
        Validator validator = validator(violations);

        ConstraintViolationException exception = assertThrows(ConstraintViolationException.class,
                () -> BeanValidators.validateWithException(validator, new Object()));

        assertEquals(1, exception.getConstraintViolations().size());
        assertEquals("名称不能为空", exception.getConstraintViolations().iterator().next().getMessage());
    }

    @Test
    void validateWithException_透传校验分组与目标对象()
    {
        Object target = new Object();
        Object[][] captured = new Object[1][];
        Validator validator = (Validator) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Validator.class }, (proxy, method, args) -> {
                    if ("validate".equals(method.getName()))
                    {
                        captured[0] = args;
                        return Collections.emptySet();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        BeanValidators.validateWithException(validator, target, String.class);

        assertSame(target, captured[0][0]);
        assertArrayEquals(new Class<?>[] { String.class }, (Class<?>[]) captured[0][1]);
    }

    private static Validator validator(Set<ConstraintViolation<Object>> violations)
    {
        return (Validator) Proxy.newProxyInstance(BeanValidatorsTest.class.getClassLoader(),
                new Class<?>[] { Validator.class }, (proxy, method, args) -> {
                    if ("validate".equals(method.getName()))
                    {
                        return violations;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<Object> violation(String message)
    {
        return (ConstraintViolation<Object>) Proxy.newProxyInstance(BeanValidatorsTest.class.getClassLoader(),
                new Class<?>[] { ConstraintViolation.class }, (proxy, method, args) -> {
                    if ("getMessage".equals(method.getName()))
                    {
                        return message;
                    }
                    if ("toString".equals(method.getName()))
                    {
                        return message;
                    }
                    // 其他方法按返回类型给出默认值，避免拆箱空指针
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class)
                    {
                        return false;
                    }
                    if (returnType == int.class)
                    {
                        return 0;
                    }
                    if (returnType == long.class)
                    {
                        return 0L;
                    }
                    return null;
                });
    }
}
