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
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.rag.KnowledgeQueryLogRecorder;
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

    private IAiCallRecordService callRecordService;

    private IAiCallTicketService callTicketService;

    private VoiceRobotService service;

    @BeforeEach
    void setUp() throws Exception
    {
        ragSearchService = mock(RagSearchService.class);
        modelConfigService = mock(IAiModelConfigService.class);
        callRecordService = mock(IAiCallRecordService.class);
        callTicketService = mock(IAiCallTicketService.class);
        service = new VoiceRobotService();
        inject("ragSearchService", ragSearchService);
        inject("modelConfigService", modelConfigService);
        inject("callRecordService", callRecordService);
        inject("callTicketService", callTicketService);
        inject("selfQueryEnabled", true);
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
        when(ragSearchService.search(eq("被辞退怎么赔偿"), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(hits);
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
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(Collections.emptyList());
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
        verify(ragSearchService, never()).search(anyString(), anyList(), anyString());
    }

    @Test
    void answer_llmThrows_fallbackAndNeverThrows()
    {
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(Collections.emptyList());
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
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenThrow(new RuntimeException("chunk table missing"));
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
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(Collections.emptyList());
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
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString()))
                .thenReturn("一二三四五六七八九十十一十二十三十四");

        VoiceRobotResult r = service.answer("问题", "sess-8");

        assertThat(r.getAnswer()).isEqualTo("一二三四五六七八九十");
    }

    /* ---- G6：机器人自助查询 ---- */

    private static AiCallRecord recordWithPhone(String phone)
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(1001L);
        record.setCallerNumber(phone);
        return record;
    }

    private static AiCallTicket ticket(String no, String status, String processContent)
    {
        AiCallTicket t = new AiCallTicket();
        t.setTicketNo(no);
        t.setStatus(status);
        t.setProcessContent(processContent);
        return t;
    }

    @Test
    void answer_ticketQueryIntent_returnsOwnTickets()
    {
        when(callRecordService.selectAiCallRecordByRecordId(1001L))
                .thenReturn(recordWithPhone("13800001234"));
        List<AiCallTicket> tickets = new ArrayList<>();
        tickets.add(ticket("GD20261009001", "1", "已转交值班律师"));
        when(callTicketService.selectAiCallTicketList(any(AiCallTicket.class))).thenReturn(tickets);

        VoiceRobotResult r = service.answer("帮我查一下我的工单进度", "sess-g1", 1001L);

        assertThat(r.getAction()).isEqualTo("query_ticket");
        assertThat(r.getTicketCount()).isEqualTo(1);
        assertThat(r.getTicketNos()).containsExactly("GD20261009001");
        assertThat(r.getAnswer()).contains("GD20261009001").contains("处理中").contains("已转交值班律师");
        // 命中自助查询不再调 LLM/RAG
        verify(modelConfigService, never()).chat(anyString(), anyString(), anyString());
    }

    @Test
    void answer_ticketQueryIntentButNoTicket_returnsEmptyHintWithoutLlm()
    {
        when(callRecordService.selectAiCallRecordByRecordId(1001L))
                .thenReturn(recordWithPhone("13800001234"));
        when(callTicketService.selectAiCallTicketList(any(AiCallTicket.class)))
                .thenReturn(Collections.emptyList());

        VoiceRobotResult r = service.answer("我的工单办理得怎么样了", "sess-g2", 1001L);

        assertThat(r.getAction()).isEqualTo("query_ticket");
        assertThat(r.getTicketCount()).isZero();
        assertThat(r.getAnswer()).contains("没有查到");
        verify(modelConfigService, never()).chat(anyString(), anyString(), anyString());
    }

    @Test
    void answer_notTicketIntent_fallsBackToRag()
    {
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString())).thenReturn("法律解答");

        VoiceRobotResult r = service.answer("借钱不还怎么办", "sess-g3", 1001L);

        assertThat(r.getAction()).isNull();
        assertThat(r.getAnswer()).isEqualTo("法律解答");
        verify(callRecordService, never()).selectAiCallRecordByRecordId(any());
    }

    @Test
    void answer_browserWithoutAnchor_neverQueriesTicketsEvenIfSessionIdNumeric()
    {
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString())).thenReturn("法律解答");

        // 浏览器侧 anchoredRecordId 恒为 null，即便问题命中意图也不得查库（防伪造越权）
        VoiceRobotResult r = service.answer("查我的工单进度", "1001", null);

        assertThat(r.getAction()).isNull();
        assertThat(r.getAnswer()).isEqualTo("法律解答");
        verify(callTicketService, never()).selectAiCallTicketList(any());
    }

    @Test
    void answer_recordServiceThrows_fallsBackToRagAndNeverThrows()
    {
        when(callRecordService.selectAiCallRecordByRecordId(1001L))
                .thenThrow(new RuntimeException("db down"));
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString())).thenReturn("降级解答");

        VoiceRobotResult r = service.answer("查工单进度", "sess-g4", 1001L);

        assertThat(r.getAction()).isNull();
        assertThat(r.getAnswer()).isEqualTo("降级解答");
    }

    @Test
    void answer_selfQueryDisabled_fallsBackToRag() throws Exception
    {
        inject("selfQueryEnabled", false);
        when(ragSearchService.search(anyString(), any(),
                eq(KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT))).thenReturn(Collections.emptyList());
        when(modelConfigService.chat(anyString(), anyString(), anyString())).thenReturn("法律解答");

        VoiceRobotResult r = service.answer("查工单进度", "sess-g5", 1001L);

        assertThat(r.getAction()).isNull();
        assertThat(r.getAnswer()).isEqualTo("法律解答");
        verify(callRecordService, never()).selectAiCallRecordByRecordId(any());
    }

    @Test
    void answer_multipleTickets_reportsCountAndLimitsSpeech() throws Exception
    {
        inject("selfQueryMaxTickets", 2);
        when(callRecordService.selectAiCallRecordByRecordId(1001L))
                .thenReturn(recordWithPhone("13800001234"));
        List<AiCallTicket> tickets = new ArrayList<>();
        tickets.add(ticket("NO-1", "0", null));
        tickets.add(ticket("NO-2", "1", null));
        tickets.add(ticket("NO-3", "2", null));
        when(callTicketService.selectAiCallTicketList(any(AiCallTicket.class))).thenReturn(tickets);

        VoiceRobotResult r = service.answer("查工单", "sess-g6", 1001L);

        assertThat(r.getTicketCount()).isEqualTo(2);
        assertThat(r.getTicketNos()).containsExactly("NO-1", "NO-2");
        assertThat(r.getAnswer()).contains("NO-1").contains("NO-2").contains("另有1条工单");
    }

    @Test
    void ticketQueryIntent_rules()
    {
        assertThat(VoiceRobotService.isTicketQueryIntent("查一下我的工单进度")).isTrue();
        assertThat(VoiceRobotService.isTicketQueryIntent("我之前的工单办理得怎么样了")).isTrue();
        assertThat(VoiceRobotService.isTicketQueryIntent("借钱不还怎么办")).isFalse();
        assertThat(VoiceRobotService.isTicketQueryIntent("办理居住证需要什么材料")).isFalse();
    }
}
