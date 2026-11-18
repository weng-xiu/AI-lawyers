package ai.lawyers.common.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.lawyers.common.utils.poi.ExcelHandlerAdapter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.junit.jupiter.api.Test;

/**
 * Excel 导出注解测试：元注解、属性默认值、自定义值与枚举取值。
 *
 * @author ai-lawyers
 */
class ExcelTest
{
    static class ExportRow
    {
        @Excel
        private String defaultField;

        @Excel(name = "金额", sort = 1, dateFormat = "yyyy-MM-dd", dictType = "sys_status", scale = 2,
                suffix = "元", defaultValue = "0", wrapText = true, needMerge = true, isExport = false,
                isStatistics = true, cellType = Excel.ColumnType.NUMERIC, type = Excel.Type.EXPORT,
                combo = { "男", "女" }, args = { "arg" })
        private BigDecimal amount;
    }

    @Test
    void 保留策略为运行时且仅作用于字段()
    {
        assertEquals(RetentionPolicy.RUNTIME, Excel.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.FIELD, Excel.class.getAnnotation(Target.class).value()[0]);
    }

    @Test
    void 属性默认值() throws Exception
    {
        Field field = ExportRow.class.getDeclaredField("defaultField");
        Excel excel = field.getAnnotation(Excel.class);

        assertEquals(Integer.MAX_VALUE, excel.sort());
        assertEquals("", excel.name());
        assertEquals("", excel.dateFormat());
        assertEquals("", excel.dictType());
        assertEquals("", excel.readConverterExp());
        assertEquals(",", excel.separator());
        assertEquals(-1, excel.scale());
        assertEquals(BigDecimal.ROUND_HALF_EVEN, excel.roundingMode());
        assertEquals(14D, excel.height());
        assertEquals(16D, excel.width());
        assertEquals("", excel.suffix());
        assertEquals("", excel.defaultValue());
        assertEquals("", excel.prompt());
        assertFalse(excel.wrapText());
        assertEquals(0, excel.combo().length);
        assertFalse(excel.comboReadDict());
        assertFalse(excel.needMerge());
        assertTrue(excel.isExport());
        assertEquals("", excel.targetAttr());
        assertFalse(excel.isStatistics());
        assertEquals(Excel.ColumnType.STRING, excel.cellType());
        assertEquals(IndexedColors.GREY_50_PERCENT, excel.headerBackgroundColor());
        assertEquals(IndexedColors.WHITE, excel.headerColor());
        assertEquals(IndexedColors.WHITE, excel.backgroundColor());
        assertEquals(IndexedColors.BLACK, excel.color());
        assertEquals(HorizontalAlignment.CENTER, excel.align());
        assertEquals(ExcelHandlerAdapter.class, excel.handler());
        assertEquals(0, excel.args().length);
        assertEquals(Excel.Type.ALL, excel.type());
    }

    @Test
    void 属性可自定义() throws Exception
    {
        Field field = ExportRow.class.getDeclaredField("amount");
        Excel excel = field.getAnnotation(Excel.class);

        assertEquals("金额", excel.name());
        assertEquals(1, excel.sort());
        assertEquals("yyyy-MM-dd", excel.dateFormat());
        assertEquals("sys_status", excel.dictType());
        assertEquals(2, excel.scale());
        assertEquals("元", excel.suffix());
        assertEquals("0", excel.defaultValue());
        assertTrue(excel.wrapText());
        assertTrue(excel.needMerge());
        assertFalse(excel.isExport());
        assertTrue(excel.isStatistics());
        assertEquals(Excel.ColumnType.NUMERIC, excel.cellType());
        assertEquals(Excel.Type.EXPORT, excel.type());
        assertEquals(2, excel.combo().length);
        assertEquals("男", excel.combo()[0]);
        assertEquals("arg", excel.args()[0]);
    }

    @Test
    void 枚举取值()
    {
        assertEquals(0, Excel.Type.ALL.value());
        assertEquals(1, Excel.Type.EXPORT.value());
        assertEquals(2, Excel.Type.IMPORT.value());
        assertEquals(0, Excel.ColumnType.NUMERIC.value());
        assertEquals(1, Excel.ColumnType.STRING.value());
        assertEquals(2, Excel.ColumnType.IMAGE.value());
        assertEquals(3, Excel.ColumnType.TEXT.value());
    }
}
