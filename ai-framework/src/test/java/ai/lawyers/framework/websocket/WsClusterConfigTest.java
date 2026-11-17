package ai.lawyers.framework.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * WebSocket 集群配置测试：监听容器绑定连接工厂与转发频道。
 *
 * @author ai-lawyers
 */
class WsClusterConfigTest
{
    @Test
    void wsClusterListenerContainer_绑定连接工厂与频道()
    {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        WsClusterRelay relay = mock(WsClusterRelay.class);

        RedisMessageListenerContainer container = new WsClusterConfig()
                .wsClusterListenerContainer(connectionFactory, relay);

        assertNotNull(container);
        assertEquals(connectionFactory, container.getConnectionFactory());
        assertFalse(container.isRunning());
    }
}
