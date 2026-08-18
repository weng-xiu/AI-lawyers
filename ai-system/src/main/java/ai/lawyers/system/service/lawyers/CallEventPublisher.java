package ai.lawyers.system.service.lawyers;

/**
 * 呼叫实时事件发布者
 *
 * <p>系统模块只依赖此接口；框架模块提供基于 WebSocket 的实现，
 * 把事件实时推送给坐席工作台，替代 5 秒轮询。</p>
 *
 * @author ai-lawyers
 */
public interface CallEventPublisher
{
    /**
     * 向指定用户推送一条事件。
     *
     * @param userId 接收者用户ID（坐席绑定的 userId）
     * @param type   事件类型，如 AGENT_STATUS / INBOUND_RING / CALL_ANSWERED / CALL_HANGUP
     * @param data   业务数据（将被序列化为 JSON）
     */
    void publishToUser(Long userId, String type, Object data);

    /**
     * 向所有在线坐席广播一条事件。
     */
    void broadcast(String type, Object data);
}
