package ai.lawyers.system.service.impl.lawyers.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;

/**
 * F2/P0-5：{@link RecordingUploadService} 单元测试。
 *
 * <p>验证多 worker 处理：S3 上传 + DB 更新 + 本地清理、失败退避重试、
 * 本地加密开关、文件未就绪超时、空参防护。</p>
 *
 * @author ai-lawyers
 */
class RecordingUploadServiceTest
{
    private RecordingStorageService storageService;
    private AiCallRecordMapper callRecordMapper;
    private RecordingUploadService service;

    /** 构造服务并设置上传池参数（快速轮询，测试不等待真实退避） */
    private RecordingUploadService buildService()
    {
        storageService = Mockito.mock(RecordingStorageService.class);
        callRecordMapper = Mockito.mock(AiCallRecordMapper.class);
        RecordingUploadService svc = new RecordingUploadService();
        ReflectionTestUtils.setField(svc, "storageService", storageService);
        ReflectionTestUtils.setField(svc, "callRecordMapper", callRecordMapper);
        ReflectionTestUtils.setField(svc, "deleteLocalAfterUpload", true);
        ReflectionTestUtils.setField(svc, "workers", 2);
        ReflectionTestUtils.setField(svc, "queueCapacity", 100);
        ReflectionTestUtils.setField(svc, "maxAttempts", 3);
        ReflectionTestUtils.setField(svc, "retryBackoffBaseMs", 10L);
        ReflectionTestUtils.setField(svc, "readyTimeoutSeconds", 5L);
        ReflectionTestUtils.setField(svc, "readyPollIntervalMs", 20L);
        return svc;
    }

    /** 创建带 1 字节内容的临时 wav（就绪确认要求长度 >0），退出时删除 */
    private File tempWav() throws java.io.IOException
    {
        File tmp = File.createTempFile("rec", ".wav");
        java.nio.file.Files.write(tmp.toPath(), new byte[] {1});
        tmp.deleteOnExit();
        return tmp;
    }

    @Test
    void enqueue_localMode_encryptDisabled_noop() throws Exception
    {
        service = buildService();
        when(storageService.isObjectStorage()).thenReturn(false);
        ReflectionTestUtils.setField(service, "encryptEnabled", false);
        service.init();
        File tmp = tempWav();

        service.enqueue(1000L, tmp);
        Thread.sleep(100);

        verify(storageService, never()).save(anyString(), any(File.class));
    }

    @Test
    void enqueue_localMode_encryptEnabled_triggersSave() throws Exception
    {
        service = buildService();
        when(storageService.isObjectStorage()).thenReturn(false);
        ReflectionTestUtils.setField(service, "encryptEnabled", true);
        service.init();
        File tmp = tempWav();

        service.enqueue(1000L, tmp);
        Thread.sleep(200);

        verify(storageService).save(tmp.getName(), tmp);
        verify(callRecordMapper, never()).updateRecordingInfo(any(AiCallRecord.class));
        assertThat(tmp).exists();
    }

    @Test
    void enqueue_s3Mode_uploadsUpdatesDbAndDeletesLocal() throws Exception
    {
        service = buildService();
        when(storageService.isObjectStorage()).thenReturn(true);
        service.init();
        File tmp = tempWav();

        service.enqueue(1000L, tmp);
        Thread.sleep(300);

        verify(storageService).save(tmp.getName(), tmp);
        verify(callRecordMapper).updateRecordingInfo(any(AiCallRecord.class));
        assertThat(tmp).doesNotExist();
    }

    @Test
    void enqueue_s3Mode_deleteLocalDisabled_keepsLocal() throws Exception
    {
        service = buildService();
        when(storageService.isObjectStorage()).thenReturn(true);
        ReflectionTestUtils.setField(service, "deleteLocalAfterUpload", false);
        service.init();
        File tmp = tempWav();

        service.enqueue(1000L, tmp);
        Thread.sleep(300);

        verify(storageService).save(tmp.getName(), tmp);
        assertThat(tmp).exists();
    }

    @Test
    void enqueue_uploadFails_retriesAndKeepsLocal() throws Exception
    {
        service = buildService();
        when(storageService.isObjectStorage()).thenReturn(true);
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        service.init();
        File tmp = tempWav();
        Mockito.doThrow(new java.io.IOException("网络错误"))
                .when(storageService).save(anyString(), any(File.class));

        service.enqueue(1000L, tmp);
        Thread.sleep(500);

        // 两次尝试（首次失败 → 10ms 退避 → 再失败），DB 不更新，文件保留
        verify(storageService, times(2)).save(tmp.getName(), tmp);
        verify(callRecordMapper, never()).updateRecordingInfo(any(AiCallRecord.class));
        assertThat(tmp).exists();
    }

    @Test
    void enqueue_fileNotExists_readyTimeout_saveNeverCalled() throws Exception
    {
        service = buildService();
        when(storageService.isObjectStorage()).thenReturn(true);
        ReflectionTestUtils.setField(service, "readyTimeoutSeconds", 0L);
        service.init();
        File notExist = new File("not-exist-file.wav");

        service.enqueue(1000L, notExist);
        Thread.sleep(200);

        verify(storageService, never()).save(anyString(), any(File.class));
    }

    @Test
    void enqueue_nullRecordId_noop() throws Exception
    {
        service = buildService();
        when(storageService.isObjectStorage()).thenReturn(true);
        service.init();
        File tmp = tempWav();

        service.enqueue(null, tmp);
        Thread.sleep(100);

        verify(storageService, never()).save(anyString(), any(File.class));
    }

    @Test
    void waitUntilReady_existingContent_returnsTrue() throws Exception
    {
        File tmp = null;
        try
        {
            tmp = tempWav();
            assertThat(RecordingUploadService.waitUntilReady(tmp, 2000L, 20L)).isTrue();
        }
        finally
        {
            if (tmp != null)
            {
                tmp.delete();
            }
        }
    }

    @Test
    void waitUntilReady_missingFileZeroTimeout_returnsFalse()
    {
        File notExist = new File("ready-not-exist-file.wav");
        assertThat(RecordingUploadService.waitUntilReady(notExist, 0L, 1L)).isFalse();
    }
}
