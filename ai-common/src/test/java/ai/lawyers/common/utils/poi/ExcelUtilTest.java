package ai.lawyers.common.utils.poi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import org.apache.poi.ss.usermodel.DateUtil;
import org.junit.jupiter.api.Test;

/**
 * Excel 工具类测试：表达式转换、文件名编码与日期格式化。
 *
 * @author ai-lawyers
 */
class ExcelUtilTest
{
    private static final String CONVERTER_EXP = "0=男,1=女,2=未知";

    @Test
    void 正向解析单个值()
    {
        assertEquals("男", ExcelUtil.convertByExp("0", CONVERTER_EXP, ","));
        assertEquals("女", ExcelUtil.convertByExp("1", CONVERTER_EXP, ","));
        assertEquals("未知", ExcelUtil.convertByExp("2", CONVERTER_EXP, ","));
    }

    @Test
    void 正向解析多个值()
    {
        assertEquals("男,女", ExcelUtil.convertByExp("0,1", CONVERTER_EXP, ","));
        assertEquals("男", ExcelUtil.convertByExp("0,9", CONVERTER_EXP, ","));
    }

    @Test
    void 正向解析无匹配值返回空字符串()
    {
        assertEquals("", ExcelUtil.convertByExp("9", CONVERTER_EXP, ","));
    }

    @Test
    void 反向解析标签为值()
    {
        assertEquals("0", ExcelUtil.reverseByExp("男", CONVERTER_EXP, ","));
        assertEquals("0,1", ExcelUtil.reverseByExp("男,女", CONVERTER_EXP, ","));
        assertEquals("", ExcelUtil.reverseByExp("其他", CONVERTER_EXP, ","));
    }

    @Test
    void 文件名编码包含原文件名与扩展名()
    {
        ExcelUtil<Object> util = new ExcelUtil<>(Object.class);

        String first = util.encodingFilename("用户数据");
        String second = util.encodingFilename("用户数据");

        assertTrue(first.endsWith("_用户数据.xlsx"));
        assertNotEquals(first, second);
    }

    @Test
    void 日期格式化支持多种时间类型()
    {
        ExcelUtil<Object> util = new ExcelUtil<>(Object.class);
        LocalDate localDate = LocalDate.of(2026, 1, 5);
        LocalDateTime localDateTime = LocalDateTime.of(2026, 1, 5, 8, 30);
        Date date = DateUtil.getJavaDate(46027D);

        assertEquals("2026-01-05", util.parseDateToStr("yyyy-MM-dd", localDate));
        assertEquals("2026-01-05 08:30", util.parseDateToStr("yyyy-MM-dd HH:mm", localDateTime));
        assertEquals("2026-01-05", util.parseDateToStr("yyyy-MM-dd", date));
        assertEquals("文本", util.parseDateToStr("yyyy-MM-dd", "文本"));
        assertEquals("", util.parseDateToStr("yyyy-MM-dd", null));
    }
}
