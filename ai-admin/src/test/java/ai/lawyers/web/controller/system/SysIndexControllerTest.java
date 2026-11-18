package ai.lawyers.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.lawyers.common.config.RuoYiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 首页提示接口测试：返回系统名称与版本。
 *
 * @author ai-lawyers
 */
class SysIndexControllerTest
{
    @Test
    void 首页提示包含系统名称与版本()
    {
        RuoYiConfig ruoYiConfig = new RuoYiConfig();
        ruoYiConfig.setName("AI律师法律服务平台");
        ruoYiConfig.setVersion("1.0.0");
        SysIndexController controller = new SysIndexController();
        ReflectionTestUtils.setField(controller, "ruoyiConfig", ruoYiConfig);

        assertEquals("欢迎使用AI律师法律服务平台后台管理框架，当前版本：v1.0.0，请通过前端地址访问。",
                controller.index());
    }
}
