package ai.lawyers.system.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 操作日志实体测试：属性读写与业务类型数组。
 *
 * @author ai-lawyers
 */
class SysOperLogTest
{
    @Test
    void 属性读写()
    {
        SysOperLog operLog = new SysOperLog();
        operLog.setOperId(1L);
        operLog.setTitle("用户管理");
        operLog.setBusinessType(2);
        operLog.setMethod("ai.lawyers.system.controller.SysUserController.edit()");
        operLog.setRequestMethod("PUT");
        operLog.setOperatorType(1);
        operLog.setOperName("admin");
        operLog.setDeptName("法务部");
        operLog.setOperUrl("/system/user");
        operLog.setOperIp("127.0.0.1");
        operLog.setOperLocation("内网IP");
        operLog.setOperParam("{\"userId\":1}");
        operLog.setJsonResult("{\"code\":200}");
        operLog.setStatus(0);
        operLog.setErrorMsg("");

        assertEquals(1L, operLog.getOperId());
        assertEquals("用户管理", operLog.getTitle());
        assertEquals(2, operLog.getBusinessType());
        assertEquals("ai.lawyers.system.controller.SysUserController.edit()", operLog.getMethod());
        assertEquals("PUT", operLog.getRequestMethod());
        assertEquals(1, operLog.getOperatorType());
        assertEquals("admin", operLog.getOperName());
        assertEquals("法务部", operLog.getDeptName());
        assertEquals("/system/user", operLog.getOperUrl());
        assertEquals("127.0.0.1", operLog.getOperIp());
        assertEquals("内网IP", operLog.getOperLocation());
        assertEquals("{\"userId\":1}", operLog.getOperParam());
        assertEquals("{\"code\":200}", operLog.getJsonResult());
        assertEquals(0, operLog.getStatus());
        assertEquals("", operLog.getErrorMsg());
    }

    @Test
    void 业务类型数组()
    {
        SysOperLog operLog = new SysOperLog();
        operLog.setBusinessTypes(new Integer[] { 1, 2, 3 });

        assertArrayEquals(new Integer[] { 1, 2, 3 }, operLog.getBusinessTypes());
        assertEquals(3, operLog.getBusinessTypes().length);
    }

    @Test
    void 默认值为空()
    {
        SysOperLog operLog = new SysOperLog();

        assertNull(operLog.getOperId());
        assertNull(operLog.getBusinessType());
        assertNull(operLog.getBusinessTypes());
        assertNull(operLog.getStatus());
    }
}
