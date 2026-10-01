package ai.lawyers.system.service.lawyers.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;

import ai.lawyers.common.utils.sign.PiiCryptoUtils;

/**
 * G1-b3：录音文件 SM4-GCM 加解密服务。
 *
 * <p>封装 {@link PiiCryptoUtils#recordingEncrypt(byte[])} / {@link PiiCryptoUtils#recordingDecrypt(byte[])}，
 * 提供面向流的加解密。SM4-GCM 为整块认证加密，当前实现先将流读入内存再处理
 * （政务热线录音通常为数 MB～数十 MB，可接受；超大文件后续可分片优化）。</p>
 *
 * <p>密文格式：{@code magic(4 "AIRC") + version(1) + iv(12) + SM4-GCM 密文+tag}。
 * 解密时若头部非 magic，视为存量明文原样返回，兼容迁移窗口。</p>
 *
 * @author ai-lawyers
 */
@Service
public class RecordingCryptoService
{
    private static final int BUFFER_SIZE = 8192;

    /**
     * 加密输入流为密文字节。
     *
     * @param plainIn 明文字节流（调用方负责关闭）
     * @return 密文字节（含 magic/version/iv 头）
     */
    public byte[] encrypt(InputStream plainIn) throws IOException
    {
        byte[] plain = readAll(plainIn);
        return PiiCryptoUtils.recordingEncrypt(plain);
    }

    /**
     * 解密输入流为明文字节。若流首 4 字节非加密 magic，原样返回（存量明文兼容）。
     *
     * @param storedIn 存储字节流（加密或明文，调用方负责关闭）
     * @return 明文字节
     */
    public byte[] decrypt(InputStream storedIn) throws IOException
    {
        byte[] stored = readAll(storedIn);
        return PiiCryptoUtils.recordingDecrypt(stored);
    }

    /**
     * 判断字节是否为加密录音（前 4 字节 magic）。
     */
    public boolean isEncrypted(byte[] header)
    {
        return PiiCryptoUtils.isEncryptedRecording(header);
    }

    private byte[] readAll(InputStream in) throws IOException
    {
        if (in == null)
        {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = in.read(buf)) != -1)
        {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
