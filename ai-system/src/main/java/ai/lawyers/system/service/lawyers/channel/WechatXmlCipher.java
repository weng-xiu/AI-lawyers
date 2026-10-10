package ai.lawyers.system.service.lawyers.channel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * P3-#16：微信/政务微信回调报文加解密工具（企业微信 EncodingAESKey 约定兼容）。
 *
 * <p>验签：sha1(hex(sort(token, timestamp, nonce, encrypt)))；
 * 加解密：AES-256-CBC + PKCS7 填充，密文结构
 * {@code Base64( IV(key前16字节) ⊕ random(16) + msg_len(4, 大端) + msg + receiveId )}。
 * 供 {@link MockWechatChannelAdapter} 与联调回包模拟使用。</p>
 *
 * @author ai-lawyers
 */
public final class WechatXmlCipher
{
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private WechatXmlCipher()
    {
    }

    /** 回调验签：sha1(sort(token,timestamp,nonce,encrypt)) */
    public static String signature(String token, String timestamp, String nonce, String encrypt)
    {
        List<String> parts = new ArrayList<>();
        parts.add(nullSafe(token));
        parts.add(nullSafe(timestamp));
        parts.add(nullSafe(nonce));
        parts.add(nullSafe(encrypt));
        Collections.sort(parts);
        return sha1Hex(String.join("", parts));
    }

    /**
     * 加密明文报文（联调回包模拟与测试向量构造用）。
     *
     * @param aesKeyBase64 EncodingAESKey（43 字符，补 '=' 后 Base64 解码为 32 字节）
     */
    public static String encrypt(String plaintext, String receiveId, String aesKeyBase64)
    {
        try
        {
            byte[] key = decodeKey(aesKeyBase64);
            byte[] random = new byte[16];
            new java.security.SecureRandom().nextBytes(random);
            byte[] msg = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);
            byte[] plain = new byte[16 + 4 + msg.length + receiveIdBytes.length];
            System.arraycopy(random, 0, plain, 0, 16);
            writeInt(plain, 16, msg.length);
            System.arraycopy(msg, 0, plain, 20, msg.length);
            System.arraycopy(receiveIdBytes, 0, plain, 20 + msg.length, receiveIdBytes.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(key, 0, 16));
            return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(plain));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("微信报文加密失败: " + e.getMessage(), e);
        }
    }

    /** 解密回调密文体为明文报文（不校验 receiveId，由调用方按需核对） */
    public static String decrypt(String encryptBase64, String aesKeyBase64)
    {
        try
        {
            byte[] key = decodeKey(aesKeyBase64);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(key, 0, 16));
            byte[] plain = cipher.doFinal(java.util.Base64.getDecoder().decode(encryptBase64));
            if (plain.length < 20)
            {
                throw new IllegalStateException("解密报文过短");
            }
            int msgLen = readInt(plain, 16);
            if (msgLen < 0 || 20L + msgLen > plain.length)
            {
                throw new IllegalStateException("解密报文长度字段非法");
            }
            return new String(plain, 20, msgLen, StandardCharsets.UTF_8);
        }
        catch (IllegalStateException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IllegalStateException("微信报文解密失败: " + e.getMessage(), e);
        }
    }

    /** 提取 XML 单值字段（支持 CDATA 与裸值；不存在返回 null） */
    public static String xmlValue(String xml, String tag)
    {
        if (xml == null || tag == null)
        {
            return null;
        }
        Pattern p = Pattern.compile("<" + Pattern.quote(tag) + ">(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</"
                + Pattern.quote(tag) + ">", Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static byte[] decodeKey(String aesKeyBase64)
    {
        if (aesKeyBase64 == null || aesKeyBase64.trim().isEmpty())
        {
            throw new IllegalStateException("EncodingAESKey 未配置");
        }
        String b64 = aesKeyBase64.trim();
        if (b64.length() == 43)
        {
            b64 = b64 + "=";
        }
        byte[] key = java.util.Base64.getDecoder().decode(b64);
        if (key.length != 32)
        {
            throw new IllegalStateException("EncodingAESKey 解码后须为 32 字节，实际 " + key.length);
        }
        return key;
    }

    private static void writeInt(byte[] buf, int offset, int value)
    {
        buf[offset] = (byte) ((value >>> 24) & 0xFF);
        buf[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        buf[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        buf[offset + 3] = (byte) (value & 0xFF);
    }

    private static int readInt(byte[] buf, int offset)
    {
        return ((buf[offset] & 0xFF) << 24) | ((buf[offset + 1] & 0xFF) << 16)
                | ((buf[offset + 2] & 0xFF) << 8) | (buf[offset + 3] & 0xFF);
    }

    private static String sha1Hex(String plain)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest)
            {
                sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }

    private static String nullSafe(String s)
    {
        return s == null ? "" : s;
    }
}
