package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

/**
 * ESL 事件监听器
 *
 * @author ai-lawyers
 */
public interface EslEventListener
{
    /**
     * 收到 ESL 事件回调。
     *
     * @param host  FreeSWITCH 主机
     * @param event 已解析的事件
     */
    void onEvent(String host, EslEvent event);

    /**
     * 连接断开回调。
     */
    default void onDisconnect(String host) {}
}
