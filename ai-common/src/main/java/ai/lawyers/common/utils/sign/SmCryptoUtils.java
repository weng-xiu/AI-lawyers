package ai.lawyers.common.utils.sign;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.math.ec.ECPoint;

/**
 * P3-G1 国密算法工具（V2.54）：SM4-GCM 对称加解密 / SM3 摘要 / SM2 签名验签与加解密。
 *
 * <p>实现基于 BouncyCastle 轻量级 API（bcprov-jdk15on 1.70，单一 jar 无传递依赖），
 * <b>不注册 JVM Security Provider</b>——避免污染全局 Cipher 选择、规避 JDK 8 下
 * Provider 注册顺序与 JCE 策略问题；等保/信创测评认可的标准实现。</p>
 *
 * <p>算法与格式：</p>
 * <ul>
 *   <li>SM4：GB/T 32907-2016。对称加密采用 <b>GCM</b>（AEAD 认证加密，tag 128 位，
 *       随机 12 字节 IV 随密文一同存储）；另暴露单块 ECB 入口仅供 GB/T 标准测试向量
 *       自检（分组密码本身不建议 ECB 模式承载业务数据）；</li>
 *   <li>SM3：GB/T 32905-2016 摘要，256 位，输出小写 hex；可用于盲索引/完整性校验；</li>
 *   <li>SM2：GB/T 32918-2016，曲线 sm2p256v1。签名 SM3withSM2（含 Z 值，默认用户 ID
 *       {@code 1234567812345678}），加密 C1C3C2 模式；密钥以 Base64 序列化——
 *       私钥 = Base64(BigInteger d)，公钥 = Base64(未压缩点 65 字节)。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public final class SmCryptoUtils
{
    /** SM2 标准曲线（GB/T 32918） */
    private static final X9ECParameters SM2_X9 = GMNamedCurves.getByName("sm2p256v1");

    private static final ECDomainParameters SM2_DOMAIN =
            new ECDomainParameters(SM2_X9.getCurve(), SM2_X9.getG(), SM2_X9.getN());

    /** SM2 默认用户 ID（GB/T 32918 附录 A） */
    private static final byte[] SM2_DEFAULT_USER_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    /** SM4-GCM：随机 IV 长度（NIST 建议值） */
    private static final int SM4_GCM_IV_LENGTH = 12;

    /** SM4-GCM：认证标签位数 */
    private static final int SM4_GCM_TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SmCryptoUtils()
    {
    }

    // ------------------------------------------------------------ SM4

    /**
     * SM4-GCM 加密（随机 IV）：返回 {@code Base64(IV(12) + 密文 + tag)}，对同一明文
     * 每次调用产生不同密文。
     */
    public static String sm4GcmEncryptToBase64(String plain, byte[] key)
    {
        try
        {
            byte[] iv = new byte[SM4_GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            byte[] cipher = sm4Gcm(plain.getBytes(StandardCharsets.UTF_8), key, iv, true);
            byte[] combined = new byte[iv.length + cipher.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipher, 0, combined, iv.length, cipher.length);
            return Base64.getEncoder().encodeToString(combined);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-GCM 加密失败", e);
        }
    }

    /** SM4-GCM 解密：入参为 {@link #sm4GcmEncryptToBase64} 的输出格式。 */
    public static String sm4GcmDecryptFromBase64(String stored, byte[] key)
    {
        try
        {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[SM4_GCM_IV_LENGTH];
            byte[] cipher = new byte[combined.length - SM4_GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, SM4_GCM_IV_LENGTH);
            System.arraycopy(combined, SM4_GCM_IV_LENGTH, cipher, 0, cipher.length);
            return new String(sm4Gcm(cipher, key, iv, false), StandardCharsets.UTF_8);
        }
        catch (IllegalStateException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4-GCM 解密失败（密文被篡改或密钥不匹配）", e);
        }
    }

    /**
     * SM4-GCM 核心入口：明文/密文与 IV 显式分离，供上层自行组装存储格式。
     * GCM 校验失败抛异常（认证加密防篡改）。
     */
    public static byte[] sm4Gcm(byte[] data, byte[] key, byte[] iv, boolean forEncrypt)
    {
        try
        {
            GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
            cipher.init(forEncrypt, new AEADParameters(new KeyParameter(key), SM4_GCM_TAG_BITS, iv));
            byte[] out = new byte[cipher.getOutputSize(data.length)];
            int len = cipher.processBytes(data, 0, data.length, out, 0);
            len += cipher.doFinal(out, len);
            byte[] result = new byte[len];
            System.arraycopy(out, 0, result, 0, len);
            return result;
        }
        catch (Exception e)
        {
            throw new IllegalStateException(forEncrypt ? "SM4-GCM 加密失败" : "SM4-GCM 解密失败", e);
        }
    }

    /**
     * SM4 单块（16 字节）ECB 处理，仅供 GB/T 32907-2016 标准测试向量自检与
     * 底层一致性验证；<b>业务数据不得使用 ECB 模式</b>（用 {@link #sm4Gcm}）。
     */
    public static byte[] sm4EcbBlock(byte[] block16, byte[] key16, boolean forEncrypt)
    {
        try
        {
            SM4Engine engine = new SM4Engine();
            engine.init(forEncrypt, new KeyParameter(key16));
            byte[] out = new byte[16];
            engine.processBlock(block16, 0, out, 0);
            return out;
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM4 单块处理失败", e);
        }
    }

    // ------------------------------------------------------------ SM3

    /** SM3 摘要（hex 小写输出），null/空串也产生合法摘要。 */
    public static String sm3Hex(String text)
    {
        byte[] data = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        return bytesToHex(sm3(data));
    }

    /** SM3 摘要（256 位 = 32 字节）。 */
    public static byte[] sm3(byte[] data)
    {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return out;
    }

    // ------------------------------------------------------------ SM2

    /**
     * 生成 SM2 密钥对。返回长度 2 数组：[0]=私钥 Base64（BigInteger d），
     * [1]=公钥 Base64（未压缩点 65 字节）。
     */
    public static String[] generateSm2KeyPairBase64()
    {
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(SM2_DOMAIN, RANDOM));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        ECPrivateKeyParameters priv = (ECPrivateKeyParameters) pair.getPrivate();
        ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
        return new String[] {
                Base64.getEncoder().encodeToString(priv.getD().toByteArray()),
                Base64.getEncoder().encodeToString(pub.getQ().getEncoded(false))
        };
    }

    /** SM2 签名（SM3withSM2，含 Z 值，默认用户 ID），返回 Base64 签名值。 */
    public static String sm2Sign(String privateKeyBase64, byte[] data)
    {
        try
        {
            ECPrivateKeyParameters priv = new ECPrivateKeyParameters(
                    new java.math.BigInteger(Base64.getDecoder().decode(privateKeyBase64)), SM2_DOMAIN);
            SM2Signer signer = new SM2Signer();
            signer.init(true, new ParametersWithID(
                    new ParametersWithRandom(priv, RANDOM), SM2_DEFAULT_USER_ID));
            signer.update(data, 0, data.length);
            return Base64.getEncoder().encodeToString(signer.generateSignature());
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM2 签名失败", e);
        }
    }

    /** SM2 验签（与 {@link #sm2Sign} 同一用户 ID）；签名/数据被篡改返回 false。 */
    public static boolean sm2Verify(String publicKeyBase64, byte[] data, String signBase64)
    {
        try
        {
            ECPublicKeyParameters pub = decodeSm2PublicKey(publicKeyBase64);
            SM2Signer signer = new SM2Signer();
            signer.init(false, new ParametersWithID(pub, SM2_DEFAULT_USER_ID));
            signer.update(data, 0, data.length);
            return signer.verifySignature(Base64.getDecoder().decode(signBase64));
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /** SM2 加密（C1C3C2 模式）：返回 {@code Base64(密文)}。 */
    public static String sm2EncryptToBase64(String publicKeyBase64, String plain)
    {
        try
        {
            ECPublicKeyParameters pub = decodeSm2PublicKey(publicKeyBase64);
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(true, new ParametersWithRandom(pub, RANDOM));
            byte[] src = plain.getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(engine.processBlock(src, 0, src.length));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM2 加密失败", e);
        }
    }

    /** SM2 解密（C1C3C2 模式）：入参为 {@link #sm2EncryptToBase64} 的输出。 */
    public static String sm2DecryptFromBase64(String privateKeyBase64, String stored)
    {
        try
        {
            ECPrivateKeyParameters priv = new ECPrivateKeyParameters(
                    new java.math.BigInteger(Base64.getDecoder().decode(privateKeyBase64)), SM2_DOMAIN);
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(false, priv);
            byte[] src = Base64.getDecoder().decode(stored);
            return new String(engine.processBlock(src, 0, src.length), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SM2 解密失败（密文被篡改或私钥不匹配）", e);
        }
    }

    private static ECPublicKeyParameters decodeSm2PublicKey(String publicKeyBase64)
    {
        byte[] encoded = Base64.getDecoder().decode(publicKeyBase64);
        ECPoint point = SM2_X9.getCurve().decodePoint(encoded);
        return new ECPublicKeyParameters(point, SM2_DOMAIN);
    }

    private static String bytesToHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
        {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
