package ai.lawyers.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 日志块工具测试：包装内容与空值处理。
 *
 * @author ai-lawyers
 */
class LogUtilsTest
{
    @Test
    void getBlock_普通字符串加方括号()
    {
        assertEquals("[查询用户]", LogUtils.getBlock("查询用户"));
    }

    @Test
    void getBlock_空值返回空方括号()
    {
        assertEquals("[]", LogUtils.getBlock(null));
    }

    @Test
    void getBlock_对象使用toString()
    {
        assertEquals("[1]", LogUtils.getBlock(1));
        assertEquals("[" + Map.of("k", "v") + "]", LogUtils.getBlock(Map.of("k", "v")));
    }
}
