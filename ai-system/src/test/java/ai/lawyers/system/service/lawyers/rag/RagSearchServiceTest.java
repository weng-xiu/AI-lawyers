package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.domain.lawyers.stat.AiModelCallLog;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeChunkMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder;
import ai.lawyers.system.service.lawyers.stat.ModelUsage;

/**
 * T3/F8：{@link RagSearchService} 混合检索测试。
 *
 * <p>覆盖开关与空问句、关键词路 FULLTEXT→LIKE 降级、向量路可用性判断与异常跳过、
 * RRF 融合去重与得分累加、Top-K 截断、rerank 精排（真实 HTTP 端点：成功重排/
 * HTTP 失败/解析为空/连接异常均退回融合序）、rerank 状态与联调、Prompt 上下文拼装、
 * 知识 ID 去重与 rerank 响应多协议解析。</p>
 *
 * @author ai-lawyers
 */
class RagSearchServiceTest
{
    private AiLegalKnowledgeChunkMapper chunkMapper;

    private AiLegalKnowledgeMapper knowledgeMapper;

    private IAiModelConfigService modelConfigService;

    private VectorIndex vectorIndex;

    private HotlineMetrics metrics;

    private AiModelCallLogRecorder callLogRecorder;

    private KnowledgeQueryLogRecorder queryLogRecorder;

    private RagSearchService service;

    private HttpServer server;

    private String rerankBase;

    @BeforeEach
    void setUp() throws IOException
    {
        chunkMapper = mock(AiLegalKnowledgeChunkMapper.class);
        knowledgeMapper = mock(AiLegalKnowledgeMapper.class);
        modelConfigService = mock(IAiModelConfigService.class);
        vectorIndex = mock(VectorIndex.class);
        metrics = mock(HotlineMetrics.class);
        callLogRecorder = mock(AiModelCallLogRecorder.class);
        queryLogRecorder = mock(KnowledgeQueryLogRecorder.class);

        service = new RagSearchService();
        ReflectionTestUtils.setField(service, "chunkMapper", chunkMapper);
        ReflectionTestUtils.setField(service, "knowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(service, "modelConfigService", modelConfigService);
        ReflectionTestUtils.setField(service, "vectorIndex", vectorIndex);
        ReflectionTestUtils.setField(service, "metrics", metrics);
        ReflectionTestUtils.setField(service, "callLogRecorder", callLogRecorder);
        ReflectionTestUtils.setField(service, "queryLogRecorder", queryLogRecorder);
        ReflectionTestUtils.setField(service, "ragEnabled", true);
        ReflectionTestUtils.setField(service, "topK", 3);
        ReflectionTestUtils.setField(service, "candidateSize", 20);
        ReflectionTestUtils.setField(service, "rerankEnabled", false);
        ReflectionTestUtils.setField(service, "rerankUrl", "");
        ReflectionTestUtils.setField(service, "rerankModel", "bge-reranker-base");
        ReflectionTestUtils.setField(service, "rerankApiKey", "");
        ReflectionTestUtils.setField(service, "chunkMaxLen", 10);
        service.init();

        // N3：默认全库存在有效知识，resolveActiveScope 全库路返回 null（等同旧行为，不影响既有断言）
        when(knowledgeMapper.selectActiveKnowledgeIds(any()))
                .thenReturn(Collections.singletonList(999L));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        context("/ok", 200, "{\"results\":[{\"index\":1,\"relevance_score\":0.9},"
                + "{\"index\":0,\"relevance_score\":0.5}]}");
        context("/http500", 500, "boom");
        context("/unparseable", 200, "{\"foo\":1}");
        server.start();
        rerankBase = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown()
    {
        if (server != null)
        {
            server.stop(0);
        }
    }

    // ---------- 开关与关键词路 ----------

    @Test
    void disabledOrBlankQueryReturnsEmpty()
    {
        ReflectionTestUtils.setField(service, "ragEnabled", false);
        assertThat(service.search("劳动合同", null)).isEmpty();
        assertThat(service.isEnabled()).isFalse();

        ReflectionTestUtils.setField(service, "ragEnabled", true);
        assertThat(service.search("  ", null)).isEmpty();
        verify(chunkMapper, never()).searchByFulltext(any(), any(), anyInt());
    }

    @Test
    void keywordRouteUsesFulltextHits()
    {
        when(chunkMapper.searchByFulltext(eq("劳动合同"), isNull(), eq(20)))
                .thenReturn(Arrays.asList(chunk(1L, 11L, "合同"), chunk(2L, 12L, "工资")));

        List<RagChunk> hits = service.search("劳动合同", null);

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(1L, 2L);
        assertThat(hits.get(0).getScore()).isCloseTo(1d / 61, within(1e-9));
        assertThat(hits.get(1).getScore()).isCloseTo(1d / 62, within(1e-9));
        verify(chunkMapper, never()).searchByLike(any(), any(), anyInt());
        verify(metrics).incrementRagHit("keyword");
        verify(queryLogRecorder).record(eq("劳动合同"), eq(KnowledgeQueryLogRecorder.SCENE_OTHER),
                eq(hits), eq("keyword"), anyLong());
    }

    @Test
    void fulltextFailureFallsBackToLike()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("ngram unavailable"));
        when(chunkMapper.searchByLike(eq("工资"), isNull(), eq(20)))
                .thenReturn(Collections.singletonList(chunk(3L, 13L, "工资")));

        List<RagChunk> hits = service.search("工资", null);

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(3L);
        verify(metrics).incrementRagHit("keyword");
    }

    @Test
    void emptyFulltextFallsBackToLike()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt())).thenReturn(new ArrayList<>());
        when(chunkMapper.searchByLike(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(chunk(4L, 14L, "工伤")));

        assertThat(service.search("工伤", null)).extracting(RagChunk::getChunkId).containsExactly(4L);
    }

    @Test
    void bothKeywordRoutesFailYieldsEmptyAndNoneRoute()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("fulltext down"));
        when(chunkMapper.searchByLike(any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("like down"));

        assertThat(service.search("离婚", null)).isEmpty();
        verify(metrics).incrementRagHit("none");
        verify(queryLogRecorder).record(eq("离婚"), anyString(), isNull(), eq("none"), anyLong());
    }

    // ---------- 向量路 ----------

    @Test
    void vectorRouteUsedWhenKeywordEmpty()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt())).thenReturn(new ArrayList<>());
        when(chunkMapper.searchByLike(any(), any(), anyInt())).thenReturn(new ArrayList<>());
        when(vectorIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), eq(AiModelCallLogRecorder.SCENE_RAG)))
                .thenReturn(Collections.singletonList(new float[] { 0.1f, 0.2f }));
        when(vectorIndex.searchNearest(any(), isNull(), eq(20)))
                .thenReturn(Collections.singletonList(new RagChunk(chunk(5L, 15L, "继承"), 0.88d)));

        List<RagChunk> hits = service.search("继承", null);

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(5L);
        assertThat(hits.get(0).getScore()).isCloseTo(1d / 61, within(1e-9));
        verify(metrics).incrementRagHit("vector");
    }

    @Test
    void fusedRouteDeduplicatesAndSumsScores()
    {
        AiLegalKnowledgeChunk shared = chunk(1L, 11L, "同一分块");
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Arrays.asList(shared, chunk(2L, 12L, "关键词独有")));
        when(vectorIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), anyString()))
                .thenReturn(Collections.singletonList(new float[] { 1f }));
        when(vectorIndex.searchNearest(any(), any(), anyInt()))
                .thenReturn(Arrays.asList(new RagChunk(shared, 0.9d),
                        new RagChunk(chunk(3L, 13L, "向量独有"), 0.8d)));

        List<RagChunk> hits = service.search("合同", null);

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(1L, 2L, 3L);
        assertThat(hits.get(0).getScore()).isCloseTo(2d / 61, within(1e-9));
        verify(metrics).incrementRagHit("fused");
    }

    @Test
    void vectorSkippedWhenIndexNotReady()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(chunk(1L, 11L, "合同")));
        when(vectorIndex.isReady()).thenReturn(false);

        service.search("合同", null);

        verify(modelConfigService, never()).embedTexts(anyList(), anyString());
        verify(vectorIndex, never()).searchNearest(any(), any(), anyInt());
    }

    @Test
    void vectorSkippedWhenEmbeddingEmpty()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(chunk(1L, 11L, "合同")));
        when(vectorIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), anyString()))
                .thenReturn(Collections.singletonList(new float[0]));

        List<RagChunk> hits = service.search("合同", null);

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(1L);
        verify(vectorIndex, never()).searchNearest(any(), any(), anyInt());
    }

    @Test
    void vectorFailureSkipped()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(chunk(1L, 11L, "合同")));
        when(vectorIndex.isReady()).thenReturn(true);
        when(modelConfigService.embedTexts(anyList(), anyString()))
                .thenThrow(new IllegalStateException("embedding down"));

        assertThat(service.search("合同", null)).extracting(RagChunk::getChunkId).containsExactly(1L);
        verify(metrics).incrementRagHit("keyword");
    }

    // ---------- Top-K 与候选数 ----------

    @Test
    void topKTruncatesResult()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Arrays.asList(chunk(1L, 11L, "a"), chunk(2L, 12L, "b"),
                        chunk(3L, 13L, "c"), chunk(4L, 14L, "d")));

        assertThat(service.search("x", null)).hasSize(3);

        ReflectionTestUtils.setField(service, "topK", 1);
        assertThat(service.search("x", null)).extracting(RagChunk::getChunkId).containsExactly(1L);
    }

    @Test
    void topKZeroFallsBackToThreeAndCandidateSizeFallback()
    {
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Arrays.asList(chunk(1L, 11L, "a"), chunk(2L, 12L, "b"),
                        chunk(3L, 13L, "c"), chunk(4L, 14L, "d")));
        ReflectionTestUtils.setField(service, "topK", 0);
        ReflectionTestUtils.setField(service, "candidateSize", 0);

        assertThat(service.search("x", null)).hasSize(3);
        verify(chunkMapper).searchByFulltext("x", null, 20);
    }

    // ---------- rerank ----------

    @Test
    void rerankReordersCandidates()
    {
        ReflectionTestUtils.setField(service, "rerankEnabled", true);
        ReflectionTestUtils.setField(service, "rerankUrl", rerankBase + "/ok");
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Arrays.asList(chunk(1L, 11L, "甲"), chunk(2L, 12L, "乙")));

        List<RagChunk> hits = service.search("合同", null);

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(2L, 1L);
        assertThat(hits.get(0).getScore()).isEqualTo(0.9d);
        assertThat(hits.get(1).getScore()).isEqualTo(0.5d);
        verify(metrics).incrementAi("rerank", "success");
        verify(callLogRecorder).recordWithoutConfig(eq(AiModelCallLogRecorder.KIND_RERANK),
                eq(AiModelCallLogRecorder.SCENE_RAG), eq("rerank"), eq("bge-reranker-base"),
                any(ModelUsage.class), anyLong(), eq(1), eq(AiModelCallLog.RESULT_SUCCESS), isNull());
    }

    @Test
    void rerankHttpFailureKeepsFusedOrder()
    {
        ReflectionTestUtils.setField(service, "rerankEnabled", true);
        ReflectionTestUtils.setField(service, "rerankUrl", rerankBase + "/http500");
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Arrays.asList(chunk(1L, 11L, "甲"), chunk(2L, 12L, "乙")));

        List<RagChunk> hits = service.search("合同", null);

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(1L, 2L);
        verify(metrics).incrementAi("rerank", "fail");
        verify(callLogRecorder).recordWithoutConfig(anyString(), anyString(), anyString(), anyString(),
                any(ModelUsage.class), anyLong(), eq(1), eq(AiModelCallLog.RESULT_FAIL), anyString());
    }

    @Test
    void rerankUnparseableKeepsFusedOrder()
    {
        ReflectionTestUtils.setField(service, "rerankEnabled", true);
        ReflectionTestUtils.setField(service, "rerankUrl", rerankBase + "/unparseable");
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(chunk(1L, 11L, "甲")));

        assertThat(service.search("合同", null)).extracting(RagChunk::getChunkId).containsExactly(1L);
        verify(metrics).incrementAi("rerank", "fail");
    }

    @Test
    void rerankExceptionKeepsFusedOrder()
    {
        ReflectionTestUtils.setField(service, "rerankEnabled", true);
        ReflectionTestUtils.setField(service, "rerankUrl", "http://127.0.0.1:1/rerank");
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(chunk(1L, 11L, "甲")));

        assertThat(service.search("合同", null)).extracting(RagChunk::getChunkId).containsExactly(1L);
        verify(metrics).incrementAi("rerank", "fail");
    }

    @Test
    void rerankStatusReflectsConfig()
    {
        Map<String, Object> status = service.rerankStatus();
        assertThat(status).containsEntry("enabled", false).containsEntry("configured", false);

        ReflectionTestUtils.setField(service, "rerankEnabled", true);
        ReflectionTestUtils.setField(service, "rerankUrl", "http://x/rerank");
        status = service.rerankStatus();
        assertThat(status).containsEntry("enabled", true)
                .containsEntry("url", "http://x/rerank")
                .containsEntry("model", "bge-reranker-base")
                .containsEntry("configured", true);
    }

    // ---------- rerank 联调 ----------

    @Test
    void testRerankRejectsWhenNotConfigured()
    {
        Map<String, Object> out = service.testRerank();

        assertThat(out).containsEntry("success", false);
        assertThat(String.valueOf(out.get("error"))).contains("未启用");
    }

    @Test
    void testRerankReturnsRankingOnSuccess()
    {
        ReflectionTestUtils.setField(service, "rerankEnabled", true);
        ReflectionTestUtils.setField(service, "rerankUrl", rerankBase + "/ok");

        Map<String, Object> out = service.testRerank();

        assertThat(out).containsEntry("success", true).containsEntry("httpStatus", 200);
        @SuppressWarnings("unchecked")
        List<String> ranking = (List<String>) out.get("ranking");
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0)).contains("score=0.9000");
    }

    @Test
    void testRerankReportsHttpFailure()
    {
        ReflectionTestUtils.setField(service, "rerankEnabled", true);
        ReflectionTestUtils.setField(service, "rerankUrl", rerankBase + "/http500");

        Map<String, Object> out = service.testRerank();

        assertThat(out).containsEntry("success", false).containsEntry("httpStatus", 500);
        assertThat(String.valueOf(out.get("error"))).contains("HTTP 500");
    }

    @Test
    void testRerankReportsUnparseableResponse()
    {
        ReflectionTestUtils.setField(service, "rerankEnabled", true);
        ReflectionTestUtils.setField(service, "rerankUrl", rerankBase + "/unparseable");

        Map<String, Object> out = service.testRerank();

        assertThat(out).containsEntry("success", false).containsKey("raw");
        assertThat(String.valueOf(out.get("error"))).contains("无法解析");
    }

    // ---------- Prompt 上下文与工具 ----------

    @Test
    void buildContextFormatsAndTruncates()
    {
        AiLegalKnowledgeChunk longChunk = chunk(1L, 11L, "0123456789ABC");
        longChunk.setTitle("劳动合同");
        longChunk.setLawArticle("第十条");
        longChunk.setSource("劳动合同法");
        AiLegalKnowledgeChunk plain = chunk(2L, 12L, "短内容");
        plain.setTitle(null);
        plain.setLawArticle("");
        plain.setSource(null);

        String text = service.buildContext(Arrays.asList(new RagChunk(longChunk, 1d),
                new RagChunk(plain, 0.5d)));

        assertThat(text).isEqualTo("1. 劳动合同：0123456789...（法条：第十条）（出处：劳动合同法）\n"
                + "2. ：短内容\n");
        assertThat(service.buildContext(null)).isEmpty();
        assertThat(service.buildContext(new ArrayList<>())).isEmpty();
    }

    @Test
    void knowledgeIdsOfDeduplicatesAndSkipsNull()
    {
        AiLegalKnowledgeChunk noKnowledge = chunk(2L, null, "无知识ID");
        List<Long> ids = service.knowledgeIdsOf(Arrays.asList(
                new RagChunk(chunk(1L, 11L, "a"), 1d),
                new RagChunk(chunk(3L, 11L, "b"), 1d),
                new RagChunk(noKnowledge, 1d)));

        assertThat(ids).containsExactly(11L);
        assertThat(service.knowledgeIdsOf(null)).isEmpty();
    }

    @Test
    void truncateForRerankHonoursMaxLength()
    {
        assertThat(service.truncateForRerank(null)).isEmpty();
        assertThat(service.truncateForRerank("abcdef")).isEqualTo("abcdef");
        assertThat(service.truncateForRerank("0123456789ABC")).isEqualTo("0123456789");

        ReflectionTestUtils.setField(service, "chunkMaxLen", 0);
        assertThat(service.truncateForRerank("0123456789ABC")).isEqualTo("0123456789ABC");
    }

    @Test
    void applyRerankResponseSupportsProtocols()
    {
        List<RagChunk> candidates = Arrays.asList(
                new RagChunk(chunk(1L, 11L, "a"), 0d),
                new RagChunk(chunk(2L, 12L, "b"), 0d));
        ObjectMapper om = new ObjectMapper();

        List<RagChunk> byData = RagSearchService.applyRerankResponse(candidates,
                read(om, "{\"data\":[{\"index\":1,\"score\":0.3},{\"index\":0,\"score\":0.7}]}"));
        assertThat(byData).extracting(RagChunk::getChunkId).containsExactly(1L, 2L);

        List<RagChunk> topLevel = RagSearchService.applyRerankResponse(candidates,
                read(om, "[{\"index\":5,\"score\":0.9},{\"index\":1,\"score\":0.2}]"));
        assertThat(topLevel).extracting(RagChunk::getChunkId).containsExactly(2L);

        assertThat(RagSearchService.applyRerankResponse(candidates, read(om, "{\"foo\":1}"))).isEmpty();
        assertThat(RagSearchService.applyRerankResponse(candidates, read(om, "[]"))).isEmpty();
    }

    @Test
    void nullOptionalCollaboratorsTolerated()
    {
        ReflectionTestUtils.setField(service, "metrics", null);
        ReflectionTestUtils.setField(service, "queryLogRecorder", null);
        ReflectionTestUtils.setField(service, "callLogRecorder", null);
        when(chunkMapper.searchByFulltext(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(chunk(1L, 11L, "合同")));

        assertThat(service.search("合同", null)).hasSize(1);
    }

    // ---------- N3：运行时效力过滤（失效/修订法条零召回） ----------

    @Test
    void explicitScopeAllInvalidReturnsEmptyAndNoneRoute()
    {
        // 调用方限定的两个知识均已失效/修订，有效集合求交为空 → 直接零召回，不触分块检索
        when(knowledgeMapper.selectActiveKnowledgeIds(Arrays.asList(11L, 12L)))
                .thenReturn(new ArrayList<>());

        assertThat(service.search("劳动合同", Arrays.asList(11L, 12L))).isEmpty();

        verify(chunkMapper, never()).searchByFulltext(any(), any(), anyInt());
        verify(chunkMapper, never()).searchByLike(any(), any(), anyInt());
        verify(metrics).incrementRagHit("none");
        verify(queryLogRecorder).record(eq("劳动合同"), anyString(), isNull(), eq("none"), anyLong());
    }

    @Test
    void explicitScopeIntersectsActiveSetBeforeRecall()
    {
        // 显式范围 11/12/13 中仅 11、13 仍有效：关键词路只能收到求交后的范围
        when(knowledgeMapper.selectActiveKnowledgeIds(Arrays.asList(11L, 12L, 13L)))
                .thenReturn(Arrays.asList(11L, 13L));
        when(chunkMapper.searchByFulltext(eq("合同"), eq(Arrays.asList(11L, 13L)), eq(20)))
                .thenReturn(Collections.singletonList(chunk(1L, 11L, "合同")));

        List<RagChunk> hits = service.search("合同", Arrays.asList(11L, 12L, 13L));

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(1L);
        verify(metrics).incrementRagHit("keyword");
    }

    @Test
    void fullScanWithoutAnyActiveKnowledgeReturnsEmpty()
    {
        // 全库检索（scope=null）但库中已无有效知识 → 零召回
        when(knowledgeMapper.selectActiveKnowledgeIds(isNull())).thenReturn(new ArrayList<>());

        assertThat(service.search("继承", null)).isEmpty();

        verify(chunkMapper, never()).searchByFulltext(any(), any(), anyInt());
        verify(metrics).incrementRagHit("none");
    }

    @Test
    void activeScopeQueryFailureDegradesToOriginalScope()
    {
        // 有效集合查询故障不阻断主链路：降级使用原 scope，关键词路照常召回
        when(knowledgeMapper.selectActiveKnowledgeIds(anyList()))
                .thenThrow(new IllegalStateException("db down"));
        when(chunkMapper.searchByFulltext(eq("工资"), eq(Arrays.asList(13L)), eq(20)))
                .thenReturn(Collections.singletonList(chunk(3L, 13L, "工资")));

        List<RagChunk> hits = service.search("工资", Arrays.asList(13L));

        assertThat(hits).extracting(RagChunk::getChunkId).containsExactly(3L);
        verify(metrics).incrementRagHit("keyword");
    }

    private com.fasterxml.jackson.databind.JsonNode read(ObjectMapper om, String json)
    {
        try
        {
            return om.readTree(json);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private void context(String path, int status, String body) throws IOException
    {
        server.createContext(path, exchange -> {
            try (InputStream in = exchange.getRequestBody())
            {
                while (in.read() >= 0)
                {
                    // 消费请求体，避免连接复用异常
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(bytes);
            }
        });
    }

    private AiLegalKnowledgeChunk chunk(long chunkId, Long knowledgeId, String content)
    {
        AiLegalKnowledgeChunk c = new AiLegalKnowledgeChunk();
        c.setChunkId(chunkId);
        c.setKnowledgeId(knowledgeId);
        c.setChunkContent(content);
        return c;
    }
}
