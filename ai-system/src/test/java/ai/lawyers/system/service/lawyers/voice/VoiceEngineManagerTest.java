package ai.lawyers.system.service.lawyers.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * VoiceEngineManager 单测：默认引擎解析、TTS/ASR 按引擎选择、
 * 空结果归一化、异常降级 Mock 与 ASR 指标记录。
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class VoiceEngineManagerTest
{
    @Mock
    private DashScopeTtsEngine dashScopeTtsEngine;

    @Mock
    private OpenAiCompatibleAsrEngine openAiCompatibleAsrEngine;

    @Mock
    private MockVoiceEngine mockVoiceEngine;

    private VoiceProperties properties;
    private VoiceEngineManager manager;

    @BeforeEach
    void setUp()
    {
        properties = new VoiceProperties();
        properties.setEngine("dashscope");
        properties.setAsrBaseUrl("http://localhost:8000/v1");
        manager = new VoiceEngineManager(properties, dashScopeTtsEngine,
                openAiCompatibleAsrEngine, mockVoiceEngine);
    }

    @Test
    void defaultEngineResolvedFromProperties()
    {
        assertThat(manager.defaultEngine()).isEqualTo(VoiceModelEnum.DASHSCOPE);

        properties.setEngine("unknown");
        assertThat(manager.defaultEngine()).isEqualTo(VoiceModelEnum.MOCK);

        properties.setEngine(null);
        assertThat(manager.defaultEngine()).isEqualTo(VoiceModelEnum.MOCK);
    }

    @Test
    void dashscopeSynthesizeDelegates()
    {
        byte[] audio = { 1, 2 };
        when(dashScopeTtsEngine.synthesize("hi", "wav", 16000, null)).thenReturn(audio);

        assertThat(manager.synthesize(VoiceModelEnum.DASHSCOPE, "hi", "wav", 16000, null))
                .isEqualTo(audio);
    }

    @Test
    void nullSynthesisResultBecomesEmptyArray()
    {
        when(dashScopeTtsEngine.synthesize(anyString(), anyString(), anyInt(), any()))
                .thenReturn(null);

        assertThat(manager.synthesize(VoiceModelEnum.DASHSCOPE, "hi", "wav", 16000, null)).isEmpty();
    }

    @Test
    void synthesisFailureFallsBackToMock()
    {
        when(dashScopeTtsEngine.synthesize(anyString(), anyString(), anyInt(), any()))
                .thenThrow(new RuntimeException("tts down"));
        byte[] fallback = { 9 };
        when(mockVoiceEngine.synthesize("hi", "wav", 16000, null)).thenReturn(fallback);

        assertThat(manager.synthesize(VoiceModelEnum.DASHSCOPE, "hi", "wav", 16000, null))
                .isEqualTo(fallback);
    }

    @Test
    void nonDashScopeEngineUsesMockForTts()
    {
        when(mockVoiceEngine.synthesize(anyString(), anyString(), anyInt(), any()))
                .thenReturn(new byte[] { 3 });

        manager.synthesize(VoiceModelEnum.ALI, "hi", "wav", 8000, new HashMap<>());

        verify(mockVoiceEngine).synthesize("hi", "wav", 8000, new HashMap<>());
        verifyNoInteractions(dashScopeTtsEngine);
    }

    @Test
    void transcribeUsesOpenAiCompatibleWhenBaseUrlConfigured()
    {
        HotlineMetrics metrics = mock(HotlineMetrics.class);
        ReflectionTestUtils.setField(manager, "metrics", metrics);
        when(openAiCompatibleAsrEngine.transcribe(any(), eq("wav"), eq(16000), anyMap()))
                .thenReturn("你好");

        String text = manager.transcribe(VoiceModelEnum.DASHSCOPE, new byte[] { 1 }, "wav", 16000,
                new HashMap<>());

        assertThat(text).isEqualTo("你好");
        verify(metrics).incrementAi("asr", "success");
        verify(metrics).recordAiFirstResponse(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void nullTranscriptionResultBecomesEmptyString()
    {
        when(openAiCompatibleAsrEngine.transcribe(any(), anyString(), anyInt(), any()))
                .thenReturn(null);

        assertThat(manager.transcribe(VoiceModelEnum.DASHSCOPE, new byte[] { 1 }, "wav", 16000,
                new HashMap<>())).isEmpty();
    }

    @Test
    void transcribeFallsBackToMockWhenBaseUrlMissing()
    {
        properties.setAsrBaseUrl("");
        when(mockVoiceEngine.transcribe(any(), anyString(), anyInt(), any()))
                .thenReturn("mock 文本");

        String text = manager.transcribe(VoiceModelEnum.DASHSCOPE, new byte[] { 1 }, "wav", 16000,
                new HashMap<>());

        assertThat(text).isEqualTo("mock 文本");
        verifyNoInteractions(openAiCompatibleAsrEngine);
    }

    @Test
    void transcribeWithMockEngineUsesMockDirectly()
    {
        when(mockVoiceEngine.transcribe(any(), anyString(), anyInt(), any()))
                .thenReturn("mock 文本");

        manager.transcribe(VoiceModelEnum.MOCK, new byte[] { 1 }, "wav", 16000, null);

        verify(mockVoiceEngine).transcribe(any(), eq("wav"), eq(16000), isNull());
        verifyNoInteractions(openAiCompatibleAsrEngine);
    }

    @Test
    void transcribeFailureFallsBackAndRecordsFallbackMetric()
    {
        HotlineMetrics metrics = mock(HotlineMetrics.class);
        ReflectionTestUtils.setField(manager, "metrics", metrics);
        when(openAiCompatibleAsrEngine.transcribe(any(), anyString(), anyInt(), any()))
                .thenThrow(new RuntimeException("asr down"));
        when(mockVoiceEngine.transcribe(any(), anyString(), anyInt(), any()))
                .thenReturn("降级文本");

        String text = manager.transcribe(VoiceModelEnum.DASHSCOPE, new byte[] { 1 }, "wav", 16000,
                new HashMap<>());

        assertThat(text).isEqualTo("降级文本");
        verify(metrics).incrementAi("asr", "fallback");
    }

    @Test
    void transcribeWithoutMetricsDoesNotFail()
    {
        when(openAiCompatibleAsrEngine.transcribe(any(), anyString(), anyInt(), any()))
                .thenReturn("文本");

        assertThat(manager.transcribe(VoiceModelEnum.DASHSCOPE, new byte[] { 1 }, "wav", 16000, null))
                .isEqualTo("文本");
    }

    @Test
    void synthesizeWithoutMetricsDoesNotFail()
    {
        when(dashScopeTtsEngine.synthesize(anyString(), anyString(), anyInt(), any()))
                .thenReturn(new byte[] { 1 });

        assertThat(manager.synthesize(VoiceModelEnum.DASHSCOPE, "hi", "wav", 8000, null))
                .hasSize(1);
    }
}
