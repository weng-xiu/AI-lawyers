package ai.lawyers.system.service.lawyers.queue;

import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;

/**
 * W1: call-event 队列消息体。
 *
 * <p>根据 eventType 决定携带 callRecord 或 dialLog，消费端按类型分发。</p>
 *
 * @author ai-lawyers
 */
public class CallEventPayload
{
    /** 事件类型：UPDATE_CALL_RECORD / UPDATE_DIAL_LOG */
    private String eventType;

    /** UPDATE_CALL_RECORD 时携带的话单更新对象（仅含需更新字段 + recordId） */
    private AiCallRecord callRecord;

    /** UPDATE_DIAL_LOG 时携带的拨号日志更新对象（仅含需更新字段 + logId） */
    private AiCallDialLog dialLog;

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    public AiCallRecord getCallRecord()
    {
        return callRecord;
    }

    public void setCallRecord(AiCallRecord callRecord)
    {
        this.callRecord = callRecord;
    }

    public AiCallDialLog getDialLog()
    {
        return dialLog;
    }

    public void setDialLog(AiCallDialLog dialLog)
    {
        this.dialLog = dialLog;
    }
}
