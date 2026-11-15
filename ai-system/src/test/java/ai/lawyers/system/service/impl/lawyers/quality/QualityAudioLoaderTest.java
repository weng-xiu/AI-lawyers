package ai.lawyers.system.service.impl.lawyers.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;
import ai.lawyers.system.service.lawyers.voice.VoiceEngineManager;
import ai.lawyers.system.service.lawyers.voice.VoiceModelEnum;

/**
 * QualityAudioLoader 单测：对象存储/本地录音加载与就绪等待、
 * 转写引擎覆盖与文本裁剪、WAV 声学分析与非 WAV/解析失败跳过。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class QualityAudioLoaderTest
{
    @TempDir
    Path tempDir;

    @Mock
    private VoiceEngineManager voiceEngineManager;

    @Mock
    private AcousticFeatureAnalyzer acousticFeatureAnalyzer;

    @Mock
    private RecordingStorageService recordingStorageService;

    private QualityAudioLoader loader;

    @BeforeEach
    void setUp()
    {
        loader = new QualityAudioLoader();
        ReflectionTestUtils.setField(loader, "voiceEngineManager", voiceEngineManager);
        ReflectionTestUtils.setField(loader, "acousticFeatureAnalyzer", acousticFeatureAnalyzer);
        ReflectionTestUtils.setField(loader, "recordingBasePath", tempDir.toString());
        ReflectionTestUtils.setField(loader, "readyTimeoutSeconds", 2L);
        ReflectionTestUtils.setField(loader, "readyPollIntervalMs", 1L);
    }

    @Test
    void loadsFromObjectStorage() throws Exception
    {
        ReflectionTestUtils.setField(loader, "recordingStorageService", recordingStorageService);
        when(recordingStorageService.isObjectStorage()).thenReturn(true);
        when(recordingStorageService.load("k/a.wav"))
                .thenReturn(new ByteArrayInputStream("AUDIO".getBytes(StandardCharsets.UTF_8)));

        QualityAudioLoader.LoadedAudio loaded = loader.loadRecording(record("k/a.wav"));

        assertThat(loaded.data).isEqualTo("AUDIO".getBytes(StandardCharsets.UTF_8));
        assertThat(loaded.fileName).isEqualTo("k/a.wav");
    }

    @Test
    void objectStorageEmptyKeyThrows()
    {
        ReflectionTestUtils.setField(loader, "recordingStorageService", recordingStorageService);
        when(recordingStorageService.isObjectStorage()).thenReturn(true);

        assertThatThrownBy(() -> loader.loadRecording(record("")))
                .isInstanceOf(RecordingNotReadyException.class)
                .hasMessageContaining("recordId=100");
    }

    @Test
    void objectStorageNullStreamThrows() throws Exception
    {
        ReflectionTestUtils.setField(loader, "recordingStorageService", recordingStorageService);
        when(recordingStorageService.isObjectStorage()).thenReturn(true);
        when(recordingStorageService.load("k/a.wav")).thenReturn(null);

        assertThatThrownBy(() -> loader.loadRecording(record("k/a.wav")))
                .isInstanceOf(RecordingNotReadyException.class);
    }

    @Test
    void loadsLocalRelativeFile() throws Exception
    {
        Files.write(tempDir.resolve("a.wav"), wav(1, 16000, 320));

        QualityAudioLoader.LoadedAudio loaded = loader.loadRecording(record("a.wav"));

        assertThat(loaded.data).hasSize(364);
        assertThat(loaded.fileName).isEqualTo("a.wav");
    }

    @Test
    void missingLocalFileThrows()
    {
        ReflectionTestUtils.setField(loader, "readyTimeoutSeconds", 0L);

        assertThatThrownBy(() -> loader.loadRecording(record("missing.wav")))
                .isInstanceOf(RecordingNotReadyException.class);
    }

    @Test
    void nullRecordFileThrows()
    {
        assertThatThrownBy(() -> loader.loadRecording(record(null)))
                .isInstanceOf(RecordingNotReadyException.class);
    }

    @Test
    void stripsRecordingsDirPlaceholder() throws Exception
    {
        Files.write(tempDir.resolve("a.wav"), wav(1, 16000, 32));

        QualityAudioLoader.LoadedAudio loaded = loader.loadRecording(record("$${recordings_dir}/a.wav"));

        assertThat(loaded.fileName).isEqualTo("a.wav");
    }

    @Test
    void absolutePathUsedDirectly() throws Exception
    {
        Path absolute = tempDir.resolve("abs.wav");
        Files.write(absolute, wav(1, 8000, 32));

        QualityAudioLoader.LoadedAudio loaded = loader.loadRecording(record(absolute.toString()));

        assertThat(loaded.fileName).isEqualTo("abs.wav");
    }

    @Test
    void transcribeUsesExplicitEngineAndTrims() throws Exception
    {
        ReflectionTestUtils.setField(loader, "qualityAsrEngine", "mock");
        when(voiceEngineManager.transcribe(eq(VoiceModelEnum.MOCK), any(), eq("wav"), eq(16000), isNull()))
                .thenReturn("  转写文本  ");

        String text = loader.transcribeAudio(record("a.wav"), loaded("a.wav", wav(1, 16000, 32)));

        assertThat(text).isEqualTo("转写文本");
    }

    @Test
    void transcribeUsesDefaultEngineWhenOverrideEmpty() throws Exception
    {
        ReflectionTestUtils.setField(loader, "qualityAsrEngine", "");
        when(voiceEngineManager.defaultEngine()).thenReturn(VoiceModelEnum.DASHSCOPE);
        when(voiceEngineManager.transcribe(eq(VoiceModelEnum.DASHSCOPE), any(), anyString(), anyInt(), isNull()))
                .thenReturn("文本");

        String text = loader.transcribeAudio(record("a.mp3"), loaded("a.mp3", new byte[] { 1 }));

        assertThat(text).isEqualTo("文本");
        verify(voiceEngineManager).transcribe(eq(VoiceModelEnum.DASHSCOPE), any(), eq("mp3"), eq(16000),
                isNull());
    }

    @Test
    void transcribeNullResultBecomesEmpty() throws Exception
    {
        ReflectionTestUtils.setField(loader, "qualityAsrEngine", "mock");
        when(voiceEngineManager.transcribe(any(), any(), anyString(), anyInt(), isNull()))
                .thenReturn(null);

        assertThat(loader.transcribeAudio(record("a.wav"), loaded("a.wav", new byte[] { 1 }))).isEmpty();
    }

    @Test
    void analyzeNonWavReturnsNull()
    {
        assertThat(loader.analyzeAcoustic(loaded("a.mp3", new byte[] { 1 }), "文本")).isNull();
    }

    @Test
    void analyzeValidWavDelegates() throws Exception
    {
        AcousticFeatureAnalyzer.AcousticResult expected = new AcousticFeatureAnalyzer.AcousticResult(
                Collections.emptyList(), 0, 100d, 0L, 0L, 1);
        when(acousticFeatureAnalyzer.analyze(eq(16000), any(byte[].class), eq("文本")))
                .thenReturn(expected);

        AcousticFeatureAnalyzer.AcousticResult result = loader.analyzeAcoustic(
                loaded("a.wav", wav(1, 16000, 320)), "文本");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void analyzeInvalidWavReturnsNull()
    {
        assertThat(loader.analyzeAcoustic(loaded("a.wav", "not-a-wav".getBytes(StandardCharsets.UTF_8)),
                "文本")).isNull();
    }

    private QualityAudioLoader.LoadedAudio loaded(String fileName, byte[] data)
    {
        return new QualityAudioLoader.LoadedAudio(data, fileName);
    }

    private AiCallRecord record(String recordFile)
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(100L);
        record.setRecordFile(recordFile);
        return record;
    }

    /** 生成 PCM16 单/双声道 WAV 字节 */
    private byte[] wav(int channels, int sampleRate, int dataBytes)
    {
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataBytes);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * channels * 2);
        buffer.putShort((short) (channels * 2));
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataBytes);
        buffer.put(new byte[dataBytes]);
        return buffer.array();
    }
}
