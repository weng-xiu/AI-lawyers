package ai.lawyers.system.service.impl.lawyers.storage;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;

/**
 * F2/P0-5（V2.58）：录音文件异步上传 / 加密服务。
 *
 * <p>FreeSWITCH 本地录制完成（RECORD_STOP）后，由多 worker 线程池处理：
 * <ul>
 *   <li>S3 模式：上传对象存储（加密由 storageService.save 内部处理），
 *       成功后更新 DB record_file 为 object key 并按配置清理本地文件；</li>
 *   <li>本地模式 + 加密开关：原地 SM4 加密；未启用加密时不入队。</li>
 * </ul>
 * 落盘竞态由 worker 等待文件就绪（存在 + 长度 > 0 + 连续两次轮询长度不变）确认，
 * 处理失败按指数退避重试，全部失败保留本地文件。</p>
 *
 * @author ai-lawyers
 */
@Service
public class RecordingUploadService
{
    private static final Logger log = LoggerFactory.getLogger(RecordingUploadService.class);

    @Autowired
    private RecordingStorageService storageService;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    /** 对象存储模式下，上传成功后是否删除本地文件 */
    @Value("${call.recording.s3.delete-local-after-upload:true}")
    private boolean deleteLocalAfterUpload;

    /** G1-b3：本地模式录音 SM4 加密开关（默认关闭） */
    @Value("${call.recording.encrypt-enabled:false}")
    private boolean encryptEnabled;

    @Value("${call.recording.upload.workers:2}")
    private int workers;

    @Value("${call.recording.upload.queue-capacity:1000}")
    private int queueCapacity;

    @Value("${call.recording.upload.max-attempts:3}")
    private int maxAttempts;

    @Value("${call.recording.upload.retry-backoff-base-ms:1000}")
    private long retryBackoffBaseMs;

    @Value("${call.recording.upload.ready-timeout-seconds:30}")
    private long readyTimeoutSeconds;

    @Value("${call.recording.upload.ready-poll-interval-ms:500}")
    private long readyPollIntervalMs;

    private ExecutorService executor;

    @PostConstruct
    public void init()
    {
        boolean needPool = storageService.isObjectStorage()
                || (!storageService.isObjectStorage() && encryptEnabled);
        if (needPool)
        {
            int poolSize = Math.max(1, workers);
            executor = new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(Math.max(1, queueCapacity)),
                    r -> {
                        Thread t = new Thread(r, "recording-upload");
                        t.setDaemon(true);
                        return t;
                    });
            log.info("[RecordingUpload] worker 池就绪 workers={} queue={} mode={}",
                    poolSize, queueCapacity,
                    storageService.isObjectStorage() ? "s3" : "local-encrypt");
        }
    }

    @PreDestroy
    public void destroy()
    {
        if (executor != null)
        {
            executor.shutdown();
            try
            {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS))
                {
                    executor.shutdownNow();
                }
            }
            catch (InterruptedException e)
            {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 排队处理录音文件。文件尚不存在也可入队——worker 会等待落盘就绪（RECORD_STOP 竞态兜底）。
     *
     * @param recordId   通话记录ID
     * @param localFile  FreeSWITCH 录制完成的本地文件
     */
    public void enqueue(Long recordId, File localFile)
    {
        if (recordId == null || localFile == null)
        {
            log.warn("[RecordingUpload] 参数异常，跳过 recordId={} file={}", recordId, localFile);
            return;
        }
        // 本地模式且加密未启用：无任何工作
        if (!storageService.isObjectStorage() && !encryptEnabled)
        {
            return;
        }
        if (executor == null)
        {
            log.warn("[RecordingUpload] worker 池未初始化，跳过 recordId={}", recordId);
            return;
        }
        try
        {
            executor.execute(() -> process(recordId, localFile));
        }
        catch (Exception e)
        {
            // 队列溢出：本地文件保留，需人工/定时扫描补传
            log.error("[RecordingUpload] 任务入队失败（队列已满？）recordId={} file={}: {}",
                    recordId, localFile.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * worker 主流程：等待就绪 → 重试处理。
     */
    private void process(Long recordId, File localFile)
    {
        long readyTimeoutMs = Math.max(0L, readyTimeoutSeconds) * 1000L;
        if (!waitUntilReady(localFile, readyTimeoutMs, Math.max(1L, readyPollIntervalMs)))
        {
            log.error("[RecordingUpload] 等待录音文件就绪超时，放弃本次处理 recordId={} file={}",
                    recordId, localFile.getAbsolutePath());
            return;
        }
        if (storageService.isObjectStorage())
        {
            uploadWithRetry(recordId, localFile);
        }
        else
        {
            encryptWithRetry(recordId, localFile);
        }
    }

    /**
     * S3 上传 + DB 回写，失败指数退避重试。
     */
    private void uploadWithRetry(Long recordId, File localFile)
    {
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++)
        {
            try
            {
                // 统一使用原始文件名作为 key（含扩展名），便于对象存储中识别
                String objectKey = localFile.getName();
                storageService.save(objectKey, localFile);

                // 更新数据库：record_file 改为 object key（不带前缀，S3 buildKey 会补前缀）
                AiCallRecord update = new AiCallRecord();
                update.setRecordId(recordId);
                update.setRecordFile(objectKey);
                update.setRecordingUrl("/lawyers/call/record/" + recordId + "/play");
                callRecordMapper.updateRecordingInfo(update);

                log.info("[RecordingUpload] 上传成功 recordId={} key={} attempt={}",
                        recordId, objectKey, attempt);

                if (deleteLocalAfterUpload)
                {
                    boolean deleted = localFile.delete();
                    if (!deleted)
                    {
                        log.warn("[RecordingUpload] 本地文件删除失败 recordId={} path={}",
                                recordId, localFile.getAbsolutePath());
                    }
                }
                return;
            }
            catch (Exception e)
            {
                if (!backoffSleep(recordId, attempt, e))
                {
                    return;
                }
            }
        }
    }

    /**
     * 本地模式原地加密，失败指数退避重试。
     */
    private void encryptWithRetry(Long recordId, File localFile)
    {
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++)
        {
            try
            {
                storageService.save(localFile.getName(), localFile);
                log.info("[RecordingUpload] 本地加密完成 recordId={} attempt={}", recordId, attempt);
                return;
            }
            catch (Exception e)
            {
                if (!backoffSleep(recordId, attempt, e))
                {
                    return;
                }
            }
        }
    }

    /**
     * 失败后退避；返回 false 表示重试次数已尽或线程被中断，应结束处理。
     */
    private boolean backoffSleep(Long recordId, int attempt, Exception error)
    {
        int limit = Math.max(1, maxAttempts);
        if (attempt >= limit)
        {
            log.error("[RecordingUpload] 重试 {} 次仍失败，保留本地文件待补处理 recordId={}: {}",
                    limit, recordId, error.getMessage());
            return false;
        }
        long delay = Math.max(0L, retryBackoffBaseMs) * (1L << (attempt - 1));
        log.warn("[RecordingUpload] 第 {} 次处理失败 recordId={}，{}ms 后重试: {}",
                attempt, recordId, delay, error.getMessage());
        try
        {
            Thread.sleep(delay);
            return true;
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 等待录音文件落盘就绪：存在 + 长度 > 0 + 连续两次轮询长度不变（写盘已结束）。
     *
     * @return true=就绪；false=超时
     */
    static boolean waitUntilReady(File file, long timeoutMs, long pollIntervalMs)
    {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        long lastLength = -1L;
        do
        {
            if (file.exists() && file.isFile())
            {
                long len = file.length();
                if (len > 0 && len == lastLength)
                {
                    return true;
                }
                lastLength = len;
            }
            try
            {
                Thread.sleep(Math.max(1L, pollIntervalMs));
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        while (System.currentTimeMillis() < deadline);
        return false;
    }
}
