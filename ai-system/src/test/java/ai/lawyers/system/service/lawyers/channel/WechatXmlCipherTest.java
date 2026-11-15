package ai.lawyers.system.service.lawyers.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * P3-#16：微信/政务微信回调报文加解密工具测试。
 *
 * <p>覆盖回调验签的参数顺序无关与空值容忍、AES-256-CBC 加解密往返（含中文）、
 * 随机 IV 导致的密文差异、密钥非法、密文结构异常（过短/长度字段非法/非 Base64）
 * 以及 XML 字段提取的 CDATA/裸值/缺失/跨行场景。</p>
 *
 * @author ai-lawyers
 */
class WechatXmlCipherTest
{
    /** 43 字符 EncodingAESKey（32 字节全零密钥去掉尾部 '=' 得到） */
    private static final String AES_KEY = Base64.getEncoder()
            .encodeToString(new byte[32]).substring(0, 43);

    private static final String RECEIVE_ID = "ww1234567890abcdef";

    // ---------- 回调验签 ----------

    @Test
    void signatureIsIndependentOfArgumentOrder()
    {
        String a = WechatXmlCipher.signature("token", "1700000000", "nonce", "cipher");
        String b = WechatXmlCipher.signature("cipher", "nonce", "1700000000", "token");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(40).matches("[0-9a-f]{40}");
    }

    @Test
    void signatureDiffersWhenTokenDiffers()
    {
        String a = WechatXmlCipher.signature("token-a", "1", "n", "c");
        String b = WechatXmlCipher.signature("token-b", "1", "n", "c");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void signatureTreatsNullAsEmptyString()
    {
        assertThat(WechatXmlCipher.signature(null, "1", null, "c"))
                .isEqualTo(WechatXmlCipher.signature("", "1", "", "c"));
    }

    // ---------- 加解密 ----------

    @Test
    void encryptDecryptRoundTripKeepsChineseText()
    {
        String plaintext = "<xml><Content><![CDATA[您好，请问有什么可以帮您？]]></Content></xml>";

        String encrypted = WechatXmlCipher.encrypt(plaintext, RECEIVE_ID, AES_KEY);

        assertThat(encrypted).isNotBlank();
        assertThat(WechatXmlCipher.decrypt(encrypted, AES_KEY)).isEqualTo(plaintext);
    }

    @Test
    void encryptUsesRandomIvSoCiphertextDiffers()
    {
        String first = WechatXmlCipher.encrypt("<xml/>", RECEIVE_ID, AES_KEY);
        String second = WechatXmlCipher.encrypt("<xml/>", RECEIVE_ID, AES_KEY);

        assertThat(first).isNotEqualTo(second);
        assertThat(WechatXmlCipher.decrypt(second, AES_KEY)).isEqualTo("<xml/>");
    }

    @Test
    void decryptAcceptsFullLength44CharKey()
    {
        // 带尾部 '=' 的 44 字符标准 Base64 同样可用
        String fullKey = Base64.getEncoder().encodeToString(new byte[32]);
        String encrypted = WechatXmlCipher.encrypt("hello", RECEIVE_ID, fullKey);

        assertThat(WechatXmlCipher.decrypt(encrypted, fullKey)).isEqualTo("hello");
    }

    @Test
    void decryptRoundTripWithEmptyPlaintext()
    {
        String encrypted = WechatXmlCipher.encrypt("", RECEIVE_ID, AES_KEY);

        assertThat(WechatXmlCipher.decrypt(encrypted, AES_KEY)).isEmpty();
    }

    // ---------- 密钥/密文异常 ----------

    @Test
    void decryptRejectsBlankKey()
    {
        assertThatThrownBy(() -> WechatXmlCipher.decrypt("AAAA", "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EncodingAESKey 未配置");
    }

    @Test
    void encryptWrapsBlankKeyError()
    {
        assertThatThrownBy(() -> WechatXmlCipher.encrypt("x", RECEIVE_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("微信报文加密失败")
                .hasMessageContaining("EncodingAESKey 未配置");
    }

    @Test
    void decryptRejectsKeyNotDecodingTo32Bytes()
    {
        // 16 字节密钥（24 字符 Base64）
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> WechatXmlCipher.decrypt("AAAA", shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("须为 32 字节");
    }

    @Test
    void decryptRejectsNonBase64Ciphertext()
    {
        assertThatThrownBy(() -> WechatXmlCipher.decrypt("not-base64!!", AES_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("微信报文解密失败");
    }

    @Test
    void decryptRejectsTooShortPlaintext() throws Exception
    {
        // 10 字节明文加密后解出 16 字节，不足 16+4 的最小结构
        String encrypted = encryptRaw(new byte[10]);

        assertThatThrownBy(() -> WechatXmlCipher.decrypt(encrypted, AES_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密报文过短");
    }

    @Test
    void decryptRejectsIllegalLengthField() throws Exception
    {
        // 32 字节明文：random(16) + msg_len=Integer.MAX_VALUE + 余量
        byte[] plain = new byte[32];
        plain[16] = (byte) 0x7F;
        plain[17] = (byte) 0xFF;
        plain[18] = (byte) 0xFF;
        plain[19] = (byte) 0xFF;
        String encrypted = encryptRaw(plain);

        assertThatThrownBy(() -> WechatXmlCipher.decrypt(encrypted, AES_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密报文长度字段非法");
    }

    // ---------- XML 字段提取 ----------

    @Test
    void xmlValueReadsCdata()
    {
        String xml = "<xml><FromUserName><![CDATA[openid-1]]></FromUserName></xml>";

        assertThat(WechatXmlCipher.xmlValue(xml, "FromUserName")).isEqualTo("openid-1");
    }

    @Test
    void xmlValueReadsPlainValue()
    {
        String xml = "<xml><MsgType>text</MsgType></xml>";

        assertThat(WechatXmlCipher.xmlValue(xml, "MsgType")).isEqualTo("text");
    }

    @Test
    void xmlValueSupportsMultilineContent()
    {
        String xml = "<xml><Content><![CDATA[第一行\n第二行]]></Content></xml>";

        assertThat(WechatXmlCipher.xmlValue(xml, "Content")).isEqualTo("第一行\n第二行");
    }

    @Test
    void xmlValueReturnsNullWhenTagMissingOrInputNull()
    {
        assertThat(WechatXmlCipher.xmlValue("<xml/>", "Absent")).isNull();
        assertThat(WechatXmlCipher.xmlValue(null, "MsgType")).isNull();
        assertThat(WechatXmlCipher.xmlValue("<xml/>", null)).isNull();
    }

    /** 用同一密钥/IV 按协议结构加密任意明文（构造边界密文用） */
    private static String encryptRaw(byte[] plain) throws Exception
    {
        byte[] key = Base64.getDecoder().decode(AES_KEY + "=");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new IvParameterSpec(key, 0, 16));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain));
    }

    /** 便于校验 UTF-8 中文往返的固定断言字符集长度 */
    @Test
    void roundTripPreservesUtf8ByteLength()
    {
        String plaintext = "中文";
        String encrypted = WechatXmlCipher.encrypt(plaintext, RECEIVE_ID, AES_KEY);

        assertThat(WechatXmlCipher.decrypt(encrypted, AES_KEY)
                .getBytes(StandardCharsets.UTF_8)).hasSize(6);
    }
}
