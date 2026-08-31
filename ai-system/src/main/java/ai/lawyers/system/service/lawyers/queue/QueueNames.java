package ai.lawyers.system.service.lawyers.queue;

/**
 * T2-3 Redis Stream 轻量队列：队列/消费组名常量。
 *
 * <p>队列 key 统一以 {@code stream:} 为前缀；每个队列对应一个同名消费组，
 * 多实例部署时同组内竞争消费（一条消息仅被一个实例处理）。</p>
 *
 * @author ai-lawyers
 */
public final class QueueNames
{
    private QueueNames() {}

    /** 话单/呼叫状态事件异步入库 */
    public static final String CALL_EVENT = "call-event";

    /** 挂断后 ASR 转写 + 智能质检（T4-1） */
    public static final String QUALITY_TRANSCRIBE = "quality-transcribe";

    /** 短信异步发送削峰 */
    public static final String SMS_SEND = "sms-send";

    /** 坐席状态流水批量落库（T4-3） */
    public static final String STATUS_LOG = "status-log";

    /** 消息中心站内信异步落库 + 实时推送（T5-3） */
    public static final String MESSAGE_NOTIFY = "message-notify";

    /** Stream key 前缀 */
    public static final String STREAM_KEY_PREFIX = "stream:";

    /** 死信 Stream 后缀（重试超限的消息转入，配合告警人工排查） */
    public static final String DEAD_LETTER_SUFFIX = ":dead";

    /** 拼出 Stream 完整 key */
    public static String streamKey(String queue)
    {
        return STREAM_KEY_PREFIX + queue;
    }

    /** 拼出死信 Stream 完整 key */
    public static String deadLetterKey(String queue)
    {
        return STREAM_KEY_PREFIX + queue + DEAD_LETTER_SUFFIX;
    }
}
