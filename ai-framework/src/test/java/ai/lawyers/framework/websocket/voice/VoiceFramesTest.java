package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * /ws/voice 下发帧构造测试：帧类型、字段顺序稳定性、可空字段兜底
 * （空列表/空串/缺省 reason/缺省 warningId）、TTS 末片判定与 PCM Base64。
 *
 * @author ai-lawyers
 */
class VoiceFramesTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> parse(String json) throws Exception
    {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @Test
    void connected帧字段与顺序() throws Exception
    {
        String json = VoiceFrames.connected("s-1", "agent");

        assertThat(json).startsWith("{\"type\":\"connected\"");
        Map<String, Object> frame = parse(json);
        assertThat(frame.get("type")).isEqualTo(VoiceFrames.T_CONNECTED);
        assertThat(frame.get("sessionId")).isEqualTo("s-1");
        assertThat(frame.get("role")).isEqualTo("agent");
    }

    @Test
    void started帧包含引擎与采样参数() throws Exception
    {
        Map<String, Object> frame = parse(VoiceFrames.started("s-1", "agent", "mock", "pcm", 16000));

        assertThat(frame.get("type")).isEqualTo(VoiceFrames.T_STARTED);
        assertThat(frame.get("engine")).isEqualTo("mock");
        assertThat(frame.get("format")).isEqualTo("pcm");
        assertThat(frame.get("sampleRate")).isEqualTo(16000);
    }

    @Test
    void asr帧区分增量与终态类型() throws Exception
    {
        Map<String, Object> partial = parse(VoiceFrames.asr(VoiceFrames.T_ASR_PARTIAL, 3L, "我想咨询"));
        Map<String, Object> done = parse(VoiceFrames.asr(VoiceFrames.T_ASR_FINAL, 4L, "我想咨询劳动仲裁"));

        assertThat(partial.get("type")).isEqualTo("asr_partial");
        assertThat(partial.get("text")).isEqualTo("我想咨询");
        assertThat(done.get("type")).isEqualTo("asr_final");
        assertThat(done.get("seq")).isEqualTo(4);
    }

    @Test
    void ttsAudio末片标记与PCM编码() throws Exception
    {
        byte[] pcm = new byte[]{1, 2, 3};
        Map<String, Object> first = parse(VoiceFrames.ttsAudio(0L, 2, 16000, pcm));
        Map<String, Object> last = parse(VoiceFrames.ttsAudio(1L, 2, 16000, pcm));

        assertThat(first.get("type")).isEqualTo(VoiceFrames.T_TTS_AUDIO);
        assertThat(first.get("isEnd")).isEqualTo(false);
        assertThat(last.get("isEnd")).isEqualTo(true);
        assertThat(first.get("data")).isEqualTo(Base64.getEncoder().encodeToString(pcm));
        assertThat(first.get("format")).isEqualTo("pcm");
    }

    @Test
    void ttsEnd按需携带中断原因() throws Exception
    {
        Map<String, Object> interrupted = parse(VoiceFrames.ttsEnd(true, "barge-in"));
        Map<String, Object> normal = parse(VoiceFrames.ttsEnd(false, null));

        assertThat(interrupted.get("interrupted")).isEqualTo(true);
        assertThat(interrupted.get("reason")).isEqualTo("barge-in");
        assertThat(normal.get("interrupted")).isEqualTo(false);
        assertThat(normal).doesNotContainKey("reason");
    }

    @Test
    void error与vad帧() throws Exception
    {
        Map<String, Object> error = parse(VoiceFrames.error("ASR_FAIL", "识别失败"));
        Map<String, Object> vad = parse(VoiceFrames.vad(VoiceFrames.T_VAD_SPEECH_START));

        assertThat(error.get("type")).isEqualTo(VoiceFrames.T_ERROR);
        assertThat(error.get("code")).isEqualTo("ASR_FAIL");
        assertThat(error.get("message")).isEqualTo("识别失败");
        assertThat(vad.get("type")).isEqualTo("vad_speech_start");
        assertThat(vad).hasSize(1);
    }

    @Test
    void answerDelta与answerDone() throws Exception
    {
        Map<String, Object> delta = parse(VoiceFrames.answerDelta(7L, 1L, "您好"));
        Map<String, Object> done = parse(VoiceFrames.answerDone(7L, 2, null, true));

        assertThat(delta.get("type")).isEqualTo(VoiceFrames.T_ANSWER_DELTA);
        assertThat(delta.get("turnId")).isEqualTo(7);
        assertThat(delta.get("text")).isEqualTo("您好");
        assertThat(done.get("type")).isEqualTo(VoiceFrames.T_ANSWER_DONE);
        assertThat(done.get("ragHits")).isEqualTo(2);
        assertThat(done.get("sources")).isEqualTo(Collections.emptyList());
        assertThat(done.get("degraded")).isEqualTo(true);
    }

    @Test
    void emotion按需携带意图与预警ID() throws Exception
    {
        Map<String, Object> full = parse(VoiceFrames.emotion("urgent", "complaint",
                Collections.singletonList("投诉"), 9L));
        Map<String, Object> minimal = parse(VoiceFrames.emotion("negative", null, null, null));

        assertThat(full.get("level")).isEqualTo("urgent");
        assertThat(full.get("intent")).isEqualTo("complaint");
        assertThat(full.get("keywords")).isEqualTo(Collections.singletonList("投诉"));
        assertThat(full.get("warningId")).isEqualTo(9);
        assertThat(minimal).doesNotContainKey("intent");
        assertThat(minimal).doesNotContainKey("warningId");
        assertThat(minimal.get("keywords")).isEqualTo(Collections.emptyList());
    }

    @Test
    void copilotElement空值兜底() throws Exception
    {
        Map<String, Object> frame = parse(VoiceFrames.copilotElement(1L, null, null, null, null, true));

        assertThat(frame.get("type")).isEqualTo(VoiceFrames.T_COPILOT_ELEMENT);
        assertThat(frame.get("disputeType")).isEqualTo("");
        assertThat(frame.get("claims")).isEqualTo(Collections.emptyList());
        assertThat(frame.get("urgency")).isEqualTo("normal");
        assertThat(frame.get("keyFacts")).isEqualTo(Collections.emptyList());
        assertThat(frame.get("degraded")).isEqualTo(true);
    }

    @Test
    void copilot列表帧空值兜底() throws Exception
    {
        Map<String, Object> laws = parse(VoiceFrames.copilotLaws(2L, null));
        Map<String, Object> tickets = parse(VoiceFrames.copilotTickets(2L, null));
        Map<String, Object> actions = parse(VoiceFrames.copilotActions(2L, null));

        assertThat(laws.get("laws")).isEqualTo(Collections.emptyList());
        assertThat(tickets.get("tickets")).isEqualTo(Collections.emptyList());
        assertThat(actions.get("actions")).isEqualTo(Collections.emptyList());
    }

    @Test
    void copilotLaws透传法条条目() throws Exception
    {
        List<Map<String, Object>> laws = new ArrayList<>();
        Map<String, Object> law = new HashMap<>();
        law.put("chunkId", 11L);
        law.put("title", "劳动合同法");
        laws.add(law);

        Map<String, Object> frame = parse(VoiceFrames.copilotLaws(3L, laws));

        assertThat(frame.get("seq")).isEqualTo(3);
        assertThat((List<?>) frame.get("laws")).hasSize(1);
    }

    @Test
    void copilotScript空值兜底为空串() throws Exception
    {
        Map<String, Object> frame = parse(VoiceFrames.copilotScript(4L, null));

        assertThat(frame.get("type")).isEqualTo(VoiceFrames.T_COPILOT_SCRIPT);
        assertThat(frame.get("script")).isEqualTo("");
    }
}
