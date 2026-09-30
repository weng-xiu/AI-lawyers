package ai.lawyers.common.utils.sign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import ai.lawyers.common.utils.StringUtils;

/**
 * PII 字段级加密工具（P3-G1-b V2.55）：个人身份信息（手机号/身份证号等）落库加密与盲索引。
 *
 * <p>与 {@link SecretCryptoUtils}（配置凭据加密）同源不同域：根密钥同样来自
 * 环境变量 {@code APP_SECRET_KEY}（或系统属性 {@code app.secret.key}，开发默认兜底），
 * 经 SHA-256 派生根密钥后再按用途做域分离派生，两套密钥互不相关：</p>
 * <ul>
 *   <li><b>字段加密</b>：SM4-GCM（GB/T 32907-2016，随机 12 字节 IV + 128 位认证标签），
 *       存储格式 {@code encp: + Base64(IV(12) + 密文 + tag)}；同明文每次密文不同，
 *       密文被篡改时解密抛异常；</li>
 *   <li><b>盲索引</b>：HMAC-SM3（GB/T 32905-2016 为内核）截断 128 位、小写 hex 32 字符，
 *       对同明文恒定输出（支持 {@code where xxx_index = ?} 等值查询与唯一索引防重），
 *       密钥参与运算，明文空间小的字段（手机号）无法被彩虹表/枚举反推；
 *       <b>仅支持等值匹配，不支持模糊查询</b>（模糊检索类表列入 G1-b2 专项）。</li>
 * </ul>
 *
 * <p>三态兼容：无 {@code encp:} 前缀的存量明文在解密侧原样返回，配合迁移接口
 * （PiiCryptoController.migrateCallerProfile）实现灰度平滑；加密侧幂等
 * （已密文不再二次加密）。</p>
 *
 * @author ai-lawyers
 */
public final class PiiCryptoUtils
{
    /** PII 密文前缀：与凭据加密（enc2:）、历史 AES（enc:）互相区分 */
    private static final String CIPHER_PREFIX = "encp:";

    /** 密钥域分离标签：字段加密密钥 */
    private static final String SM4_KEY_DOMAIN = "pii-sm4-v1";

    /** 密钥域分离标签：盲索引 HMAC 密钥 */
    private static final String BLIND_KEY_DOMAIN = "pii-blind-v1";

    /** SM4 密钥长度（128 位） */
    private static final int SM4_KEY_LENGTH = 16;

    /** 盲索引截断长度：HMAC-SM3 256 位取前 128 位（hex 32 字符） */
    private static final int BLIND_INDEX_BYTES = 16;

    /** 开发兜底密钥（与 SecretCryptoUtils 一致；生产必须注入 APP_SECRET_KEY） */
    private static final String DEV_DEFAULT_KEY = "ai-lawyers-dev-secret-change-me-in-production-2026";

    private PiiCryptoUtils()
    {
    }

    // ------------------------------------------------------------ 加解密

    /**
     * 加密字段明文：{@code encp: + Base64(IV(12) + SM4-GCM 密文 + tag)}。
     * null/空串原样返回；已带 encp: 前缀幂等返回（防二次加密）。
     */
    public static String encrypt(String plain)
    {
        if (StringUtils.isEmpty(plain) || isEncrypted(plain))
        {
            return plain;
        }
        return CIPHER_PREFIX + SmCryptoUtils.sm4GcmEncryptToBase64(plain, piiSm4Key());
    }

    /**
     * 解密字段值。三态兼容：encp: → SM4-GCM 解密（篡改/密钥不匹配抛异常）；
     * 无前缀 → 存量明文原样返回（迁移窗口兼容）。
     */
    public static String decrypt(String stored)
    {
        if (StringUtils.isEmpty(stored))
        {
            return stored;
        }
        if (isEncrypted(stored))
        {
            return SmCryptoUtils.sm4GcmDecryptFromBase64(stored.substring(CIPHER_PREFIX.length()), piiSm4Key());
        }
        return stored;
    }

    /** 是否为本工具密文（encp: 前缀）。 */
    public static boolean isEncrypted(String stored)
    {
        return stored != null && stored.startsWith(CIPHER_PREFIX);
    }

    // ------------------------------------------------------------ 盲索引

    /**
     * 计算盲索引：HMAC-SM3(密钥, 明文) 截断 128 位，小写 hex 32 字符。
     * 同明文恒定输出；null/空串返回 null（不参与索引）。
     */
    public static String blindIndex(String plain)
    {
        if (StringUtils.isEmpty(plain))
        {
            return null;
        }
        byte[] in = plain.getBytes(StandardCharsets.UTF_8);
        HMac hMac = new HMac(new SM3Digest());
        hMac.init(new KeyParameter(piiBlindKey()));
        hMac.update(in, 0, in.length);
        byte[] mac = new byte[hMac.getMacSize()];
        hMac.doFinal(mac, 0);
        return toHex(mac, BLIND_INDEX_BYTES);
    }

    // ------------------------------------------------------------ 密钥派生

    /** 根密钥：SHA-256(密钥来源)，与 SecretCryptoUtils 同源（env → 属性 → 开发默认） */
    private static byte[] rootKey32()
    {
        String key = System.getenv("APP_SECRET_KEY");
        if (StringUtils.isEmpty(key))
        {
            key = System.getProperty("app.secret.key");
        }
        if (StringUtils.isEmpty(key))
        {
            key = DEV_DEFAULT_KEY;
        }
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(key.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("初始化 PII 加密根密钥失败", e);
        }
    }

    /** 域分离派生：SHA-256(根密钥 || 域标签)，各用途密钥互不相关 */
    private static byte[] domainKey(String domain)
    {
        byte[] root = rootKey32();
        byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
        byte[] material = new byte[root.length + domainBytes.length];
        System.arraycopy(root, 0, material, 0, root.length);
        System.arraycopy(domainBytes, 0, material, root.length, domainBytes.length);
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(material);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("PII 密钥域分离派生失败", e);
        }
    }

    /** 字段加密密钥：域派生 32 字节取前 16 字节（SM4 固定 128 位密钥） */
    private static byte[] piiSm4Key()
    {
        byte[] derived = domainKey(SM4_KEY_DOMAIN);
        byte[] key = new byte[SM4_KEY_LENGTH];
        System.arraycopy(derived, 0, key, 0, SM4_KEY_LENGTH);
        return key;
    }

    /** 盲索引 HMAC 密钥：域派生全 32 字节 */
    private static byte[] piiBlindKey()
    {
        return domainKey(BLIND_KEY_DOMAIN);
    }

    /** 字节数组前 length 位转小写 hex */
    private static String toHex(byte[] bytes, int length)
    {
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++)
        {
            String hex = Integer.toHexString(bytes[i] & 0xff);
            if (hex.length() < 2)
            {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
