package ai.lawyers.system.service.lawyers.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * 队列名常量测试：Stream key 与死信 key 拼接规则。
 *
 * @author ai-lawyers
 */
class QueueNamesTest
{
    @Test
    void streamKey_统一添加前缀()
    {
        assertEquals("stream:call-event", QueueNames.streamKey(QueueNames.CALL_EVENT));
        assertEquals("stream:quality-transcribe", QueueNames.streamKey(QueueNames.QUALITY_TRANSCRIBE));
        assertEquals("stream:sms-send", QueueNames.streamKey(QueueNames.SMS_SEND));
        assertEquals("stream:status-log", QueueNames.streamKey(QueueNames.STATUS_LOG));
        assertEquals("stream:message-notify", QueueNames.streamKey(QueueNames.MESSAGE_NOTIFY));
    }

    @Test
    void deadLetterKey_追加死信后缀()
    {
        assertEquals("stream:call-event:dead", QueueNames.deadLetterKey(QueueNames.CALL_EVENT));
    }

    @Test
    void 前缀与后缀_常量取值稳定()
    {
        assertEquals("stream:", QueueNames.STREAM_KEY_PREFIX);
        assertEquals(":dead", QueueNames.DEAD_LETTER_SUFFIX);
    }

    @Test
    void 队列名_互不重复()
    {
        assertNotEquals(QueueNames.CALL_EVENT, QueueNames.QUALITY_TRANSCRIBE);
        assertNotEquals(QueueNames.SMS_SEND, QueueNames.STATUS_LOG);
        assertNotEquals(QueueNames.STATUS_LOG, QueueNames.MESSAGE_NOTIFY);
    }
}
