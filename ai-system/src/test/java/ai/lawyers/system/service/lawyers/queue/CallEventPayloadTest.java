package ai.lawyers.system.service.lawyers.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.service.lawyers.trunk.event.CallEvent;

/**
 * 呼叫事件队列消息体测试：按事件类型携带对应负载。
 *
 * @author ai-lawyers
 */
class CallEventPayloadTest
{
    @Test
    void 初始状态_各负载为空()
    {
        CallEventPayload payload = new CallEventPayload();

        assertNull(payload.getEventType());
        assertNull(payload.getCallRecord());
        assertNull(payload.getDialLog());
        assertNull(payload.getCallEvent());
    }

    @Test
    void setCallRecord_话单更新事件负载透传()
    {
        CallEventPayload payload = new CallEventPayload();
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1001L);

        payload.setEventType("UPDATE_CALL_RECORD");
        payload.setCallRecord(record);

        assertEquals("UPDATE_CALL_RECORD", payload.getEventType());
        assertSame(record, payload.getCallRecord());
        assertEquals(Long.valueOf(1001L), payload.getCallRecord().getRecordId());
        assertNull(payload.getDialLog());
    }

    @Test
    void setDialLog_拨号日志更新事件负载透传()
    {
        CallEventPayload payload = new CallEventPayload();
        AiCallDialLog dialLog = new AiCallDialLog();
        dialLog.setLogId(2002L);

        payload.setEventType("UPDATE_DIAL_LOG");
        payload.setDialLog(dialLog);

        assertEquals("UPDATE_DIAL_LOG", payload.getEventType());
        assertSame(dialLog, payload.getDialLog());
        assertEquals(Long.valueOf(2002L), payload.getDialLog().getLogId());
    }

    @Test
    void setCallEvent_PBX统一事件负载透传()
    {
        CallEventPayload payload = new CallEventPayload();
        CallEvent event = new CallEvent();

        payload.setEventType("PBX_EVENT");
        payload.setCallEvent(event);

        assertEquals("PBX_EVENT", payload.getEventType());
        assertSame(event, payload.getCallEvent());
    }
}
