package ai.lawyers.common.utils.sign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import ai.lawyers.common.utils.StringUtils;

/**
 * 敏感配置凭据（API Key / Access Secret 等）落库加密工具（S6 安全收口）。
 *
 * <p>算法：AES/CBC/PKCS5Padding，每条密文使用随机 16 字节 IV，
 * 存储格式为 {@code enc: + Base64(IV + 密文)}；未带前缀的值视为存量明文，
 * 解密时原样返回，保证平滑兼容。</p>
 *
 * <p>密钥来源：环境变量 {@code APP_SECRET_KEY}（推荐 >=32 字节随机串）；
 * 未配置时使用内置开发默认值，<b>生产环境必须通过环境变量注入</b>。</p>
 *
 * @author ai-lawyers
 */
public final class SecretCryptoUtils
{
    /** 密文前缀，用于区分加密值与历史明文 */
    private static final String CIPHER_PREFIX = "enc:";

    private static final String ALGORITHM = "AES";

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private static final int IV_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 回显脱敏占位：已配置密钥但不返回明文 */
    private static final String MASK_PLACEHOLDER = "******";

    private SecretCryptoUtils()
    {
    }

    private static byte[] secretKey()
    {
        String key = System.getenv("APP_SECRET_KEY");
        if (StringUtils.isEmpty(key))
        {
            key = System.getProperty("app.secret.key");
        }
        if (StringUtils.isEmpty(key))
        {
            // 仅开发兜底；生产必须注入 APP_SECRET_KEY
            key = "ai-lawyers-dev-secret-change-me-in-production-2026";
        }
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(key.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("初始化凭据加密密钥失败", e);
        }
    }

    /**
     * 加密明文。null/空串原样返回；已是密文（enc: 前缀）原样返回避免重复加密；
     * 前端回显占位符 ****** 原样返回（表示未修改）。
     */
    public static String encrypt(String plain)
    {
        if (StringUtils.isEmpty(plain) || isEncrypted(plain) || MASK_PLACEHOLDER.equals(plain))
        {
            return plain;
        }
        try
        {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(secretKey(), ALGORITHM),
                    new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(combined);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /**
     * 解密密文。null/空串原样返回；历史明文（无 enc: 前缀）原样返回。
     */
    public static String decrypt(String stored)
    {
        if (StringUtils.isEmpty(stored) || !isEncrypted(stored))
        {
            return stored;
        }
        try
        {
            byte[] combined = Base64.getDecoder().decode(stored.substring(CIPHER_PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(secretKey(), ALGORITHM),
                    new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("凭据解密失败", e);
        }
    }

    /**
     * 回显脱敏：已配置（非空）密钥仅返回 ******；未配置返回 null。
     */
    public static String mask(String stored)
    {
        return StringUtils.isEmpty(stored) ? null : MASK_PLACEHOLDER;
    }

    /**
     * 更新场景：前端未修改密钥时回传占位符 ******，此时应保留库中旧值；
     * 返回 true 表示入参为占位符，应忽略本次更新。
     */
    public static boolean isMaskPlaceholder(String value)
    {
        return MASK_PLACEHOLDER.equals(value);
    }

    private static boolean isEncrypted(String value)
    {
        return value != null && value.startsWith(CIPHER_PREFIX);
    }
}
