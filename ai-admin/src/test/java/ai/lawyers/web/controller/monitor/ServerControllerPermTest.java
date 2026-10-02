package ai.lawyers.web.controller.monitor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link ServerController} 权限校验——服务器监控端点读取真实硬件/系统信息，
 * 这里仅验证未登录 401。
 */
public class ServerControllerPermTest extends AbstractPermMvcTest
{
    @BeforeEach
    void setUp()
    {
        startMvc(ServerController.class);
    }

    @Test
    void serverInfo_unauthenticated_401() throws Exception
    {
        mockMvc.perform(get("/monitor/server")).andExpect(status().isUnauthorized());
    }
}
