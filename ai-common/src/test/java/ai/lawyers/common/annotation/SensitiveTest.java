package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.lawyers.common.config.serializer.SensitiveJsonSerializer;
import ai.lawyers.common.enums.DesensitizedType;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * 数据脱敏注解测试：元注解、序列化器绑定与脱敏类型。
 *
 * @author ai-lawyers
 */
class SensitiveTest
{
    static class UserView
    {
        @Sensitive(desensitizedType = DesensitizedType.PHONE)
        private String phone;

        @Sensitive(desensitizedType = DesensitizedType.ID_CARD)
        private String idCard;
    }

    @Test
    void 保留策略为运行时且仅作用于字段()
    {
        assertEquals(RetentionPolicy.RUNTIME, Sensitive.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.FIELD, Sensitive.class.getAnnotation(Target.class).value()[0]);
    }

    @Test
    void 绑定Jackson注解与脱敏序列化器()
    {
        assertTrue(Sensitive.class.isAnnotationPresent(JacksonAnnotationsInside.class));
        JsonSerialize jsonSerialize = Sensitive.class.getAnnotation(JsonSerialize.class);

        assertEquals(SensitiveJsonSerializer.class, jsonSerialize.using());
    }

    @Test
    void 脱敏类型可指定() throws Exception
    {
        Field phone = UserView.class.getDeclaredField("phone");
        Field idCard = UserView.class.getDeclaredField("idCard");

        assertEquals(DesensitizedType.PHONE, phone.getAnnotation(Sensitive.class).desensitizedType());
        assertEquals(DesensitizedType.ID_CARD, idCard.getAnnotation(Sensitive.class).desensitizedType());
    }
}
