package ai.lawyers.system.service.impl.lawyers.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
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

    /** 录音文件根目录，对应 FreeSWITCH recordings_dir */
    @Value("${call.recording.base-path:C:/Program Files/FreeSWITCH/recordings}")
    private String recordingBasePath;

    @Override
    public void save(String key, File file) throws IOException
    {
        // 本地模式：FreeSWITCH 已把文件写到目标位置，无需重复保存
        log.debug("[RecordingStorage] 本地模式无需额外保存 key={}", key);
    }

    @Override
    public void save(String key, InputStream inputStream, long contentLength, String contentType)
            throws IOException
    {
        // 本地模式：同上，仅支持 File 传入
        log.debug("[RecordingStorage] 本地模式无需额外保存 key={}", key);
    }

    @Override
    public InputStream load(String key) throws IOException
    {
        File file = resolveFile(key);
        if (file == null || !file.exists() || !file.isFile())
        {
            return null;
        }
        return Files.newInputStream(file.toPath());
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
