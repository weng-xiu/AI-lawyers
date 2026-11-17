package ai.lawyers.common.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.constant.HttpStatus;

/**
 * 统一响应体测试：成功/失败工厂方法、自定义状态码、成功判定。
 *
 * @author ai-lawyers
 */
class RTest
{
    @Test
    void ok_无数据()
    {
        R<Void> r = R.ok();

        assertEquals(HttpStatus.SUCCESS, r.getCode());
        assertEquals("操作成功", r.getMsg());
        assertNull(r.getData());
    }

    @Test
    void ok_带数据与自定义消息()
    {
        R<String> r = R.ok("结果");

        assertEquals(R.SUCCESS, r.getCode());
        assertEquals("结果", r.getData());

        R<String> custom = R.ok("结果", "查询完成");
        assertEquals("查询完成", custom.getMsg());
        assertEquals("结果", custom.getData());
    }

    @Test
    void fail_默认与自定义消息()
    {
        R<Void> r = R.fail();

        assertEquals(R.FAIL, r.getCode());
        assertEquals("操作失败", r.getMsg());
        assertNull(r.getData());

        assertEquals("库存不足", R.fail("库存不足").getMsg());
        // 带任意类型数据时使用默认失败消息
        R<Integer> dataFail = R.fail(42);
        assertEquals(Integer.valueOf(42), dataFail.getData());
        assertEquals("操作失败", dataFail.getMsg());
        assertEquals("冲突", R.fail(409, "冲突").getMsg());
        assertEquals(409, R.fail(409, "冲突").getCode());
    }

    @Test
    void 成功与失败判定()
    {
        assertTrue(R.isSuccess(R.ok()));
        assertFalse(R.isError(R.ok()));

        assertFalse(R.isSuccess(R.fail()));
        assertTrue(R.isError(R.fail()));

        // 非 200 自定义状态码视为失败
        assertFalse(R.isSuccess(R.fail(401, "未授权")));
    }
}
