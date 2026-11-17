package ai.lawyers.common.utils.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 回调签名工具测试：HMAC-SHA256 生成稳定性、时间戳窗口、空参数与错误签名拒绝。
 *
 * @author ai-lawyers
 */
class CallbackSignUtilsTest
{
    private static final String SECRET = "test-secret";

    private static final String CONTENT = "call-001.answered";

    @Test
    void sign_相同输入生成相同签名且随内容变化()
    {
        String timestamp = "1700000000000";
        String signature = CallbackSignUtils.sign(SECRET, CONTENT, timestamp);

        assertEquals(signature, CallbackSignUtils.sign(SECRET, CONTENT, timestamp));
        assertNotEquals(signature, CallbackSignUtils.sign(SECRET, "call-002.answered", timestamp));
        assertNotEquals(signature, CallbackSignUtils.sign(SECRET, CONTENT, "1700000000001"));
        assertFalse(signature.isEmpty());
    }

    @Test
    void verify_窗口内正确签名通过()
    {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = CallbackSignUtils.sign(SECRET, CONTENT, timestamp);

        assertTrue(CallbackSignUtils.verify(SECRET, CONTENT, timestamp, signature));
    }

    @Test
    void verify_签名或密钥错误拒绝()
    {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = CallbackSignUtils.sign(SECRET, CONTENT, timestamp);

        assertFalse(CallbackSignUtils.verify("other-secret", CONTENT, timestamp, signature));
        assertFalse(CallbackSignUtils.verify(SECRET, "call-002.answered", timestamp, signature));
        // 等长但内容不同的签名
        assertFalse(CallbackSignUtils.verify(SECRET, CONTENT, timestamp, tamper(signature)));
        // 长度不同的签名
        assertFalse(CallbackSignUtils.verify(SECRET, CONTENT, timestamp, signature + "x"));
    }

    @Test
    void verify_时间戳超出窗口拒绝()
    {
        long expired = System.currentTimeMillis() - 6 * 60 * 1000L;
        String timestamp = String.valueOf(expired);
        String signature = CallbackSignUtils.sign(SECRET, CONTENT, timestamp);

        assertFalse(CallbackSignUtils.verify(SECRET, CONTENT, timestamp, signature));
    }

    @Test
    void verify_时间戳格式非法拒绝()
    {
        assertFalse(CallbackSignUtils.verify(SECRET, CONTENT, "not-a-number",
                CallbackSignUtils.sign(SECRET, CONTENT, "not-a-number")));
    }

    @Test
    void verify_空参数直接拒绝()
    {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = CallbackSignUtils.sign(SECRET, CONTENT, timestamp);

        assertFalse(CallbackSignUtils.verify(null, CONTENT, timestamp, signature));
        assertFalse(CallbackSignUtils.verify(SECRET, CONTENT, null, signature));
        assertFalse(CallbackSignUtils.verify(SECRET, CONTENT, timestamp, null));
        assertFalse(CallbackSignUtils.verify(SECRET, CONTENT, "", signature));
    }

    /** 生成等长但内容被篡改的签名 */
    private String tamper(String signature)
    {
        char first = signature.charAt(0);
        char replaced = first == 'A' ? 'B' : 'A';
        return replaced + signature.substring(1);
    }
}
