package ai.lawyers.common.utils.sign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import ai.lawyers.common.utils.StringUtils;

/**
 * 敏感配置凭据（API Key / Access Secret 等）落库加密工具（S6 安全收口）。
 *
 * <p>算法（P3-G1 V2.54 国密化）：<b>新写入一律 SM4-GCM（GB/T 32907-2016，国密）</b>，
 * 存储格式 {@code enc2: + Base64(IV(12) + 密文 + GCM tag)}；历史 {@code enc: + Base64(IV(16) + 密文)}
 * （AES/CBC/PKCS5Padding，随机 16 字节 IV）在解密侧保持兼容，存量密文无需迁移即可继续读取；
 * 未带任何前缀的值视为存量明文，解密时原样返回，保证平滑兼容。GCM 为认证加密，
 * 密文被篡改时解密抛异常（防静默改包）。</p>
 *
 * <p>密钥来源：环境变量 {@code APP_SECRET_KEY}（推荐 >=32 字节随机串）；
 * 未配置时使用内置开发默认值，<b>生产环境必须通过环境变量注入</b>。
 * 密钥派生：SHA-256 → 32 字节，AES 用全 32 字节，SM4 取前 16 字节（SM4 密钥固定 128 位）。</p>
 *
 * @author ai-lawyers
 */
public final class SecretCryptoUtils
{
    /** 国密密文前缀（V2.54 G1 起 encrypt 输出）：SM4-GCM */
    private static final String CIPHER_PREFIX_SM4 = "enc2:";

    /** 版本化国密密文前缀（V2.57 密钥轮换起）：Base64(version(1) + IV(12) + 密文 + tag) */
    private static final String CIPHER_PREFIX_SM4_V2 = "enc3:";

    /** 历史 AES 密文前缀：仅解密侧兼容，不再新写入 */
    private static final String CIPHER_PREFIX_AES = "enc:";

    private static final String AES_ALGORITHM = "AES";

    private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private static final int AES_IV_LENGTH = 16;

    private static final int SM4_IV_LENGTH = 12;

    private static final int SM4_KEY_LENGTH = 16;

    /** 当前根密钥版本号（密钥轮换时递增） */
    private static final byte KEY_VERSION = 1;

    /** 回显脱敏占位：已配置密钥但不返回明文 */
    private static final String MASK_PLACEHOLDER = "******";

    private SecretCryptoUtils()
    {
    }

    /** SHA-256 派生 32 字节根密钥（AES 全量使用；SM4 取前 16 字节） */
    private static byte[] derivedKey32()
    {
        return derivedKey32(KEY_VERSION);
    }

    /** 按版本派生根密钥：当前版本用 APP_SECRET_KEY，历史版本用 APP_PREVIOUS_SECRET_KEY */
    private static byte[] derivedKey32(byte version)
    {
        String key;
        if (version == KEY_VERSION)
        {
            key = System.getenv("APP_SECRET_KEY");
            if (StringUtils.isEmpty(key))
            {
                key = System.getProperty("app.secret.key");
            }
        }
        else
        {
            key = System.getenv("APP_PREVIOUS_SECRET_KEY");
            if (StringUtils.isEmpty(key))
            {
                key = System.getProperty("app.previous-secret-key");
            }
        }
        if (StringUtils.isEmpty(key))
        {
            if (version == KEY_VERSION)
            {
                // 仅开发兜底；生产必须注入 APP_SECRET_KEY
                key = "ai-lawyers-dev-secret-change-me-in-production-2026";
            }
            else
            {
                // 历史密钥未配置，回退当前根密钥（单密钥环境）
                return derivedKey32(KEY_VERSION);
            }
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

    /** AES/CBC 密钥（历史兼容，32 字节） */
    private static byte[] aesKey()
    {
        return derivedKey32();
    }

    /** SM4 密钥（G1 新写入路径，取派生根密钥前 16 字节） */
    private static byte[] sm4Key()
    {
        return sm4Key(KEY_VERSION);
    }

    /** SM4 密钥（按版本选根密钥） */
    private static byte[] sm4Key(byte version)
    {
        byte[] root = derivedKey32(version);
        byte[] key = new byte[SM4_KEY_LENGTH];
        System.arraycopy(root, 0, key, 0, SM4_KEY_LENGTH);
        return key;
    }

    /**
     * 加密明文（V2.57 起输出版本化国密 enc3: SM4-GCM）。null/空串原样返回；
     * 已是密文（enc3:/enc2:/enc: 前缀）原样返回避免重复加密；
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
            byte[] iv = new byte[SM4_IV_LENGTH];
            new java.security.SecureRandom().nextBytes(iv);
            byte[] cipher = SmCryptoUtils.sm4Gcm(plain.getBytes(StandardCharsets.UTF_8), sm4Key(), iv, true);
            byte[] combined = new byte[1 + iv.length + cipher.length];
            combined[0] = KEY_VERSION;
            System.arraycopy(iv, 0, combined, 1, iv.length);
            System.arraycopy(cipher, 0, combined, 1 + iv.length, cipher.length);
            return CIPHER_PREFIX_SM4_V2 + Base64.getEncoder().encodeToString(combined);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /**
     * 解密密文。多态兼容：
     * <ul>
     *   <li>enc3: 版本化国密 → 按 version 选根密钥 SM4-GCM 解密；</li>
     *   <li>enc2: 存量国密 → 先 current 后 previous；</li>
     *   <li>enc: 历史 AES/CBC；</li>
     *   <li>无前缀 → 存量明文原样返回。</li>
     * </ul>
     */
    public static String decrypt(String stored)
    {
        if (StringUtils.isEmpty(stored))
        {
            return stored;
        }
        if (stored.startsWith(CIPHER_PREFIX_SM4_V2))
        {
            try
            {
                byte[] combined = Base64.getDecoder().decode(stored.substring(CIPHER_PREFIX_SM4_V2.length()));
                byte version = combined[0];
                byte[] iv = new byte[SM4_IV_LENGTH];
                System.arraycopy(combined, 1, iv, 0, SM4_IV_LENGTH);
                byte[] cipher = new byte[combined.length - 1 - SM4_IV_LENGTH];
                System.arraycopy(combined, 1 + SM4_IV_LENGTH, cipher, 0, cipher.length);
                return new String(SmCryptoUtils.sm4Gcm(cipher, sm4Key(version), iv, false), StandardCharsets.UTF_8);
            }
            catch (Exception e)
            {
                throw new IllegalStateException("凭据解密失败（版本化国密）", e);
            }
        }
        if (stored.startsWith(CIPHER_PREFIX_SM4))
        {
            // 存量 enc2: 无 version，先 current 后 previous
            String body = stored.substring(CIPHER_PREFIX_SM4.length());
            try
            {
                return SmCryptoUtils.sm4GcmDecryptFromBase64(body, sm4Key());
            }
            catch (Exception curFail)
            {
                try
                {
                    return SmCryptoUtils.sm4GcmDecryptFromBase64(body, sm4Key((byte) 0));
                }
                catch (Exception e)
                {
                    throw new IllegalStateException("凭据解密失败（国密）", e);
                }
            }
        }
        if (stored.startsWith(CIPHER_PREFIX_AES))
        {
            return decryptAes(stored);
        }
        return stored;
    }

    /** 历史 AES/CBC 解密（enc: 前缀，仅兼容存量） */
    private static String decryptAes(String stored)
    {
        try
        {
            byte[] combined = Base64.getDecoder().decode(stored.substring(CIPHER_PREFIX_AES.length()));
            byte[] iv = new byte[AES_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - AES_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, AES_IV_LENGTH);
            System.arraycopy(combined, AES_IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey(), AES_ALGORITHM),
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
        return value != null
                && (value.startsWith(CIPHER_PREFIX_SM4_V2)
                || value.startsWith(CIPHER_PREFIX_SM4)
                || value.startsWith(CIPHER_PREFIX_AES));
    }
}
