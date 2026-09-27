package ai.lawyers.system.service.lawyers.voice.robot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.rag.RagChunk;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * {@link VoiceRobotService} P3-E3 测试：
 * RAG 装配进 prompt、降级链（开关关闭/RAG 异常/LLM 异常/空答复）、
 * 长度截断、溯源清单、scene=voice_robot 成本归因、任何路径不外抛。
 *
 * @author ai-lawyers
 */
class VoiceRobotServiceTest
{
    private RagSearchService ragSearchService;

    private IAiModelConfigService modelConfigService;

    private VoiceRobotService service;

    @BeforeEach
    void setUp() throws Exception
    {
        ragSearchService = mock(RagSearchService.class);
        modelConfigService = mock(IAiModelConfigService.class);
        service = new VoiceRobotService();
        inject("ragSearchService", ragSearchService);
        inject("modelConfigService", modelConfigService);
        service.setEnabled(true);
    }

    private void inject(String field, Object value) throws Exception
    {
        Field f = VoiceRobotService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(service, value);
    }

    private static RagChunk chunk(String title, String lawArticle, String content)
    {
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setTitle(title);
        c.setLawArticle(lawArticle);
        c.setChunkContent(content);
        return new RagChunk(c, 1.0d);
    }

    @Test
    void answer_withRagHits_assemblesPromptAndSources()
    {
        List<RagChunk> hits = new ArrayList<>();
        hits.add(chunk("劳动合同法", "第四十六条", "用人单位应当向劳动者支付经济补偿的情形……"));
        when(ragSearchService.search(eq("被辞退怎么赔偿"), any())).thenReturn(hits);
        when(modelConfigService.chat(anyString(), anyString(), anyString())).thenReturn("您可以主张经济补偿。");

        VoiceRobotResult r = service.answer("被辞退怎么赔偿", "sess-1");

        assertThat(r.isDegraded()).isFalse();
        assertThat(r.getAnswer()).isEqualTo("您可以主张经济补偿。");
        assertThat(r.getRagHits()).isEqualTo(1);
        assertThat(r.getSources()).containsExactly("劳动合同法 第四十六条");
        // prompt：检索依据与问题均进入 userMessage，scene=voice_robot
        ArgumentCaptor<String> userCap = ArgumentCaptor.forClass(String.class);
        verify(modelConfigService).chat(eq(VoiceRobotService.SYSTEM_PROMPT), userCap.capture(),
                eq(AiModelCallLogRecorder.SCENE_VOICE_ROBOT));
        assertThat(userCap.getValue()).contains("劳动合同法").contains("第四十六条")
                .contains("经济补偿").contains("被辞退怎么赔偿");
    }

    @Test
    void answer_ragEmpty_stillCallsLlmWithNoHitHint()
    {
        when(ragSearchService.search(anyString(), any())).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString())).thenReturn("建议转人工坐席咨询。");

        VoiceRobotResult r = service.answer("稀有法律问题", "sess-2");

        assertThat(r.isDegraded()).isFalse();
        assertThat(r.getRagHits()).isZero();
        assertThat(r.getSources()).isEmpty();
        ArgumentCaptor<String> userCap = ArgumentCaptor.forClass(String.class);
        verify(modelConfigService).chat(anyString(), userCap.capture(), anyString());
        assertThat(userCap.getValue()).contains("未检索到");
    }

    @Test
    void answer_disabled_returnsFallbackWithoutCallingLlm()
    {
        service.setEnabled(false);

        VoiceRobotResult r = service.answer("你好", "sess-3");

        assertThat(r.isDegraded()).isTrue();
        assertThat(r.getFallbackReason()).isEqualTo("robot_disabled");
        assertThat(r.getAnswer()).contains("转接人工坐席");
        verify(modelConfigService, never()).chat(anyString(), anyString(), anyString());
        verify(ragSearchService, never()).search(anyString(), anyList());
    }

    @Test
    void answer_llmThrows_fallbackAndNeverThrows()
    {
        when(ragSearchService.search(anyString(), any())).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("model timeout"));

        VoiceRobotResult r = service.answer("问题", "sess-4");

        assertThat(r.isDegraded()).isTrue();
        assertThat(r.getFallbackReason()).isEqualTo("llm_error");
        assertThat(r.getAnswer()).isEqualTo(VoiceRobotService.FALLBACK_ANSWER);
    }

    @Test
    void answer_ragThrows_degradesRagButStillAnswers()
    {
        when(ragSearchService.search(anyString(), any())).thenThrow(new RuntimeException("chunk table missing"));
        when(modelConfigService.chat(anyString(), anyString(), anyString())).thenReturn("通用回答");

        VoiceRobotResult r = service.answer("问题", "sess-5");

        // RAG 异常按无命中继续（可用性兜底语义），不应整体失败
        assertThat(r.isDegraded()).isFalse();
        assertThat(r.getRagHits()).isZero();
        assertThat(r.getAnswer()).isEqualTo("通用回答");
    }

    @Test
    void answer_llmEmpty_returnsFallback()
    {
        when(ragSearchService.search(anyString(), any())).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString())).thenReturn("  ");

        VoiceRobotResult r = service.answer("问题", "sess-6");

        assertThat(r.isDegraded()).isTrue();
        assertThat(r.getFallbackReason()).isEqualTo("llm_empty");
    }

    @Test
    void answer_emptyQuestion_noCall()
    {
        VoiceRobotResult r = service.answer("   ", "sess-7");

        assertThat(r.isDegraded()).isTrue();
        assertThat(r.getFallbackReason()).isEqualTo("empty_question");
        verify(modelConfigService, never()).chat(anyString(), anyString(), anyString());
    }

    @Test
    void answer_overLongAnswer_truncatedToMaxChars() throws Exception
    {
        inject("answerMaxChars", 10);
        when(ragSearchService.search(anyString(), any())).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString()))
                .thenReturn("一二三四五六七八九十十一十二十三十四");

        VoiceRobotResult r = service.answer("问题", "sess-8");

        assertThat(r.getAnswer()).isEqualTo("一二三四五六七八九十");
    }
}
