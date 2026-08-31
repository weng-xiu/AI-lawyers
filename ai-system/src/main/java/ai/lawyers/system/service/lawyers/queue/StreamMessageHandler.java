package ai.lawyers.system.service.lawyers.queue;

/**
 * T2-3 Stream 队列消息处理器。
 *
 * <p>实现类按队列注册到 {@link StreamQueueService}，消费线程拉取消息后回调本方法；
 * 抛出异常表示处理失败（消息不 ACK，将重投，超限进死信），正常返回视为处理成功并 ACK。</p>
 *
 * <p><b>幂等要求</b>：队列保证 at-least-once，重投/多实例恢复时同一消息可能被处理多次，
 * 实现必须按业务唯一键（话单ID/消息ID/状态流水ID等）幂等。</p>
 *
 * @author ai-lawyers
 */
public interface StreamMessageHandler
{
    /**
     * 处理一条消息。
     *
     * @param payloadJson XADD 时写入的消息体（JSON 字符串）
     */
    void onMessage(String payloadJson) throws Exception;
}
