package ai.lawyers.common.utils.sign;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-G1（V2.54）：国密算法工具单元测试——GB/T 标准测试向量 + 加解密/签验/防篡改行为。
 */
class SmCryptoUtilsTest
{
    private static final byte[] SM4_KEY = hexToBytes("0123456789ABCDEFFEDCBA9876543210");

    private static byte[] hexToBytes(String hex)
    {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++)
        {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    @Test
    void sm4_standardVector_gbt32907()
    {
        // GB/T 32907-2016 附录 A：key=明文=0123456789ABCDEFFEDCBA9876543210 → 681EDF34D206965E86B3E94F536E4246
        byte[] plain = hexToBytes("0123456789ABCDEFFEDCBA9876543210");
        byte[] cipher = SmCryptoUtils.sm4EcbBlock(plain, SM4_KEY, true);
        assertArrayEquals(hexToBytes("681EDF34D206965E86B3E94F536E4246"), cipher, "SM4 标准测试向量应吻合");
        // 解密回原明文
        assertArrayEquals(plain, SmCryptoUtils.sm4EcbBlock(cipher, SM4_KEY, false), "SM4 解密应还原明文");
    }

    @Test
    void sm3_standardVectors_gbt32905()
    {
        // GB/T 32905-2016 标准向量："abc" 与空串
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",
                SmCryptoUtils.sm3Hex("abc"), "SM3(\"abc\") 标准向量应吻合");
        assertEquals("1ab21d8355cfa17f8e61194831e81a8f22bec8c728fefb747ed035eb5082aa2b",
                SmCryptoUtils.sm3Hex(""), "SM3(\"\") 标准向量应吻合");
        assertEquals(64, SmCryptoUtils.sm3Hex("abc").length(), "SM3 摘要应为 256 位 hex");
    }

    @Test
    void sm4Gcm_roundtrip_randomIvAndTamperDetection()
    {
        byte[] key = new byte[16];
        new java.security.SecureRandom().nextBytes(key);
        String plain = "apiKey-测试-1234567890-ABCDEF";

        String stored1 = SmCryptoUtils.sm4GcmEncryptToBase64(plain, key);
        String stored2 = SmCryptoUtils.sm4GcmEncryptToBase64(plain, key);
        assertEquals(plain, SmCryptoUtils.sm4GcmDecryptFromBase64(stored1, key), "SM4-GCM 应解密还原");
        assertNotEquals(stored1, stored2, "随机 IV 下同一明文两次密文应不同");

        // 篡改密文（Base64 解码后翻转 1 字节再编码）→ GCM 认证失败抛异常
        byte[] raw = Base64.getDecoder().decode(stored1);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class,
                () -> SmCryptoUtils.sm4GcmDecryptFromBase64(tampered, key), "GCM 应检测到密文篡改");

        // 密钥不匹配同样失败
        byte[] otherKey = new byte[16];
        assertThrows(IllegalStateException.class,
                () -> SmCryptoUtils.sm4GcmDecryptFromBase64(stored1, otherKey), "密钥不匹配应失败");
    }

    @Test
    void sm2_signVerifyAndEncryptDecrypt()
    {
        String[] pair = SmCryptoUtils.generateSm2KeyPairBase64();
        String privB64 = pair[0];
        String pubB64 = pair[1];
        assertFalse(privB64.isEmpty());
        assertEquals(88, pubB64.length(), "未压缩公钥点应为 65 字节（Base64 88 字符）");

        byte[] data = "sign-me-国密签名验证".getBytes(StandardCharsets.UTF_8);
        String sig = SmCryptoUtils.sm2Sign(privB64, data);
        assertTrue(SmCryptoUtils.sm2Verify(pubB64, data, sig), "SM2 签名应验签通过");

        // 数据/签名篡改 → 验签失败
        byte[] altered = "sign-me-国密签名验证~".getBytes(StandardCharsets.UTF_8);
        assertFalse(SmCryptoUtils.sm2Verify(pubB64, altered, sig), "数据被篡改应验签失败");
        assertFalse(SmCryptoUtils.sm2Verify(pubB64, data, sig.substring(0, sig.length() - 4) + "AAAA"),
                "签名被篡改应验签失败");

        // 加解密 roundtrip（C1C3C2）
        String secret = "sm2-secret-中文-2026";
        String enc = SmCryptoUtils.sm2EncryptToBase64(pubB64, secret);
        assertEquals(secret, SmCryptoUtils.sm2DecryptFromBase64(privB64, enc), "SM2 加解密应还原");
        assertNotEquals(secret, enc, "密文不应等于明文");
    }
}
