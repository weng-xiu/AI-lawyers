package ai.lawyers.system.service.impl.lawyers.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.storage.RecordingCryptoService;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;

/**
 * F2：录音本地文件系统存储实现（默认模式，完全向后兼容）。
 *
 * <p>与改造前行为一致：以 {@code call.recording.base-path} 为根目录，
 * 保存/读取/删除均基于本地文件系统；{@link #getPlayUrl} 返回 null，
 * 播放/下载仍走后端流式接口。</p>
 *
 * @author ai-lawyers
 */
@Service
@ConditionalOnProperty(name = "call.recording.type", havingValue = "local", matchIfMissing = true)
public class LocalRecordingStorageService implements RecordingStorageService
{
    private static final Logger log = LoggerFactory.getLogger(LocalRecordingStorageService.class);

    /** 录音文件根目录，对应 FreeSWITCH recordings_dir；默认相对路径（跨平台），由环境变量覆盖 */
    @Value("${call.recording.base-path:recordings}")
    private String recordingBasePath;

    /** G1-b3：是否启用录音 SM4 加密（默认关闭，灰度） */
    @Value("${call.recording.encrypt-enabled:false}")
    private boolean encryptEnabled;

    @Autowired(required = false)
    private RecordingCryptoService recordingCryptoService;

    @Override
    public void save(String key, File file) throws IOException
    {
        // 本地模式：FreeSWITCH 已把文件写到目标位置。
        // 加密启用时：读明文 → 加密 → 原地覆盖为密文（已加密则跳过）
        if (!encryptEnabled || recordingCryptoService == null || file == null || !file.exists())
        {
            log.debug("[RecordingStorage] 本地模式无需额外保存 key={}", key);
            return;
        }
        encryptLocalFileInPlace(file);
    }

    @Override
    public void save(String key, InputStream inputStream, long contentLength, String contentType)
            throws IOException
    {
        // 本地模式流式保存：加密启用时把密文写到目标文件
        if (!encryptEnabled || recordingCryptoService == null || inputStream == null)
        {
            log.debug("[RecordingStorage] 本地模式无需额外保存 key={}", key);
            return;
        }
        File target = resolveFile(key);
        if (target == null)
        {
            return;
        }
        byte[] cipher = recordingCryptoService.encrypt(inputStream);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists())
        {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(target))
        {
            fos.write(cipher);
        }
        log.debug("[RecordingStorage] 本地加密保存 key={} cipherLen={}", key, cipher.length);
    }

    @Override
    public InputStream load(String key) throws IOException
    {
        File file = resolveFile(key);
        if (file == null || !file.exists() || !file.isFile())
        {
            return null;
        }
        if (!encryptEnabled || recordingCryptoService == null)
        {
            return Files.newInputStream(file.toPath());
        }
        // 加密模式：读文件 → 检测 magic → 已加密则解密，未加密原样返回
        try (InputStream in = new FileInputStream(file))
        {
            byte[] plain = recordingCryptoService.decrypt(in);
            return new java.io.ByteArrayInputStream(plain);
        }
    }

    /**
     * 原地加密本地文件：读明文 → 加密 → 写临时文件 → 原子替换。
     * 已加密（头部 magic）则跳过，保证幂等。
     */
    private void encryptLocalFileInPlace(File file) throws IOException
    {
        byte[] raw = Files.readAllBytes(file.toPath());
        if (recordingCryptoService.isEncrypted(raw))
        {
            log.debug("[RecordingStorage] 文件已加密，跳过 key={}", file.getAbsolutePath());
            return;
        }
        byte[] cipher = recordingCryptoService.encrypt(new java.io.ByteArrayInputStream(raw));
        File tmp = new File(file.getAbsolutePath() + ".enc.tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp))
        {
            fos.write(cipher);
        }
        // 原子替换（Windows 下 Files.move 可能失败，退化为删旧改名）
        try
        {
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            if (file.delete() && tmp.renameTo(file))
            {
                log.debug("[RecordingStorage] 本地文件加密完成（降级替换） key={}", file.getAbsolutePath());
            }
            else
            {
                tmp.delete();
                throw e;
            }
        }
        log.debug("[RecordingStorage] 本地文件加密完成 key={}", file.getAbsolutePath());
    }

    @Override
    public boolean delete(String key)
    {
        File file = resolveFile(key);
        if (file == null)
        {
            return false;
        }
        if (!file.exists())
        {
            return true; // 不存在视为成功（幂等）
        }
        boolean deleted = file.delete();
        if (!deleted)
        {
            log.warn("[RecordingStorage] 本地文件删除失败: {}", file.getAbsolutePath());
        }
        return deleted;
    }

    @Override
    public boolean exists(String key)
    {
        File file = resolveFile(key);
        return file != null && file.exists() && file.isFile();
    }

    @Override
    public String getPlayUrl(String key, int expirySeconds)
    {
        return null; // 本地模式走后端流式接口
    }

    @Override
    public boolean isObjectStorage()
    {
        return false;
    }

    /**
     * 解析 key 为真实文件：支持绝对路径、相对路径（拼 basePath）、
     * $${recordings_dir} 前缀兼容（与 AiCallRecordController / 质检 原有逻辑对齐）。
     */
    private File resolveFile(String key)
    {
        if (StringUtils.isEmpty(key))
        {
            return null;
        }
        String path = key.trim();
        File file = new File(path);
        if (file.isAbsolute())
        {
            return file;
        }
        if (path.startsWith("$${recordings_dir}") || path.startsWith("${recordings_dir}"))
        {
            path = path.substring(path.indexOf('}') + 1);
            if (path.startsWith("/") || path.startsWith("\\"))
            {
                path = path.substring(1);
            }
        }
        return new File(recordingBasePath, path);
    }
}
