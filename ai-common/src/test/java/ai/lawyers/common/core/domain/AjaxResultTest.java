package ai.lawyers.common.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.constant.HttpStatus;

/**
 * 操作消息提醒测试：构造器、成功/警告/错误工厂方法、状态判定与链式调用。
 *
 * @author ai-lawyers
 */
class AjaxResultTest
{
    @Test
    void 构造器_写入状态码与消息()
    {
        AjaxResult result = new AjaxResult(200, "成功");

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertEquals("成功", result.get(AjaxResult.MSG_TAG));
        assertFalse(result.containsKey(AjaxResult.DATA_TAG));
    }

    @Test
    void 构造器_数据为空时不写入数据键()
    {
        AjaxResult nullData = new AjaxResult(200, "成功", null);
        assertFalse(nullData.containsKey(AjaxResult.DATA_TAG));

        AjaxResult withData = new AjaxResult(200, "成功", "载荷");
        assertEquals("载荷", withData.get(AjaxResult.DATA_TAG));
    }

    @Test
    void success_默认与自定义消息()
    {
        AjaxResult defaultResult = AjaxResult.success();
        assertEquals(HttpStatus.SUCCESS, defaultResult.get(AjaxResult.CODE_TAG));
        assertEquals("操作成功", defaultResult.get(AjaxResult.MSG_TAG));

        AjaxResult custom = AjaxResult.success("已完成");
        assertEquals("已完成", custom.get(AjaxResult.MSG_TAG));
        assertFalse(custom.containsKey(AjaxResult.DATA_TAG));
    }

    @Test
    void success_带数据()
    {
        Map<String, String> data = Map.of("k", "v");

        AjaxResult result = AjaxResult.success(data);
        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals("操作成功", result.get(AjaxResult.MSG_TAG));
        assertSame(data, result.get(AjaxResult.DATA_TAG));

        AjaxResult custom = AjaxResult.success("查询完成", data);
        assertEquals("查询完成", custom.get(AjaxResult.MSG_TAG));
        assertSame(data, custom.get(AjaxResult.DATA_TAG));
    }

    @Test
    void warn_默认与带数据()
    {
        AjaxResult result = AjaxResult.warn("注意");

        assertEquals(HttpStatus.WARN, result.get(AjaxResult.CODE_TAG));
        assertEquals("注意", result.get(AjaxResult.MSG_TAG));
        assertFalse(result.containsKey(AjaxResult.DATA_TAG));

        AjaxResult withData = AjaxResult.warn("注意", 1);
        assertEquals(1, withData.get(AjaxResult.DATA_TAG));
    }

    @Test
    void error_默认自定义与指定状态码()
    {
        assertEquals("操作失败", AjaxResult.error().get(AjaxResult.MSG_TAG));
        assertEquals("参数错误", AjaxResult.error("参数错误").get(AjaxResult.MSG_TAG));

        AjaxResult withData = AjaxResult.error("异常", "详情");
        assertEquals(HttpStatus.ERROR, withData.get(AjaxResult.CODE_TAG));
        assertEquals("详情", withData.get(AjaxResult.DATA_TAG));

        AjaxResult customCode = AjaxResult.error(401, "未授权");
        assertEquals(401, customCode.get(AjaxResult.CODE_TAG));
        assertEquals("未授权", customCode.get(AjaxResult.MSG_TAG));
        assertFalse(customCode.containsKey(AjaxResult.DATA_TAG));
    }

    @Test
    void 状态判定_依据状态码()
    {
        assertTrue(AjaxResult.success().isSuccess());
        assertFalse(AjaxResult.success().isWarn());
        assertFalse(AjaxResult.success().isError());

        assertTrue(AjaxResult.warn("w").isWarn());
        assertFalse(AjaxResult.warn("w").isSuccess());

        assertTrue(AjaxResult.error().isError());
        assertFalse(AjaxResult.error().isSuccess());

        // 空结果没有任何状态码，三种判定均为 false
        AjaxResult empty = new AjaxResult();
        assertNull(empty.get(AjaxResult.CODE_TAG));
        assertFalse(empty.isSuccess());
        assertFalse(empty.isWarn());
        assertFalse(empty.isError());
    }

    @Test
    void put_返回自身支持链式调用()
    {
        AjaxResult result = new AjaxResult();

        assertSame(result, result.put("a", 1).put("b", 2));
        assertEquals(1, result.get("a"));
        assertEquals(2, result.get("b"));
    }
}
