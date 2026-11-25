package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotResult;
import ai.lawyers.system.service.lawyers.voice.robot.VoiceRobotService;

/**
 * P3-E3：{@link VoiceRobotTurnRunner} 回合语义测试（P5 点名缺测）。
 *
 * <p>钉住"最新回合取代旧回合"、invalidate/shutdown 作废在途结果、话单ID仅服务端锚定一次、
 * answer_delta/answer_done 帧载荷与自动 TTS 播报、E2E 埋点仅记一次。</p>
 *
 * @author ai-lawyers
 */
class VoiceRobotTurnRunnerTest
{
    private static final String CONN = "c1";

    private static final String SESSION = "sess-1";

    private static final long WAIT_MS = 5000L;

    private final ObjectMapper mapper = new ObjectMapper();

    private VoiceRobotService robotService;

    private VoiceRobotTurnRunner.Host host;

    private HotlineMetrics metrics;

    private List<String> frames;

    private List<String> tts;

    private VoiceRobotTurnRunner runner;

    @BeforeEach
    void setUp()
    {
        robotService = mock(VoiceRobotService.class);
        host = mock(VoiceRobotTurnRunner.Host.class);
        metrics = mock(HotlineMetrics.class);
        frames = new CopyOnWriteArrayList<>();
        tts = new CopyOnWriteArrayList<>();
        doAnswer(inv -> frames.add(inv.getArgument(0, String.class))).when(host).send(anyString());
        doAnswer(inv -> tts.add(inv.getArgument(0, String.class))).when(host).startTts(anyString());
    }

    @AfterEach
    void tearDown()
    {
        if (runner != null)
        {
            runner.shutdown();
        }
    }

    // ---------- 装配可用性 ----------

    @Test
    void isAvailable_falseWhenServiceMissing()
    {
        runner = new VoiceRobotTurnRunner(null, host, CONN, SESSION, metrics);
        assertThat(runner.isAvailable()).isFalse();

        runner.submitTurn("你好");
        runner.shutdown();
        verifyNoInteractions(host);
    }

    @Test
    void isAvailable_trueWhenServicePresent()
    {
        runner = new VoiceRobotTurnRunner(robotService, host, CONN, SESSION, metrics);
        assertThat(runner.isAvailable()).isTrue();
    }

    // ---------- 回合产出 ----------

    @Test
    void submitTurn_emitsFramesAndStartsTts() throws Exception
    {
        VoiceRobotResult result = VoiceRobotResult.ok("可以依法起诉");
        result.setRagHits(2);
        result.setSources(List.of("劳动合同法 第三十条"));
        when(robotService.answer("欠薪如何起诉", SESSION, null)).thenReturn(result);

        newRunner();
        runner.submitTurn("欠薪如何起诉");
        awaitTrue(() -> tts.size() == 1);

        assertThat(frames).hasSize(2);
        JsonNode delta = mapper.readTree(frames.get(0));
        assertThat(delta.get("type").asText()).isEqualTo(VoiceFrames.T_ANSWER_DELTA);
        assertThat(delta.get("turnId").asLong()).isEqualTo(1L);
        assertThat(delta.get("text").asText()).isEqualTo("可以依法起诉");

        JsonNode done = mapper.readTree(frames.get(1));
        assertThat(done.get("type").asText()).isEqualTo(VoiceFrames.T_ANSWER_DONE);
        assertThat(done.get("turnId").asLong()).isEqualTo(1L);
        assertThat(done.get("ragHits").asInt()).isEqualTo(2);
        assertThat(done.get("sources").get(0).asText()).isEqualTo("劳动合同法 第三十条");
        assertThat(done.get("degraded").asBoolean()).isFalse();
        assertThat(tts).containsExactly("可以依法起诉");
    }

    @Test
    void submitTurn_degradedResult_marksAnswerDoneDegraded() throws Exception
    {
        when(robotService.answer("问题", SESSION, null))
                .thenReturn(VoiceRobotResult.degraded("请稍候转人工", "robot_disabled"));

        newRunner();
        runner.submitTurn("问题");
        awaitTrue(() -> tts.size() == 1);

        JsonNode done = mapper.readTree(frames.get(1));
        assertThat(done.get("degraded").asBoolean()).isTrue();
        assertThat(tts).containsExactly("请稍候转人工");
        // N6（语雀 7.8-6）：降级兜底计数，reason 透传
        verify(metrics).incrementVoiceDegrade("robot_disabled");
    }

    @Test
    void submitTurn_okResult_doesNotCountDegrade() throws Exception
    {
        when(robotService.answer("问题", SESSION, null)).thenReturn(VoiceRobotResult.ok("正常应答"));

        newRunner();
        runner.submitTurn("问题");
        awaitTrue(() -> tts.size() == 1);

        verify(metrics, never()).incrementVoiceDegrade(anyString());
    }

    @Test
    void staleDegradedTurn_discardedWithoutDegradeMetric() throws Exception
    {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(robotService.answer("旧问题", SESSION, null)).thenAnswer(inv ->
        {
            firstStarted.countDown();
            releaseFirst.await(WAIT_MS, TimeUnit.MILLISECONDS);
            // 旧回合虽降级，但结果产出时已失效（迟到），不计降级指标
            return VoiceRobotResult.degraded("旧兜底", "llm_error");
        });
        when(robotService.answer("新问题", SESSION, null)).thenReturn(VoiceRobotResult.ok("新应答"));

        newRunner();
        runner.submitTurn("旧问题");
        assertStarted(firstStarted);
        runner.submitTurn("新问题");
        releaseFirst.countDown();

        awaitTrue(() -> tts.size() == 1);
        verify(metrics, never()).incrementVoiceDegrade(anyString());
    }

    @Test
    void newerTurn_discardsStaleTurnResult() throws Exception
    {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(robotService.answer("旧问题", SESSION, null)).thenAnswer(inv ->
        {
            firstStarted.countDown();
            releaseFirst.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return VoiceRobotResult.ok("旧应答");
        });
        when(robotService.answer("新问题", SESSION, null)).thenReturn(VoiceRobotResult.ok("新应答"));

        newRunner();
        runner.submitTurn("旧问题");
        assertStarted(firstStarted);
        // 旧回合在途时提交新回合：activeTurn 递增，旧回合结果产出后被丢弃
        runner.submitTurn("新问题");
        releaseFirst.countDown();

        awaitTrue(() -> tts.size() == 1);
        assertThat(frames).hasSize(2);
        assertThat(mapper.readTree(frames.get(0)).get("turnId").asLong()).isEqualTo(2L);
        assertThat(mapper.readTree(frames.get(0)).get("text").asText()).isEqualTo("新应答");
        assertThat(tts).containsExactly("新应答");
    }

    @Test
    void invalidateTurn_discardsInFlightResult()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(robotService.answer("问题", SESSION, null)).thenAnswer(inv ->
        {
            started.countDown();
            release.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return VoiceRobotResult.ok("迟到应答");
        });

        newRunner();
        runner.submitTurn("问题");
        assertStarted(started);
        runner.invalidateTurn();
        release.countDown();

        verify(host, after(300).never()).send(anyString());
        verify(host, after(300).never()).startTts(anyString());
        assertThat(tts).isEmpty();
    }

    @Test
    void shutdown_discardsInFlightResult()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(robotService.answer("问题", SESSION, null)).thenAnswer(inv ->
        {
            started.countDown();
            release.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return VoiceRobotResult.ok("停机后应答");
        });

        newRunner();
        runner.submitTurn("问题");
        assertStarted(started);
        runner.shutdown();
        release.countDown();

        verify(host, after(300).never()).send(anyString());
        verify(host, after(300).never()).startTts(anyString());
    }

    @Test
    void answerFailure_emitsNothing()
    {
        when(robotService.answer("问题", SESSION, null)).thenThrow(new RuntimeException("llm down"));

        newRunner();
        runner.submitTurn("问题");

        verify(robotService, timeout((int) WAIT_MS)).answer("问题", SESSION, null);
        verify(host, after(300).never()).send(anyString());
        verify(host, after(300).never()).startTts(anyString());
    }

    // ---------- G6 话单ID锚定 ----------

    @Test
    void anchorRecordId_onlyFirstTrustedValueWins()
    {
        when(robotService.answer("问题", SESSION, 11L)).thenReturn(VoiceRobotResult.ok("应答"));

        newRunner();
        runner.anchorRecordId(null);
        runner.anchorRecordId(11L);
        // 浏览器/坐席链路再次设置必须无效（防伪造越权）
        runner.anchorRecordId(22L);
        runner.submitTurn("问题");
        awaitTrue(() -> tts.size() == 1);

        verify(robotService).answer("问题", SESSION, 11L);
    }

    // ---------- E2E 埋点 ----------

    @Test
    void markE2e_recordsOnceAfterTurnAndSkipsBefore()
    {
        when(robotService.answer("问题", SESSION, null)).thenReturn(VoiceRobotResult.ok("应答"));

        newRunner();
        runner.markE2eIfNeeded();
        verifyNoInteractions(metrics);

        runner.submitTurn("问题");
        awaitTrue(() -> tts.size() == 1);

        runner.markE2eIfNeeded();
        runner.markE2eIfNeeded();
        verify(metrics, times(1)).recordVoiceE2eFirstMs(anyLong(), eq("robot"), eq(SESSION));
    }

    @Test
    void markE2e_nullMetrics_doesNotThrow()
    {
        when(robotService.answer("问题", SESSION, null)).thenReturn(VoiceRobotResult.ok("应答"));

        runner = new VoiceRobotTurnRunner(robotService, host, CONN, SESSION, null);
        runner.submitTurn("问题");
        awaitTrue(() -> tts.size() == 1);

        runner.markE2eIfNeeded();
    }

    private VoiceRobotTurnRunner newRunner()
    {
        runner = new VoiceRobotTurnRunner(robotService, host, CONN, SESSION, metrics);
        return runner;
    }

    private void assertStarted(CountDownLatch latch)
    {
        try
        {
            assertThat(latch.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待回合启动被中断", e);
        }
    }

    private static void awaitTrue(BooleanSupplier condition)
    {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline)
        {
            if (condition.getAsBoolean())
            {
                return;
            }
            try
            {
                Thread.sleep(10L);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待异步结果被中断", e);
            }
        }
        throw new AssertionError("等待超时");
    }
}
