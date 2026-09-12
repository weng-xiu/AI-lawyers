package ai.lawyers.framework.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;

/**
 * WebSocket 跨实例转发装配（N4）。
 *
 * <p>仅当 {@code websocket.cluster.enabled=true}（多实例部署显式开启）时装配：
 * 注册 Redis Pub/Sub 监听容器，把 {@link WsClusterRelay} 订阅到统一 fan-out 频道。
 * 单机默认不开启，行为与旧版完全一致。</p>
 *
 * @author ai-lawyers
 */
@Configuration
@ConditionalOnProperty(prefix = "websocket.cluster", name = "enabled", havingValue = "true")
public class WsClusterConfig
{
    /**
     * WebSocket 集群消息监听容器（独立订阅连接，随容器生命周期启停）。
     */
    @Bean
    public RedisMessageListenerContainer wsClusterListenerContainer(RedisConnectionFactory connectionFactory,
            WsClusterRelay wsClusterRelay)
    {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(wsClusterRelay, new ChannelTopic(WsClusterRelay.CHANNEL));
        return container;
    }
}
