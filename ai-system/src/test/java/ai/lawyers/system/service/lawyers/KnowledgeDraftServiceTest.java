package ai.lawyers.system.service.lawyers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityInspectionMapper;
import ai.lawyers.system.service.lawyers.rag.KnowledgeQueryTextUtils;

/**
 * P1-10：{@link KnowledgeDraftService} 知识草稿自动沉淀测试。
 *
 * <p>覆盖通话/工单/未命中问句三条来源的入参与去重校验、模型不可用与返回异常、
 * 标题正文裁剪、待审核字段与审计备注组装（JSON 包裹清洗、非法 JSON 报错）、
 * 质检高分扫描的参数收敛与批量跳过逻辑。</p>
 *
 * @author ai-lawyers
 */
class KnowledgeDraftServiceTest
{
    private static final String DRAFT_JSON =
            "{\"title\":\"拖欠工资维权\",\"content\":\"可向劳动监察投诉或申请仲裁\","
            + "\"keywords\":\"欠薪,劳动仲裁\",\"lawArticle\":\"劳动合同法第八十五条\"}";

    private AiLegalKnowledgeMapper knowledgeMapper;

    private AiCallRecordMapper callRecordMapper;

    private AiCallTicketMapper ticketMapper;

    private AiQualityInspectionMapper inspectionMapper;

    private IAiModelConfigService modelConfigService;

    private KnowledgeDraftService service;

    @BeforeEach
    void setUp()
    {
        knowledgeMapper = mock(AiLegalKnowledgeMapper.class);
        callRecordMapper = mock(AiCallRecordMapper.class);
        ticketMapper = mock(AiCallTicketMapper.class);
        inspectionMapper = mock(AiQualityInspectionMapper.class);
        modelConfigService = mock(IAiModelConfigService.class);
        // 落库后回填主键，模拟 MyBatis useGeneratedKeys
        doAnswer(invocation ->
        {
            ((AiLegalKnowledge) invocation.getArgument(0)).setKnowledgeId(99L);
            return 1;
        }).when(knowledgeMapper).insertAiLegalKnowledge(any(AiLegalKnowledge.class));

        service = new KnowledgeDraftService();
        ReflectionTestUtils.setField(service, "knowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(service, "callRecordMapper", callRecordMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "inspectionMapper", inspectionMapper);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
    }

    // ---------- 通话来源 ----------

    @Test
    void draftFromCallRejectsMissingArguments()
    {
        assertThatThrownBy(() -> service.draftFromCall(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("通话记录ID");

        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.draftFromCall(1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不存在");
    }

    @Test
    void draftFromCallRequiresMaterial()
    {
        AiCallRecord empty = new AiCallRecord();
        empty.setContent(" ");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(empty);

        assertThatThrownBy(() -> service.draftFromCall(1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("尚无转写");

        verifyNoInteractions(modelConfigService);
    }

    @Test
    void draftFromCallPersistsPendingKnowledge() throws Exception
    {
        AiCallRecord record = new AiCallRecord();
        record.setTranscript("客户咨询拖欠工资");
        record.setContent("原始咨询内容");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
                .thenReturn("模型输出：\n" + DRAFT_JSON + "\n以上");

        Long id = service.draftFromCall(1L);

        assertThat(id).isEqualTo(99L);
        ArgumentCaptor<AiLegalKnowledge> saved = ArgumentCaptor.forClass(AiLegalKnowledge.class);
        verify(knowledgeMapper).insertAiLegalKnowledge(saved.capture());
        AiLegalKnowledge knowledge = saved.getValue();
        assertThat(knowledge.getTitle()).isEqualTo("拖欠工资维权");
        assertThat(knowledge.getContent()).isEqualTo("可向劳动监察投诉或申请仲裁");
        assertThat(knowledge.getKeywords()).isEqualTo("欠薪,劳动仲裁");
        assertThat(knowledge.getLawArticle()).isEqualTo("劳动合同法第八十五条");
        assertThat(knowledge.getSource()).isEqualTo("AI沉淀-通话");
        assertThat(knowledge.getSourceUrl()).isEqualTo("source://call/1");
        assertThat(knowledge.getStatus()).isEqualTo("0");
        assertThat(knowledge.getAuditStatus()).isEqualTo("0");
        assertThat(knowledge.getCreateBy()).isEqualTo("ai-draft");
        assertThat(knowledge.getAuditRemark()).contains("通话 1").contains("原始咨询：原始咨询内容");
    }

    @Test
    void draftFromCallRejectsDuplicateSource()
    {
        AiCallRecord record = new AiCallRecord();
        record.setTranscript("客户咨询拖欠工资");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);
        when(knowledgeMapper.countBySourceUrl("source://call/1")).thenReturn(1);

        assertThatThrownBy(() -> service.draftFromCall(1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已沉淀");

        verifyNoInteractions(modelConfigService);
    }

    @Test
    void draftRequiresModelService()
    {
        ReflectionTestUtils.setField(service, "modelConfigService", null);
        AiCallRecord record = new AiCallRecord();
        record.setTranscript("客户咨询拖欠工资");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);

        assertThatThrownBy(() -> service.draftFromCall(1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("模型配置服务未装配");
    }

    @Test
    void draftRejectsEmptyModelFields() throws Exception
    {
        AiCallRecord record = new AiCallRecord();
        record.setTranscript("客户咨询拖欠工资");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
                .thenReturn("{\"title\":\"\",\"content\":\"正文\"}");

        assertThatThrownBy(() -> service.draftFromCall(1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("标题或内容为空");

        verify(knowledgeMapper, never()).insertAiLegalKnowledge(any());
    }

    @Test
    void draftWrapsInvalidJson() throws Exception
    {
        AiCallRecord record = new AiCallRecord();
        record.setTranscript("客户咨询拖欠工资");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString())).thenReturn("完全不是JSON");

        assertThatThrownBy(() -> service.draftFromCall(1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("知识草稿生成失败");
    }

    @Test
    void draftRethrowsRuntimeExceptionFromModel() throws Exception
    {
        AiCallRecord record = new AiCallRecord();
        record.setTranscript("客户咨询拖欠工资");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("额度不足"));

        assertThatThrownBy(() -> service.draftFromCall(1L))
                .isInstanceOf(IllegalStateException.class).hasMessage("额度不足");
    }

    @Test
    void draftClipsOverlongModelFields() throws Exception
    {
        AiCallRecord record = new AiCallRecord();
        record.setTranscript("客户咨询拖欠工资");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString()))
                .thenReturn("{\"title\":\"" + repeat('t', 210) + "\",\"content\":\"正文\","
                        + "\"keywords\":\"" + repeat('k', 600) + "\",\"lawArticle\":\"" + repeat('l', 250) + "\"}");

        service.draftFromCall(1L);

        ArgumentCaptor<AiLegalKnowledge> saved = ArgumentCaptor.forClass(AiLegalKnowledge.class);
        verify(knowledgeMapper).insertAiLegalKnowledge(saved.capture());
        assertThat(saved.getValue().getTitle()).hasSize(200);
        assertThat(saved.getValue().getKeywords()).hasSize(500);
        assertThat(saved.getValue().getLawArticle()).hasSize(200);
    }

    // ---------- 工单来源 ----------

    @Test
    void draftFromTicketRejectsMissingArguments()
    {
        assertThatThrownBy(() -> service.draftFromTicket(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("工单ID");

        when(ticketMapper.selectAiCallTicketByTicketId(2L)).thenReturn(null);
        assertThatThrownBy(() -> service.draftFromTicket(2L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不存在");
    }

    @Test
    void draftFromTicketRequiresContent()
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTitle(" ");
        ticket.setContent(" ");
        when(ticketMapper.selectAiCallTicketByTicketId(2L)).thenReturn(ticket);

        assertThatThrownBy(() -> service.draftFromTicket(2L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("无内容");
    }

    @Test
    void draftFromTicketJoinsTitleContentAndProcess() throws Exception
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTitle("欠薪求助");
        ticket.setContent("公司拖欠三个月工资");
        ticket.setProcessContent("已建议申请仲裁");
        when(ticketMapper.selectAiCallTicketByTicketId(2L)).thenReturn(ticket);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString())).thenReturn(DRAFT_JSON);

        service.draftFromTicket(2L);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(modelConfigService).chatJson(anyString(), prompt.capture(), anyString());
        assertThat(prompt.getValue())
                .contains("欠薪求助。")
                .contains("公司拖欠三个月工资")
                .contains("处理内容：已建议申请仲裁");

        ArgumentCaptor<AiLegalKnowledge> saved = ArgumentCaptor.forClass(AiLegalKnowledge.class);
        verify(knowledgeMapper).insertAiLegalKnowledge(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo("AI沉淀-工单");
        assertThat(saved.getValue().getSourceUrl()).isEqualTo("source://ticket/2");
        assertThat(saved.getValue().getAuditRemark()).doesNotContain("原始咨询");
    }

    // ---------- 未命中问句 ----------

    @Test
    void draftFromQueryRejectsBlankQuestion()
    {
        assertThatThrownBy(() -> service.draftFromQuery(null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("问句不能为空");
        assertThatThrownBy(() -> service.draftFromQuery("   ", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("问句不能为空");
    }

    @Test
    void draftFromQueryTruncatesAndComputesHash() throws Exception
    {
        when(modelConfigService.chatJson(anyString(), anyString(), anyString())).thenReturn(DRAFT_JSON);

        service.draftFromQuery(repeat('q', 600), null);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(modelConfigService).chatJson(anyString(), prompt.capture(), anyString());
        assertThat(prompt.getValue()).contains("原始材料：\n" + repeat('q', KnowledgeQueryTextUtils.MAX_STORE_LENGTH));

        ArgumentCaptor<AiLegalKnowledge> saved = ArgumentCaptor.forClass(AiLegalKnowledge.class);
        verify(knowledgeMapper).insertAiLegalKnowledge(saved.capture());
        assertThat(saved.getValue().getSourceUrl())
                .isEqualTo("source://query/" + KnowledgeQueryTextUtils.hash(repeat('q', KnowledgeQueryTextUtils.MAX_STORE_LENGTH)));
        assertThat(saved.getValue().getSource()).isEqualTo("AI沉淀-未命中问句");
    }

    @Test
    void draftFromQueryUsesProvidedHash()
    {
        when(modelConfigService.chatJson(anyString(), anyString(), anyString())).thenReturn(DRAFT_JSON);

        service.draftFromQuery("试用期辞退有补偿吗", " abc123 ");

        ArgumentCaptor<AiLegalKnowledge> saved = ArgumentCaptor.forClass(AiLegalKnowledge.class);
        verify(knowledgeMapper).insertAiLegalKnowledge(saved.capture());
        assertThat(saved.getValue().getSourceUrl()).isEqualTo("source://query/abc123");
    }

    @Test
    void draftFromQueryRejectsPunctuationOnlyQuestion()
    {
        assertThatThrownBy(() -> service.draftFromQuery("？!，。", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("有效内容为空");

        verifyNoInteractions(modelConfigService);
    }

    // ---------- 优质通话扫描 ----------

    @Test
    void scanQualityCallsDefaultsAndClampsParameters()
    {
        when(inspectionMapper.selectHighScoreInspections(anyInt(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        service.scanQualityCalls(null, null, null);
        verify(inspectionMapper, org.mockito.Mockito.times(1))
                .selectHighScoreInspections(7, BigDecimal.valueOf(90d), 40);

        service.scanQualityCalls(0, new BigDecimal("80"), 0);
        verify(inspectionMapper).selectHighScoreInspections(7, new BigDecimal("80"), 40);

        service.scanQualityCalls(31, null, 51);
        verify(inspectionMapper, org.mockito.Mockito.times(2))
                .selectHighScoreInspections(7, BigDecimal.valueOf(90d), 40);

        service.scanQualityCalls(3, new BigDecimal("95"), 5);
        verify(inspectionMapper).selectHighScoreInspections(3, new BigDecimal("95"), 10);
    }

    @Test
    void scanQualityCallsCreatesUpToLimitAndSkipsFailures() throws Exception
    {
        AiQualityInspection missingRecord = inspection(null);
        AiQualityInspection first = inspection(1L);
        AiQualityInspection second = inspection(2L);
        AiQualityInspection third = inspection(3L);
        when(inspectionMapper.selectHighScoreInspections(anyInt(), any(), anyInt()))
                .thenReturn(Arrays.asList(missingRecord, first, second, third));
        AiCallRecord record = new AiCallRecord();
        record.setTranscript("咨询内容");
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);
        when(callRecordMapper.selectAiCallRecordByRecordId(2L)).thenReturn(record);
        when(callRecordMapper.selectAiCallRecordByRecordId(3L)).thenReturn(record);
        when(modelConfigService.chatJson(anyString(), anyString(), anyString())).thenReturn(DRAFT_JSON);

        int created = service.scanQualityCalls(5, new BigDecimal("90"), 2);

        assertThat(created).isEqualTo(2);
        verify(knowledgeMapper, org.mockito.Mockito.times(2)).insertAiLegalKnowledge(any());
    }

    @Test
    void scanQualityCallsSkipsIllegalArgumentAndNullCandidates()
    {
        AiQualityInspection invalid = inspection(1L);
        when(inspectionMapper.selectHighScoreInspections(anyInt(), any(), anyInt()))
                .thenReturn(Collections.singletonList(invalid));
        when(callRecordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(null);

        assertThat(service.scanQualityCalls(5, null, 5)).isZero();

        when(inspectionMapper.selectHighScoreInspections(anyInt(), any(), anyInt())).thenReturn(null);
        assertThat(service.scanQualityCalls(5, null, 5)).isZero();
    }

    private static AiQualityInspection inspection(Long recordId)
    {
        AiQualityInspection inspection = new AiQualityInspection();
        inspection.setRecordId(recordId);
        return inspection;
    }

    private static String repeat(char ch, int count)
    {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++)
        {
            sb.append(ch);
        }
        return sb.toString();
    }
}
