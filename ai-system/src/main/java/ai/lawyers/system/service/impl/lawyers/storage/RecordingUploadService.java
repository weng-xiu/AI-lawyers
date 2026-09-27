package ai.lawyers.system.service.impl.lawyers.storage;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;

/**
 * F2：录音文件异步上传服务（对象存储模式下，FreeSWITCH 本地录制完成后自动上传）。
 *
 * <p>单线程串行上传（避免对象存储连接池争抢），失败后保留本地文件，
 * 上传成功后更新 DB record_file 为 object key 并清理本地临时文件。</p>
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

    private ExecutorService executor;

    @PostConstruct
    public void init()
    {
        if (storageService.isObjectStorage())
        {
            executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(1000),
                    r -> {
                        Thread t = new Thread(r, "recording-upload");
                        t.setDaemon(true);
                        return t;
                    });
            log.info("[RecordingUpload] 对象存储模式已启用，单线程上传队列就绪");
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
            }
        }
    }

    /**
     * 排队上传录音文件。本地模式直接忽略。
     *
     * @param recordId   通话记录ID
     * @param localFile  FreeSWITCH 录制完成的本地文件
     */
    public void enqueue(Long recordId, File localFile)
    {
        if (!storageService.isObjectStorage())
        {
            return;
        }
        if (recordId == null || localFile == null || !localFile.exists())
        {
            log.warn("[RecordingUpload] 参数异常，跳过上传 recordId={} file={}", recordId, localFile);
            return;
        }
        try
        {
            executor.execute(() -> doUpload(recordId, localFile));
        }
        catch (Exception e)
        {
            log.error("[RecordingUpload] 任务入队失败 recordId={}: {}", recordId, e.getMessage());
        }
    }

    private void doUpload(Long recordId, File localFile)
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

            log.info("[RecordingUpload] 上传成功 recordId={} key={}", recordId, objectKey);

            if (deleteLocalAfterUpload)
            {
                boolean deleted = localFile.delete();
                if (!deleted)
                {
                    log.warn("[RecordingUpload] 本地文件删除失败 recordId={} path={}", recordId, localFile.getAbsolutePath());
                }
            }
        }
        catch (Exception e)
        {
            log.error("[RecordingUpload] 上传失败 recordId={} file={}: {}", recordId, localFile.getAbsolutePath(), e.getMessage());
            // 本地文件保留，后台可人工/定时任务重试
        }
    }
}
