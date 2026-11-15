package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistResult;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotAssistService;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotLaw;
import ai.lawyers.system.service.lawyers.voice.copilot.CopilotTicket;

/**
 * F4：{@link VoiceCopilotAssistant} 触发语义测试（P5 点名缺测）。
 *
 * <p>钉住最小字数/8s 节流/forceFlush 强制补一回合、滚动窗口 3000 字截尾、
 * stop/shutdown 作废迟到结果、五类辅助帧下发与坐席画像 ID 透传。</p>
 *
 * @author ai-lawyers
 */
class VoiceCopilotAssistantTest
{
    private static final String CONN = "c1";

    private static final String SESSION = "sess-1";

    private static final Long AGENT_ID = 123L;

    private static final long WAIT_MS = 5000L;

    /** 达到最小字数的文本（30 字） */
    private static final String LONG_TEXT = "012345678901234567890123456789";

    private final ObjectMapper mapper = new ObjectMapper();

    private CopilotAssistService service;

    private VoiceCopilotAssistant.Host host;

    private List<String> frames;

    private VoiceCopilotAssistant assistant;

    @BeforeEach
    void setUp()
    {
        service = mock(CopilotAssistService.class);
        host = mock(VoiceCopilotAssistant.Host.class);
        frames = new CopyOnWriteArrayList<>();
        doAnswer(inv -> frames.add(inv.getArgument(0, String.class))).when(host).send(anyString());
    }

    @AfterEach
    void tearDown()
    {
        if (assistant != null)
        {
            assistant.shutdown();
        }
    }

    // ---------- 触发门槛 ----------

    @Test
    void queue_noService_ignored()
    {
        assistant = new VoiceCopilotAssistant(null, host, CONN, SESSION, AGENT_ID);
        assistant.queue(LONG_TEXT);
        assistant.forceFlush();

        verifyNoInteractions(host);
    }

    @Test
    void queue_blankText_ignored()
    {
        newAssistant();
        assistant.queue(null);
        assistant.queue("   ");

        verifyNoInteractions(service);
    }

    @Test
    void queue_belowMinChars_notSubmitted()
    {
        newAssistant();
        assistant.queue("好的");

        verify(service, after(200).never()).assist(anyString(), anyString(), eq(AGENT_ID));
    }

    @Test
    void queue_reachesMinChars_submitsWithAgentId()
    {
        stubResult(CopilotAssistResult.empty());
        newAssistant();

        assistant.queue("  " + LONG_TEXT + "  ");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(service, timeout((int) WAIT_MS)).assist(text.capture(), eq(SESSION), eq(AGENT_ID));
        assertThat(text.getValue()).isEqualTo(LONG_TEXT);
    }

    // ---------- 节流 ----------

    @Test
    void queue_withinInterval_doesNotSubmitAgain()
    {
        stubResult(CopilotAssistResult.empty());
        newAssistant();

        assistant.queue(LONG_TEXT);
        verify(service, timeout((int) WAIT_MS)).assist(anyString(), eq(SESSION), eq(AGENT_ID));
        // 缓冲已超阈值，但距上次触发不足 8s：不再触发
        assistant.queue(LONG_TEXT);

        verify(service, after(200).times(1)).assist(anyString(), anyString(), eq(AGENT_ID));
    }

    @Test
    void queue_afterInterval_submitsAgain()
    {
        stubResult(CopilotAssistResult.empty());
        newAssistant();

        assistant.queue(LONG_TEXT);
        verify(service, timeout((int) WAIT_MS)).assist(anyString(), eq(SESSION), eq(AGENT_ID));
        ReflectionTestUtils.setField(assistant, "lastMs", System.currentTimeMillis() - 8001L);
        assistant.queue(LONG_TEXT);

        verify(service, timeout((int) WAIT_MS).times(2))
                .assist(anyString(), eq(SESSION), eq(AGENT_ID));
    }

    // ---------- forceFlush / 滚动窗口 ----------

    @Test
    void forceFlush_shortBuffer_submitsDespiteThresholds()
    {
        stubResult(CopilotAssistResult.empty());
        newAssistant();

        assistant.queue("短文本");
        assistant.forceFlush();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(service, timeout((int) WAIT_MS)).assist(text.capture(), eq(SESSION), eq(AGENT_ID));
        assertThat(text.getValue()).isEqualTo("短文本");
    }

    @Test
    void forceFlush_emptyBuffer_noop()
    {
        newAssistant();
        assistant.forceFlush();

        verify(service, after(200).never()).assist(anyString(), anyString(), eq(AGENT_ID));
    }

    @Test
    void queue_rollingWindow_keepsTail3000Chars()
    {
        stubResult(CopilotAssistResult.empty());
        newAssistant();
        String text = "A".repeat(3000) + "B".repeat(500);

        assistant.queue(text);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(service, timeout((int) WAIT_MS)).assist(payload.capture(), eq(SESSION), eq(AGENT_ID));
        assertThat(payload.getValue()).hasSize(3000).isEqualTo(text.substring(text.length() - 3000));
    }

    // ---------- 结果下发 ----------

    @Test
    void turn_emitsFiveCopilotFrames() throws Exception
    {
        CopilotAssistResult result = CopilotAssistResult.empty();
        result.setDisputeType("劳动争议");
        result.setClaims(Collections.singletonList("追讨欠薪"));
        result.setUrgency("urgent");
        result.setKeyFacts(Collections.singletonList("欠薪三个月"));
        result.setLaws(Collections.singletonList(
                new CopilotLaw(77L, "劳动合同法", "第三十条", "法律法规库")));
        result.setTickets(Collections.singletonList(
                new CopilotTicket(9L, "T-9", "工资争议", "0", "片段")));
        result.setActions(Collections.singletonList(new CopilotAssistResult.SuggestedAction(
                "createTicket", "建工单", Collections.singletonMap("title", "工资"))));
        result.setSuggestedScript("建议先协商，协商不成申请仲裁");
        stubResult(result);
        newAssistant();

        assistant.queue(LONG_TEXT);
        awaitTrue(() -> frames.size() == 5);

        List<String> types = Arrays.asList(
                VoiceFrames.T_COPILOT_ELEMENT, VoiceFrames.T_COPILOT_LAWS,
                VoiceFrames.T_COPILOT_TICKETS, VoiceFrames.T_COPILOT_ACTIONS,
                VoiceFrames.T_COPILOT_SCRIPT);
        for (int i = 0; i < types.size(); i++)
        {
            JsonNode frame = mapper.readTree(frames.get(i));
            assertThat(frame.get("type").asText()).isEqualTo(types.get(i));
            assertThat(frame.get("seq").asLong()).isEqualTo(1L);
        }

        JsonNode element = mapper.readTree(frames.get(0));
        assertThat(element.get("disputeType").asText()).isEqualTo("劳动争议");
        assertThat(element.get("claims").get(0).asText()).isEqualTo("追讨欠薪");
        assertThat(element.get("urgency").asText()).isEqualTo("urgent");
        assertThat(element.get("keyFacts").get(0).asText()).isEqualTo("欠薪三个月");
        assertThat(element.get("degraded").asBoolean()).isFalse();

        assertThat(mapper.readTree(frames.get(1)).get("laws").get(0).get("chunkId").asLong()).isEqualTo(77L);
        assertThat(mapper.readTree(frames.get(2)).get("tickets").get(0).get("ticketNo").asText()).isEqualTo("T-9");
        assertThat(mapper.readTree(frames.get(3)).get("actions").get(0).get("action").asText())
                .isEqualTo("createTicket");
        assertThat(mapper.readTree(frames.get(4)).get("script").asText())
                .isEqualTo("建议先协商，协商不成申请仲裁");
    }

    @Test
    void assistFailure_emitsNothing()
    {
        when(service.assist(anyString(), anyString(), eq(AGENT_ID)))
                .thenThrow(new RuntimeException("llm down"));
        newAssistant();

        assistant.queue(LONG_TEXT);

        verify(service, timeout((int) WAIT_MS)).assist(anyString(), anyString(), eq(AGENT_ID));
        verify(host, after(300).never()).send(anyString());
    }

    // ---------- 作废 ----------

    @Test
    void invalidate_discardsInFlightResult()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.assist(anyString(), anyString(), eq(AGENT_ID))).thenAnswer(inv ->
        {
            started.countDown();
            release.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return CopilotAssistResult.empty();
        });
        newAssistant();

        assistant.queue(LONG_TEXT);
        assertStarted(started);
        assistant.invalidate();
        release.countDown();

        verify(host, after(300).never()).send(anyString());
        assertThat(frames).isEmpty();
    }

    @Test
    void shutdown_discardsInFlightResult()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.assist(anyString(), anyString(), eq(AGENT_ID))).thenAnswer(inv ->
        {
            started.countDown();
            release.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return CopilotAssistResult.empty();
        });
        newAssistant();

        assistant.queue(LONG_TEXT);
        assertStarted(started);
        assistant.shutdown();
        release.countDown();

        verify(host, after(300).never()).send(anyString());
    }

    private void stubResult(CopilotAssistResult result)
    {
        when(service.assist(anyString(), eq(SESSION), eq(AGENT_ID))).thenReturn(result);
    }

    private void newAssistant()
    {
        assistant = new VoiceCopilotAssistant(service, host, CONN, SESSION, AGENT_ID);
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
            throw new AssertionError("等待辅助回合启动被中断", e);
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
