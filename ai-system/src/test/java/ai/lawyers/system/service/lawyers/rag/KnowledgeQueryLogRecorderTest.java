package ai.lawyers.system.service.lawyers.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiKnowledgeQueryLog;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledgeChunk;
import ai.lawyers.system.mapper.lawyers.AiKnowledgeQueryLogMapper;
import ai.lawyers.system.service.lawyers.concurrent.ExecutorRegistry;

/**
 * F8：{@link KnowledgeQueryLogRecorder} 知识检索查询日志记录器测试。
 *
 * <p>覆盖开关/空问句/纯标点问句跳过、脱敏截断与业务字段组装（命中数、最高分知识、
 * 场景与路由默认值及截断、耗时区间收敛）、异步提交与落库异常吞没、
 * 转草稿标记、线程池纳管与队列满丢弃计数、自建池回退与关闭幂等。</p>
 *
 * @author ai-lawyers
 */
class KnowledgeQueryLogRecorderTest
{
    private AiKnowledgeQueryLogMapper queryLogMapper;

    private ExecutorRegistry registry;

    private ExecutorService executor;

    private KnowledgeQueryLogRecorder recorder;

    @BeforeEach
    void setUp()
    {
        queryLogMapper = mock(AiKnowledgeQueryLogMapper.class);
        registry = mock(ExecutorRegistry.class);
        executor = mock(ExecutorService.class);
        recorder = new KnowledgeQueryLogRecorder();
        ReflectionTestUtils.setField(recorder, "queryLogMapper", queryLogMapper);
        ReflectionTestUtils.setField(recorder, "registry", registry);
        ReflectionTestUtils.setField(recorder, "enabled", true);
        when(registry.create(eq("ai-knowledge-query-log"), eq(1), eq(1), eq(2000),
                any(RejectedExecutionHandler.class))).thenReturn(executor);
        // 同步执行提交的任务，保证断言无需等待
        doAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        recorder.init();
    }

    // ---------- 跳过条件 ----------

    @Test
    void recordSkippedWhenDisabled()
    {
        ReflectionTestUtils.setField(recorder, "enabled", false);

        recorder.record("拖欠工资怎么办", KnowledgeQueryLogRecorder.SCENE_AGENT, null, "none", 10L);

        verify(executor, never()).execute(any(Runnable.class));
        verifyNoInteractions(queryLogMapper);
    }

    @Test
    void recordSkippedForBlankQuery()
    {
        recorder.record("   ", KnowledgeQueryLogRecorder.SCENE_AGENT, null, "none", 10L);

        verify(executor, never()).execute(any(Runnable.class));
        verifyNoInteractions(queryLogMapper);
    }

    @Test
    void recordSkippedWhenNormalizedQueryIsEmpty()
    {
        // 纯标点归一化后为空串，无分组价值，不落库
        recorder.record("？!，。", KnowledgeQueryLogRecorder.SCENE_AGENT, null, "none", 10L);

        verify(executor, never()).execute(any(Runnable.class));
        verifyNoInteractions(queryLogMapper);
    }

    // ---------- 组装 ----------

    @Test
    void recordMasksSensitiveTextAndFillsHitFields()
    {
        AiLegalKnowledgeChunk chunk = new AiLegalKnowledgeChunk();
        chunk.setKnowledgeId(77L);
        List<RagChunk> hits = Arrays.asList(new RagChunk(chunk, 0.93));

        recorder.record("我手机号13800138000被拖欠工资怎么办", KnowledgeQueryLogRecorder.SCENE_AGENT,
                hits, "fused", 120L);

        AiKnowledgeQueryLog saved = captureSaved();
        assertThat(saved.getQueryText()).contains("138****8000").doesNotContain("13800138000");
        assertThat(saved.getQueryHash()).matches("[0-9a-f]{32}");
        assertThat(saved.getScene()).isEqualTo(KnowledgeQueryLogRecorder.SCENE_AGENT);
        assertThat(saved.getHitCount()).isEqualTo(1);
        assertThat(saved.getTopKnowledgeId()).isEqualTo(77L);
        assertThat(saved.getRoute()).isEqualTo("fused");
        assertThat(saved.getLatencyMs()).isEqualTo(120);
        assertThat(saved.getConverted()).isZero();
        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    void recordUsesDefaultsWhenSceneRouteAndHitsMissing()
    {
        recorder.record("劳动合同到期不续签有补偿吗", " ", null, null, 5L);

        AiKnowledgeQueryLog saved = captureSaved();
        assertThat(saved.getScene()).isEqualTo(KnowledgeQueryLogRecorder.SCENE_OTHER);
        assertThat(saved.getRoute()).isEqualTo("none");
        assertThat(saved.getHitCount()).isZero();
        assertThat(saved.getTopKnowledgeId()).isNull();
    }

    @Test
    void recordTruncatesSceneAndRouteToColumnWidth()
    {
        String longScene = repeat('s', 40);
        String longRoute = repeat('r', 20);

        recorder.record("离婚财产怎么分割", longScene, null, longRoute, 1L);

        AiKnowledgeQueryLog saved = captureSaved();
        assertThat(saved.getScene()).hasSize(32);
        assertThat(saved.getRoute()).hasSize(16);
    }

    @Test
    void recordTruncatesMaskedQueryToStoreLimit()
    {
        recorder.record(repeat('a', 600), KnowledgeQueryLogRecorder.SCENE_COPILOT, null, "vector", 1L);

        AiKnowledgeQueryLog saved = captureSaved();
        assertThat(saved.getQueryText()).hasSize(KnowledgeQueryTextUtils.MAX_STORE_LENGTH);
    }

    @Test
    void recordClampsNegativeLatencyToZero()
    {
        recorder.record("工伤认定流程", KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT, null, "keyword", -9L);

        assertThat(captureSaved().getLatencyMs()).isZero();
    }

    @Test
    void recordClampsHugeLatencyToIntMax()
    {
        recorder.record("工伤认定流程", KnowledgeQueryLogRecorder.SCENE_VOICE_ROBOT, null, "keyword", Long.MAX_VALUE);

        assertThat(captureSaved().getLatencyMs()).isEqualTo(Integer.MAX_VALUE);
    }

    // ---------- 异常吞没 ----------

    @Test
    void recordSwallowsInsertFailure()
    {
        doThrow(new IllegalStateException("table missing")).when(queryLogMapper)
                .insertKnowledgeQueryLog(any(AiKnowledgeQueryLog.class));

        recorder.record("试用期被辞退有补偿吗", KnowledgeQueryLogRecorder.SCENE_AGENT, null, "none", 3L);

        verify(queryLogMapper).insertKnowledgeQueryLog(any(AiKnowledgeQueryLog.class));
    }

    @Test
    void recordSwallowsExecutorRejection()
    {
        doThrow(new RejectedExecutionException("pool closed")).when(executor).execute(any(Runnable.class));

        recorder.record("试用期被辞退有补偿吗", KnowledgeQueryLogRecorder.SCENE_AGENT, null, "none", 3L);

        verifyNoInteractions(queryLogMapper);
    }

    // ---------- 转草稿标记 ----------

    @Test
    void markConvertedDelegatesToMapper()
    {
        recorder.markConverted("abc123");

        verify(queryLogMapper).markConverted("abc123");
    }

    @Test
    void markConvertedSkippedForEmptyHashOrDisabled()
    {
        recorder.markConverted(null);
        recorder.markConverted(" ");
        ReflectionTestUtils.setField(recorder, "enabled", false);
        recorder.markConverted("abc123");

        verify(queryLogMapper, never()).markConverted(any());
    }

    @Test
    void markConvertedSwallowsMapperFailure()
    {
        doThrow(new IllegalStateException("db down")).when(queryLogMapper).markConverted("abc123");

        recorder.markConverted("abc123");

        verify(queryLogMapper).markConverted("abc123");
    }

    // ---------- 线程池与关闭 ----------

    @Test
    void initRegistersManagedPool()
    {
        verify(registry).create(eq("ai-knowledge-query-log"), eq(1), eq(1), eq(2000),
                any(RejectedExecutionHandler.class));
    }

    @Test
    void rejectionHandlerCountsDroppedTasks()
    {
        ArgumentCaptor<RejectedExecutionHandler> handler = ArgumentCaptor.forClass(RejectedExecutionHandler.class);
        verify(registry).create(eq("ai-knowledge-query-log"), eq(1), eq(1), eq(2000), handler.capture());

        handler.getValue().rejectedExecution(() -> { }, mock(ThreadPoolExecutor.class));
        handler.getValue().rejectedExecution(() -> { }, mock(ThreadPoolExecutor.class));

        assertThat(recorder.getDroppedCount()).isEqualTo(2);
    }

    @Test
    void fallsBackToOwnPoolWithoutRegistry() throws Exception
    {
        KnowledgeQueryLogRecorder standalone = new KnowledgeQueryLogRecorder();
        AiKnowledgeQueryLogMapper mapper = mock(AiKnowledgeQueryLogMapper.class);
        ReflectionTestUtils.setField(standalone, "queryLogMapper", mapper);
        ReflectionTestUtils.setField(standalone, "enabled", true);

        standalone.init();
        standalone.record("产假工资怎么算", KnowledgeQueryLogRecorder.SCENE_OTHER, null, "none", 8L);

        verify(mapper, timeout(2000)).insertKnowledgeQueryLog(any(AiKnowledgeQueryLog.class));
        standalone.shutdown();
    }

    @Test
    void shutdownIsNoopBeforeInit()
    {
        new KnowledgeQueryLogRecorder().shutdown();
    }

    @Test
    void shutdownRepeatsSafely()
    {
        // 重复关闭不应抛异常（线程池 shutdown 本身幂等）
        recorder.shutdown();
        recorder.shutdown();

        verify(executor, org.mockito.Mockito.times(2)).shutdown();
    }

    private AiKnowledgeQueryLog captureSaved()
    {
        ArgumentCaptor<AiKnowledgeQueryLog> captor = ArgumentCaptor.forClass(AiKnowledgeQueryLog.class);
        verify(queryLogMapper).insertKnowledgeQueryLog(captor.capture());
        return captor.getValue();
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
