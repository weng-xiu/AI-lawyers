package ai.lawyers.system.service.lawyers.voice.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 写操作白名单枚举测试：编码解析与白名单外拒绝。
 *
 * @author ai-lawyers
 */
class WriteOpActionTest
{
    @Test
    void ofCode_按编码解析白名单动作()
    {
        assertEquals(WriteOpAction.SEND_SMS, WriteOpAction.ofCode("sendSms"));
        assertEquals(WriteOpAction.BOOK_CALLBACK, WriteOpAction.ofCode("bookCallback"));
    }

    @Test
    void ofCode_空编码返回空()
    {
        assertNull(WriteOpAction.ofCode(null));
    }

    @Test
    void ofCode_白名单外编码返回空()
    {
        assertNull(WriteOpAction.ofCode("transferMoney"));
        assertNull(WriteOpAction.ofCode(""));
        assertNull(WriteOpAction.ofCode("SEND_SMS"));
    }

    @Test
    void getCode与getDesc_返回配置值()
    {
        assertEquals("sendSms", WriteOpAction.SEND_SMS.getCode());
        assertEquals("代发短信", WriteOpAction.SEND_SMS.getDesc());
        assertEquals("bookCallback", WriteOpAction.BOOK_CALLBACK.getCode());
        assertEquals("预约回访", WriteOpAction.BOOK_CALLBACK.getDesc());
    }

    @Test
    void values_仅含两项低敏写操作()
    {
        assertEquals(2, WriteOpAction.values().length);
    }
}
