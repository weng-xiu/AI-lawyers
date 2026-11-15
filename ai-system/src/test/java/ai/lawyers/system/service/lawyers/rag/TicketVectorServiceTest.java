package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;

/**
 * P1-8：{@link TicketVectorService} 工单语义向量索引编排与相似检索测试。
 *
 * <p>覆盖开关/空参数短路、异步索引（工单缺失或未办结移除索引、文本组装、
 * embedding 落库与内存刷新、模型服务缺失/异常吞没）、全量重建循环补齐、
 * 相似检索（阈值过滤、ID 去重、按分数排序、排除当前话单、limit 截断、
 * 异常回退空列表）以及启动加载与关闭。</p>
 *
 * @author ai-lawyers
 */
class TicketVectorServiceTest
{
    private static final Long TICKET_ID = 7L;

    private AiCallTicketMapper ticketMapper;

    private IAiModelConfigService modelConfigService;

    private TicketVectorIndex ticketIndex;

    private ExecutorService indexExecutor;

    private TicketVectorService service;

    @BeforeEach
    void setUp()
    {
        ticketMapper = mock(AiCallTicketMapper.class);
        modelConfigService = mock(IAiModelConfigService.class);
        ticketIndex = mock(TicketVectorIndex.class);
        indexExecutor = mock(ExecutorService.class);
        // 同步执行异步任务，保证断言无需等待
        doAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(indexExecutor).submit(any(Runnable.class));

        service = new TicketVectorService();
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
        ReflectionTestUtils.setField(service, "ticketIndex", ticketIndex);
        ReflectionTestUtils.setField(service, "indexExecutor", indexExecutor);
        ReflectionTestUtils.setField(service, "vectorEnabled", true);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.5f);
    }

    // ---------- 基础状态 ----------

    @Test
    void readyRequiresSwitchAndNonEmptyIndex()
    {
        when(ticketIndex.isReady()).thenReturn(true);
        assertThat(service.isReady()).isTrue();

        ReflectionTestUtils.setField(service, "vectorEnabled", false);
        assertThat(service.isReady()).isFalse();
    }

    @Test
    void indexedCountAndRemoveDelegateToIndex()
    {
        when(ticketIndex.size()).thenReturn(12);

        assertThat(service.indexedCount()).isEqualTo(12);

        service.removeTicket(TICKET_ID);
        verify(ticketIndex).remove(TICKET_ID);
    }

    // ---------- 异步索引 ----------

    @Test
    void indexTicketSkippedWhenDisabledOrNull()
    {
        service.indexTicketAsync(null);
        ReflectionTestUtils.setField(service, "vectorEnabled", false);
        service.indexTicketAsync(TICKET_ID);

        verify(indexExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    void indexTicketRemovesMissingOrOpenTicket()
    {
        when(ticketMapper.selectAiCallTicketByTicketId(TICKET_ID)).thenReturn(null);
        service.indexTicketAsync(TICKET_ID);
        verify(ticketIndex).remove(TICKET_ID);

        AiCallTicket open = new AiCallTicket();
        open.setTicketId(TICKET_ID);
        open.setStatus("1");
        when(ticketMapper.selectAiCallTicketByTicketId(TICKET_ID)).thenReturn(open);
        service.indexTicketAsync(TICKET_ID);
        verify(ticketIndex, org.mockito.Mockito.times(2)).remove(TICKET_ID);
        verify(ticketIndex, never()).upsert(any(), any());
    }

    @Test
    void indexTicketEmbedsAndPersistsVector()
    {
        AiCallTicket ticket = closedTicket(TICKET_ID, "拖欠工资", "公司拖了两个月工资");
        float[] vector = new float[] { 0.1f, 0.2f, 0.3f };
        when(ticketMapper.selectAiCallTicketByTicketId(TICKET_ID)).thenReturn(ticket);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG_INDEX)))
                .thenReturn(Collections.singletonList(vector));

        service.indexTicketAsync(TICKET_ID);

        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(modelConfigService).embedTexts(texts.capture(), eq(AiModelCallLogRecorder.SCENE_RAG_INDEX));
        assertThat(texts.getValue()).containsExactly("拖欠工资。公司拖了两个月工资");

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(ticketMapper).updateTicketEmbedding(eq(TICKET_ID), bytes.capture(), eq(3));
        assertThat(bytes.getValue()).isEqualTo(VectorUtils.toBytes(vector));
        verify(ticketIndex).upsert(TICKET_ID, vector);
    }

    @Test
    void indexTicketSkipsEmptyTextOrEmptyEmbedding()
    {
        AiCallTicket blank = closedTicket(TICKET_ID, " ", null);
        when(ticketMapper.selectAiCallTicketByTicketId(TICKET_ID)).thenReturn(blank);

        service.indexTicketAsync(TICKET_ID);

        verifyNoInteractions(modelConfigService);
        verify(ticketIndex, never()).upsert(any(), any());

        AiCallTicket ticket = closedTicket(TICKET_ID, "标题", "内容");
        when(ticketMapper.selectAiCallTicketByTicketId(TICKET_ID)).thenReturn(ticket);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG_INDEX)))
                .thenReturn(Collections.singletonList(new float[0]));

        service.indexTicketAsync(TICKET_ID);

        verify(ticketMapper, never()).updateTicketEmbedding(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void indexTicketSwallowsMissingModelServiceAndEmbeddingFailure()
    {
        ReflectionTestUtils.setField(service, "modelConfigService", null);
        AiCallTicket ticket = closedTicket(TICKET_ID, "标题", "内容");
        when(ticketMapper.selectAiCallTicketByTicketId(TICKET_ID)).thenReturn(ticket);

        service.indexTicketAsync(TICKET_ID);
        verify(ticketIndex, never()).upsert(any(), any());

        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG_INDEX)))
                .thenThrow(new IllegalStateException("model down"));

        service.indexTicketAsync(TICKET_ID);
        verify(ticketIndex, never()).upsert(any(), any());
    }

    // ---------- 全量重建 ----------

    @Test
    void rebuildAsyncSkippedWhenDisabled()
    {
        ReflectionTestUtils.setField(service, "vectorEnabled", false);

        service.rebuildAllAsync();

        verify(indexExecutor, never()).submit(any(Runnable.class));
        verifyNoInteractions(ticketMapper);
    }

    @Test
    void rebuildAsyncIndexesPendingTickets()
    {
        AiCallTicket first = closedTicket(1L, "标题1", "内容1");
        AiCallTicket second = closedTicket(2L, "标题2", "内容2");
        when(ticketMapper.selectClosedTicketsWithoutEmbedding())
                .thenReturn(Arrays.asList(first, second));
        when(ticketMapper.selectAiCallTicketByTicketId(any())).thenAnswer(invocation ->
        {
            Long id = invocation.getArgument(0);
            return id == 1L ? first : second;
        });
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG_INDEX)))
                .thenReturn(Collections.singletonList(new float[] { 0.5f }));

        service.rebuildAllAsync();

        verify(ticketIndex).upsert(eq(1L), any(float[].class));
        verify(ticketIndex).upsert(eq(2L), any(float[].class));
    }

    @Test
    void rebuildAsyncSwallowsScanFailure()
    {
        when(ticketMapper.selectClosedTicketsWithoutEmbedding()).thenThrow(new IllegalStateException("db down"));

        service.rebuildAllAsync();

        verify(ticketIndex, never()).upsert(any(), any());
    }

    // ---------- 相似检索 ----------

    @Test
    void findSimilarShortCircuitsWhenUnavailable()
    {
        assertThat(service.findSimilar("欠薪", null, 3)).isEmpty();

        when(ticketIndex.isReady()).thenReturn(true);
        ReflectionTestUtils.setField(service, "vectorEnabled", false);
        assertThat(service.findSimilar("欠薪", null, 3)).isEmpty();
        ReflectionTestUtils.setField(service, "vectorEnabled", true);
        assertThat(service.findSimilar(" ", null, 3)).isEmpty();
        assertThat(service.findSimilar("欠薪", null, 0)).isEmpty();

        verifyNoInteractions(modelConfigService);
    }

    @Test
    void findSimilarReturnsEmptyWhenEmbeddingUnavailable()
    {
        when(ticketIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG)))
                .thenReturn(Collections.emptyList());

        assertThat(service.findSimilar("欠薪", null, 3)).isEmpty();

        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG)))
                .thenReturn(Collections.singletonList(new float[0]));
        assertThat(service.findSimilar("欠薪", null, 3)).isEmpty();

        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG)))
                .thenThrow(new IllegalStateException("model down"));
        assertThat(service.findSimilar("欠薪", null, 3)).isEmpty();

        verifyNoInteractions(ticketMapper);
    }

    @Test
    void findSimilarFiltersThresholdAndDedupes()
    {
        when(ticketIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG)))
                .thenReturn(Collections.singletonList(new float[] { 1f, 0f }));
        when(ticketIndex.searchNearest(any(float[].class), eq(9)))
                .thenReturn(Arrays.asList(
                        new TicketVectorIndex.Hit(1L, 0.9f),
                        new TicketVectorIndex.Hit(2L, 0.4f),
                        new TicketVectorIndex.Hit(1L, 0.8f)));
        when(ticketMapper.selectAiCallTicketByIds(anyList()))
                .thenReturn(Collections.singletonList(ticket(1L, 100L)));

        List<AiCallTicket> result = service.findSimilar("欠薪", null, 3);

        assertThat(result).extracting(AiCallTicket::getTicketId).containsExactly(1L);
        verify(ticketMapper).selectAiCallTicketByIds(Collections.singletonList(1L));
    }

    @Test
    void findSimilarOrdersByScoreAndExcludesCurrentRecord()
    {
        when(ticketIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG)))
                .thenReturn(Collections.singletonList(new float[] { 1f }));
        when(ticketIndex.searchNearest(any(float[].class), eq(3)))
                .thenReturn(Arrays.asList(
                        new TicketVectorIndex.Hit(1L, 0.9f),
                        new TicketVectorIndex.Hit(2L, 0.8f)));
        // 数据层返回顺序不保证，服务需按相似度顺序重排
        when(ticketMapper.selectAiCallTicketByIds(anyList()))
                .thenReturn(Arrays.asList(ticket(2L, 200L), ticket(1L, 100L)));

        List<AiCallTicket> result = service.findSimilar("欠薪", 100L, 1);

        assertThat(result).extracting(AiCallTicket::getTicketId).containsExactly(2L);
    }

    @Test
    void findSimilarSkipsMissingTickets()
    {
        when(ticketIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG)))
                .thenReturn(Collections.singletonList(new float[] { 1f }));
        when(ticketIndex.searchNearest(any(float[].class), eq(9)))
                .thenReturn(Arrays.asList(
                        new TicketVectorIndex.Hit(1L, 0.9f),
                        new TicketVectorIndex.Hit(2L, 0.8f)));
        when(ticketMapper.selectAiCallTicketByIds(anyList()))
                .thenReturn(Collections.singletonList(ticket(2L, null)));

        List<AiCallTicket> result = service.findSimilar("欠薪", null, 3);

        assertThat(result).extracting(AiCallTicket::getTicketId).containsExactly(2L);
    }

    @Test
    void findSimilarReturnsEmptyWhenNoHitAboveThreshold()
    {
        when(ticketIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG)))
                .thenReturn(Collections.singletonList(new float[] { 1f }));
        when(ticketIndex.searchNearest(any(float[].class), eq(3)))
                .thenReturn(Collections.singletonList(new TicketVectorIndex.Hit(1L, 0.1f)));

        assertThat(service.findSimilar("欠薪", null, 1)).isEmpty();
        verify(ticketMapper, never()).selectAiCallTicketByIds(anyList());
    }

    // ---------- 启动与关闭 ----------

    @Test
    void initSkippedWhenDisabled()
    {
        ReflectionTestUtils.setField(service, "vectorEnabled", false);

        service.init();

        verifyNoInteractions(ticketMapper);
        verify(indexExecutor, never()).submit(any(Runnable.class));
        service.shutdown();
    }

    @Test
    void shutdownToleratesMissingExecutor()
    {
        ReflectionTestUtils.setField(service, "indexExecutor", null);

        service.shutdown();
    }

    @Test
    void shutdownStopsExecutor()
    {
        service.shutdown();

        verify(indexExecutor).shutdown();
    }

    private static AiCallTicket closedTicket(Long ticketId, String title, String content)
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(ticketId);
        ticket.setTitle(title);
        ticket.setContent(content);
        ticket.setStatus("2");
        return ticket;
    }

    private static AiCallTicket ticket(Long ticketId, Long recordId)
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(ticketId);
        ticket.setRecordId(recordId);
        return ticket;
    }
}
