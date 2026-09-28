package ai.lawyers.system.service.lawyers.trunk.gateway.ami;

/**
 * B1：AMI 事件监听器
 *
 * @author ai-lawyers
 */
public interface AmiEventListener
{
    /**
     * 收到 AMI 事件回调。
     *
     * @param host  Asterisk 主机
     * @param event 已解析的事件
     */
    void onEvent(String host, AmiEvent event);

    /**
     * 连接断开回调（重连由客户端内部负责，本回调用于告警/计数）。
     */
    default void onDisconnect(String host) {}
}
