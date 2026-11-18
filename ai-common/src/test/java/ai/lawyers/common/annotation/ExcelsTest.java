package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * Excel 注解集测试：元注解与多个 Excel 注解的聚合。
 *
 * @author ai-lawyers
 */
class ExcelsTest
{
    static class ExportRow
    {
        @Excels({ @Excel(name = "姓名", sort = 1), @Excel(name = "年龄", sort = 2) })
        private String field;
    }

    @Test
    void 保留策略为运行时且仅作用于字段()
    {
        assertEquals(RetentionPolicy.RUNTIME, Excels.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.FIELD, Excels.class.getAnnotation(Target.class).value()[0]);
    }

    @Test
    void 聚合多个Excel注解() throws Exception
    {
        Field field = ExportRow.class.getDeclaredField("field");
        Excels excels = field.getAnnotation(Excels.class);

        assertEquals(2, excels.value().length);
        assertEquals("姓名", excels.value()[0].name());
        assertEquals("年龄", excels.value()[1].name());
        assertEquals(1, excels.value()[0].sort());
        assertEquals(2, excels.value()[1].sort());
    }
}
