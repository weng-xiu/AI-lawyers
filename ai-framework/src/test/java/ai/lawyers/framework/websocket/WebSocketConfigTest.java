package ai.lawyers.framework.websocket;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置测试：注册 ServerEndpoint 导出器。
 *
 * @author ai-lawyers
 */
class WebSocketConfigTest
{
    @Test
    void serverEndpointExporter_返回导出器()
    {
        ServerEndpointExporter exporter = new WebSocketConfig().serverEndpointExporter();

        assertNotNull(exporter);
        assertInstanceOf(ServerEndpointExporter.class, exporter);
    }
}
