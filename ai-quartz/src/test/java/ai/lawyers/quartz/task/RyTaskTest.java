package ai.lawyers.quartz.task;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 定时任务调度测试类单元测试
 *
 * @author ai-lawyers
 */
class RyTaskTest
{
    private final RyTask ryTask = new RyTask();

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private PrintStream originalOut;

    @BeforeEach
    void setUp()
    {
        originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown()
    {
        System.setOut(originalOut);
    }

    private String printed()
    {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void 多参方法按格式输出全部参数()
    {
        ryTask.ryMultipleParams("文本", Boolean.TRUE, 100L, 1.5D, 9);

        String text = printed();
        assertTrue(text.contains("执行多参方法"));
        assertTrue(text.contains("文本"));
        assertTrue(text.contains("true"));
        assertTrue(text.contains("100"));
        assertTrue(text.contains("1.5"));
        assertTrue(text.contains("9"));
    }

    @Test
    void 有参方法输出参数内容()
    {
        ryTask.ryParams("测试参数");

        assertTrue(printed().contains("执行有参方法：测试参数"));
    }

    @Test
    void 无参方法正常执行()
    {
        ryTask.ryNoParams();

        assertTrue(printed().contains("执行无参方法"));
    }
}
