package ai.lawyers.web.controller.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.framework.web.domain.Server;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 服务器监控控制器测试：返回服务器信息与权限注解。
 *
 * @author ai-lawyers
 */
class ServerControllerTest
{
    @Test
    void 查询服务器信息返回数据() throws Exception
    {
        AjaxResult result = new ServerController().getInfo();

        assertTrue(result.isSuccess());
        assertNotNull(result.get(AjaxResult.DATA_TAG));
        assertTrue(result.get(AjaxResult.DATA_TAG) instanceof Server);
    }

    @Test
    void 接口标注服务器查询权限() throws Exception
    {
        PreAuthorize preAuthorize = ServerController.class.getDeclaredMethod("getInfo")
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermi('monitor:server:list')", preAuthorize.value());
    }
}
