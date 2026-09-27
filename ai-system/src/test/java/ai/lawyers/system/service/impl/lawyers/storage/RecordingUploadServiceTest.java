package ai.lawyers.system.service.impl.lawyers.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;

/**
 * F2：{@link RecordingUploadService} 单元测试。
 *
 * <p>验证对象存储模式下上传、DB 更新、本地文件清理；本地模式不动作。</p>
 *
 * @author ai-lawyers
 */
class RecordingUploadServiceTest
{
    private RecordingStorageService storageService;
    private AiCallRecordMapper callRecordMapper;
    private RecordingUploadService service;

    @BeforeEach
    void setUp()
    {
        storageService = Mockito.mock(RecordingStorageService.class);
        callRecordMapper = Mockito.mock(AiCallRecordMapper.class);
        service = new RecordingUploadService();
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "callRecordMapper", callRecordMapper);
        ReflectionTestUtils.setField(service, "deleteLocalAfterUpload", true);
    }

    @Test
    void enqueue_localMode_noop() throws Exception
    {
        when(storageService.isObjectStorage()).thenReturn(false);
        service.init();
        File tmp = File.createTempFile("rec", ".wav");
        tmp.deleteOnExit();

        service.enqueue(1000L, tmp);
        Thread.sleep(100);

        verify(storageService, never()).save(anyString(), any(File.class));
        verify(callRecordMapper, never()).updateRecordingInfo(any(AiCallRecord.class));
        assertThat(tmp).exists();
    }

    @Test
    void enqueue_s3Mode_uploadsUpdatesDbAndDeletesLocal() throws Exception
    {
        when(storageService.isObjectStorage()).thenReturn(true);
        service.init();
        File tmp = File.createTempFile("rec", ".wav");
        tmp.deleteOnExit();

        service.enqueue(1000L, tmp);
        Thread.sleep(500);

        verify(storageService).save(tmp.getName(), tmp);
        verify(callRecordMapper).updateRecordingInfo(any(AiCallRecord.class));
        assertThat(tmp).doesNotExist();
    }

    @Test
    void enqueue_s3Mode_deleteLocalDisabled_keepsLocal() throws Exception
    {
        when(storageService.isObjectStorage()).thenReturn(true);
        ReflectionTestUtils.setField(service, "deleteLocalAfterUpload", false);
        service.init();
        File tmp = File.createTempFile("rec", ".wav");
        tmp.deleteOnExit();

        service.enqueue(1000L, tmp);
        Thread.sleep(500);

        verify(storageService).save(tmp.getName(), tmp);
        assertThat(tmp).exists();
    }

    @Test
    void enqueue_uploadFails_localFileKept() throws Exception
    {
        when(storageService.isObjectStorage()).thenReturn(true);
        service.init();
        File tmp = File.createTempFile("rec", ".wav");
        tmp.deleteOnExit();
        org.mockito.Mockito.doThrow(new java.io.IOException("网络错误"))
                .when(storageService).save(anyString(), any(File.class));

        service.enqueue(1000L, tmp);
        Thread.sleep(500);

        verify(storageService).save(tmp.getName(), tmp);
        verify(callRecordMapper, never()).updateRecordingInfo(any(AiCallRecord.class));
        assertThat(tmp).exists();
    }

    @Test
    void enqueue_fileNotExists_noop() throws Exception
    {
        when(storageService.isObjectStorage()).thenReturn(true);
        service.init();
        File notExist = new File("not-exist-file.wav");

        service.enqueue(1000L, notExist);
        Thread.sleep(100);

        verify(storageService, never()).save(anyString(), any(File.class));
    }

    @Test
    void enqueue_nullRecordId_noop() throws Exception
    {
        when(storageService.isObjectStorage()).thenReturn(true);
        service.init();
        File tmp = File.createTempFile("rec", ".wav");
        tmp.deleteOnExit();

        service.enqueue(null, tmp);
        Thread.sleep(100);

        verify(storageService, never()).save(anyString(), any(File.class));
    }
}
