package ai.lawyers.system.service.lawyers.voice.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.rag.RagChunk;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * F4：{@link CopilotAssistService} 单元测试。
 *
 * <p>不启 Spring：Mockito 打桩模型/RAG/工单 Mapper/指标，ReflectionTestUtils 注入私有字段；
 * 覆盖正常编排、要素降级、检索异常、短语截断、sessionId 解析、开关与空文本。</p>
 *
 * @author ai-lawyers
 */
class CopilotAssistServiceTest
{
    private IAiModelConfigService modelConfigService;

    private RagSearchService ragSearchService;

    private AiCallTicketMapper ticketMapper;

    private HotlineMetrics metrics;

    private CopilotAssistService service;

    @BeforeEach
    void setUp()
    {
        modelConfigService = Mockito.mock(IAiModelConfigService.class);
        ragSearchService = Mockito.mock(RagSearchService.class);
        ticketMapper = Mockito.mock(AiCallTicketMapper.class);
        metrics = Mockito.mock(HotlineMetrics.class);
        service = new CopilotAssistService();
        service.setEnabled(true);
        ReflectionTestUtils.setField(service, "lawLimit", 3);
        ReflectionTestUtils.setField(service, "ticketLimit", 3);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
        ReflectionTestUtils.setField(service, "ragSearchService", ragSearchService);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "metrics", metrics);
    }

    private static final String TEXT = "我们公司从八月份开始拖欠工资，一共两个月了";

    private static final String JSON =
            "{\"disputeType\":\"劳动争议\",\"claims\":[\"支付拖欠工资\"],\"urgency\":\"normal\","
            + "\"keyFacts\":[\"2026年8月入职\",\"拖欠两个月\"]}";

    private RagChunk chunk(long id, String title, String article)
    {
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setChunkId(id);
        c.setTitle(title);
        c.setLawArticle(article);
        c.setSource("全国人大常委会");
        return new RagChunk(c, 1.0d);
    }

    private AiCallTicket ticket(long id, String no)
    {
        AiCallTicket t = new AiCallTicket();
        t.setTicketId(id);
        t.setTicketNo(no);
        t.setTitle("历史工资拖欠工单");
        t.setStatus("2");
        t.setContent("此前同类咨询处理内容");
        return t;
    }

    private void stubModel(String json)
    {
        when(modelConfigService.chatJson(anyString(), anyString(),
                eq(AiModelCallLogRecorder.SCENE_EXTRACT))).thenReturn(json);
    }

    // ---------- 开关 / 空文本 ----------

    @Test
    void assist_disabled_returnsEmptyAndNoInteractions()
    {
        service.setEnabled(false);
        CopilotAssistResult r = service.assist(TEXT, null);
        assertThat(r.getDisputeType()).isEmpty();
        assertThat(r.getLaws()).isEmpty();
        assertThat(r.getTickets()).isEmpty();
        verifyNoInteractions(modelConfigService, ragSearchService, ticketMapper, metrics);
    }

    @Test
    void assist_blankText_returnsEmpty()
    {
        CopilotAssistResult r = service.assist("   ", null);
        assertThat(r.isElementDegraded()).isFalse();
        verifyNoInteractions(modelConfigService, ragSearchService, ticketMapper);
    }

    // ---------- 正常编排 ----------

    @Test
    void assist_happyPath_elementsLawsTicketsAndMetrics()
    {
        // 模型在 JSON 外附加说明，cleanJson 应截取最外层 {}
        stubModel("好的，结果如下：" + JSON + " 以上。");
        when(ragSearchService.search(anyString(), isNull(List.class)))
                .thenReturn(Arrays.asList(chunk(11L, "劳动合同法", "第三十条"),
                        chunk(12L, "劳动法", "第五十条"),
                        chunk(13L, "工资支付暂行规定", "第七条"),
                        chunk(14L, "应被截断", "第X条")));
        when(ticketMapper.selectSimilarTickets(eq("劳动争议"), eq("支付拖欠工资"),
                isNull(Long.class), eq(3)))
                .thenReturn(Arrays.asList(ticket(7L, "GD2026001")));

        CopilotAssistResult r = service.assist(TEXT, null);

        assertThat(r.isElementDegraded()).isFalse();
        assertThat(r.getDisputeType()).isEqualTo("劳动争议");
        assertThat(r.getClaims()).containsExactly("支付拖欠工资");
        assertThat(r.getUrgency()).isEqualTo("normal");
        assertThat(r.getKeyFacts()).hasSize(2);
        // lawLimit=3：第 4 条截断
        assertThat(r.getLaws()).hasSize(3);
        assertThat(r.getLaws().get(0).getChunkId()).isEqualTo(11L);
        assertThat(r.getLaws().get(0).getLawArticle()).isEqualTo("第三十条");
        // 相似工单
        assertThat(r.getTickets()).hasSize(1);
        assertThat(r.getTickets().get(0).getTicketId()).isEqualTo(7L);
        assertThat(r.getTickets().get(0).getStatus()).isEqualTo("2");
        verify(metrics).incrementCopilotAssist();
    }

    @Test
    void assist_urgentUppercase_normalizedToUrgent()
    {
        stubModel("{\"disputeType\":\"家暴\",\"claims\":[\"申请人身保护\"],\"urgency\":\"URGENT\","
                + "\"keyFacts\":[]}");
        when(ragSearchService.search(anyString(), isNull(List.class)))
                .thenReturn(new ArrayList<RagChunk>());
        when(ticketMapper.selectSimilarTickets(anyString(), anyString(),
                org.mockito.ArgumentMatchers.<Long>isNull(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ArrayList<AiCallTicket>());

        CopilotAssistResult r = service.assist("他正在打我", null);
        assertThat(r.getUrgency()).isEqualTo("urgent");
    }

    // ---------- 降级 ----------

    @Test
    void assist_modelThrows_elementDegradedButLawsStillWork()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("模型超时"));
        when(ragSearchService.search(anyString(), isNull(List.class)))
                .thenReturn(Arrays.asList(chunk(21L, "劳动合同法", "第三十条")));

        CopilotAssistResult r = service.assist(TEXT, null);

        assertThat(r.isElementDegraded()).isTrue();
        assertThat(r.getLaws()).hasSize(1);
        // 要素降级后无短语，不查相似工单
        verify(ticketMapper, never()).selectSimilarTickets(
                anyString(), org.mockito.ArgumentMatchers.<String>isNull(),
                org.mockito.ArgumentMatchers.<Long>isNull(), org.mockito.ArgumentMatchers.anyInt());
        // 指标仍计数（best-effort 不因模型失败而漏统计调用量）
        verify(metrics).incrementCopilotAssist();
    }

    @Test
    void assist_modelGarbageJson_elementDegraded()
    {
        stubModel("这不是JSON");
        CopilotAssistResult r = service.assist(TEXT, null);
        assertThat(r.isElementDegraded()).isTrue();
    }

    @Test
    void assist_modelServiceNotWired_elementDegradedNoThrow()
    {
        ReflectionTestUtils.setField(service, "modelConfigService", null);
        CopilotAssistResult r = service.assist(TEXT, null);
        assertThat(r.isElementDegraded()).isTrue();
    }

    @Test
    void assist_ragThrows_lawsEmptyAndNoPropagation()
    {
        stubModel(JSON);
        when(ragSearchService.search(anyString(), isNull(List.class)))
                .thenThrow(new RuntimeException("检索失败"));
        when(ticketMapper.selectSimilarTickets(anyString(), anyString(),
                org.mockito.ArgumentMatchers.<Long>isNull(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ArrayList<AiCallTicket>());

        CopilotAssistResult r = service.assist(TEXT, null);
        assertThat(r.getLaws()).isEmpty();
        assertThat(r.isElementDegraded()).isFalse();
        assertThat(r.getTickets()).isEmpty();
    }

    @Test
    void assist_ragNotWired_lawsEmpty()
    {
        stubModel(JSON);
        ReflectionTestUtils.setField(service, "ragSearchService", null);
        CopilotAssistResult r = service.assist(TEXT, null);
        assertThat(r.getLaws()).isEmpty();
    }

    @Test
    void assist_ticketMapperThrows_noPropagation()
    {
        stubModel(JSON);
        when(ragSearchService.search(anyString(), isNull(List.class)))
                .thenReturn(new ArrayList<RagChunk>());
        when(ticketMapper.selectSimilarTickets(anyString(), anyString(),
                org.mockito.ArgumentMatchers.<Long>isNull(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new RuntimeException("SQL 异常"));

        CopilotAssistResult r = service.assist(TEXT, null);
        assertThat(r.getTickets()).isEmpty();
        assertThat(r.getLaws()).isEmpty();
    }

    // ---------- 短语截断 / sessionId 解析 ----------

    @Test
    void assist_longClaim_clippedToPhraseMax()
    {
        String longClaim = "要求公司立即支付拖欠的两个月工资并且支付经济补偿金";
        stubModel("{\"disputeType\":\"劳动争议\",\"claims\":[\"" + longClaim + "\"],"
                + "\"urgency\":\"normal\",\"keyFacts\":[]}");
        when(ragSearchService.search(anyString(), isNull(List.class)))
                .thenReturn(new ArrayList<RagChunk>());

        service.assist(TEXT, null);

        verify(ticketMapper).selectSimilarTickets(eq("劳动争议"),
                eq(longClaim.substring(0, 20)), isNull(Long.class), eq(3));
    }

    @Test
    void assist_numericSessionId_parsedAsExcludeRecordId()
    {
        stubModel(JSON);
        when(ragSearchService.search(anyString(), isNull(List.class)))
                .thenReturn(new ArrayList<RagChunk>());
        when(ticketMapper.selectSimilarTickets(anyString(), anyString(),
                eq(2000L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ArrayList<AiCallTicket>());

        CopilotAssistResult r = service.assist(TEXT, "2000");
        assertThat(r.getTickets()).isEmpty();
    }

    @Test
    void assist_nonNumericSessionId_excludeNull()
    {
        stubModel(JSON);
        when(ragSearchService.search(anyString(), isNull(List.class)))
                .thenReturn(new ArrayList<RagChunk>());
        when(ticketMapper.selectSimilarTickets(anyString(), anyString(),
                isNull(Long.class), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ArrayList<AiCallTicket>());

        service.assist(TEXT, "sess-uuid-1");
        verify(ticketMapper).selectSimilarTickets(anyString(), anyString(),
                isNull(Long.class), org.mockito.ArgumentMatchers.anyInt());
    }
}
