package ai.lawyers.common.utils.sign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-G1（V2.54）：SecretCryptoUtils 国密化兼容测试——新写入 enc2:（SM4-GCM）、
 * 历史 enc:（AES/CBC）与存量明文三态解密兼容、防重复加密与回显占位行为。
 */
class SecretCryptoUtilsTest
{
    /** 与实现一致的密钥来源选择（env → system property → 开发默认） */
    private static String keySource()
    {
        String key = System.getenv("APP_SECRET_KEY");
        if (key == null || key.isEmpty())
        {
            key = System.getProperty("app.secret.key");
        }
        if (key == null || key.isEmpty())
        {
            key = "ai-lawyers-dev-secret-change-me-in-production-2026";
        }
        return key;
    }

    /** 按历史 AES/CBC 格式构造存量密文（enc: + Base64(IV16 + 密文)），密钥=SHA-256(来源) */
    private static String legacyAesEncrypt(String plain) throws Exception
    {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(keySource().getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return "enc:" + Base64.getEncoder().encodeToString(combined);
    }

    @Test
    void encrypt_outputsSm4PrefixAndDecryptRoundtrip()
    {
        String secret = "sk-test-apikey-2026-中文";
        String stored = SecretCryptoUtils.encrypt(secret);
        assertTrue(stored.startsWith("enc3:"), "新写入应为版本化国密 enc3: 前缀，实际: " + stored.substring(0, 8));
        assertNotEquals(secret, stored);
        assertEquals(secret, SecretCryptoUtils.decrypt(stored), "SM4-GCM 解密应还原明文");

        // 同一明文两次加密密文不同（随机 IV）
        assertNotEquals(stored, SecretCryptoUtils.encrypt(secret), "随机 IV 下两次密文应不同");
    }

    @Test
    void decrypt_legacyAesCipher_stillReadable() throws Exception
    {
        // 旧 AES 路径为 256 位密钥：JDK 8u161 前未装 JCE 无限强度策略的环境（AES-256 受限）
        // 无法构造/解析存量密文，属环境限制而非实现缺陷，按能力跳过
        org.junit.jupiter.api.Assumptions.assumeTrue(
                javax.crypto.Cipher.getMaxAllowedKeyLength("AES") >= 256, "JDK JCE 策略不支持 AES-256，跳过存量兼容验证");
        // G1 兼容承诺：存量 enc:（AES/CBC）密文无需迁移即可继续读取
        String secret = "legacy-aes-secret-2026";
        String legacy = legacyAesEncrypt(secret);
        assertTrue(legacy.startsWith("enc:"));
        assertEquals(secret, SecretCryptoUtils.decrypt(legacy), "历史 AES 密文应继续可解");
    }

    @Test
    void decrypt_plainTextStoredValue_returnedAsIs()
    {
        // 存量明文（无前缀）原样返回，平滑兼容
        assertEquals("plain-api-key", SecretCryptoUtils.decrypt("plain-api-key"));
        assertEquals("", SecretCryptoUtils.decrypt(""));
        assertNull(SecretCryptoUtils.decrypt(null));
    }

    @Test
    void encrypt_idempotentAndPlaceholderAware() throws Exception
    {
        String sm4 = SecretCryptoUtils.encrypt("v");
        // 已加密值（enc2:）不重复加密
        assertEquals(sm4, SecretCryptoUtils.encrypt(sm4), "enc2: 值不应被二次加密");
        // 回显占位符原样返回
        assertEquals("******", SecretCryptoUtils.encrypt("******"));
        assertTrue(SecretCryptoUtils.isMaskPlaceholder("******"));
        assertFalse(SecretCryptoUtils.isMaskPlaceholder("real-key"));
        // mask 行为
        assertEquals("******", SecretCryptoUtils.mask("anything"));
        assertNull(SecretCryptoUtils.mask(""));
        assertNull(SecretCryptoUtils.mask(null));
    }
}
