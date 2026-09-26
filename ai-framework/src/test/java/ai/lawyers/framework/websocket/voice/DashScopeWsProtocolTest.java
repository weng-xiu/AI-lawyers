package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link DashScopeWsProtocol} 协议构造/解析单测（P3-A2/A3，纯本地无外网）。
 *
 * @author ai-lawyers
 */
class DashScopeWsProtocolTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void newTaskId_shouldBe32Hex()
    {
        String id = DashScopeWsProtocol.newTaskId();
        assertThat(id).hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void buildRunTask_shouldContainActionModelAndParameters() throws Exception
    {
        JsonNode params = DashScopeWsProtocol.buildAsrParameters("pcm", 16000);
        String json = DashScopeWsProtocol.buildRunTask(
                "t1", "audio", DashScopeWsProtocol.TASK_ASR,
                DashScopeWsProtocol.FUNCTION_RECOGNITION, "paraformer-realtime-v2", params);
        JsonNode root = MAPPER.readTree(json);
        assertThat(root.path("header").path("action").asText()).isEqualTo("run-task");
        assertThat(root.path("header").path("task_id").asText()).isEqualTo("t1");
        assertThat(root.path("header").path("streaming").asText()).isEqualTo("duplex");
        JsonNode payload = root.path("payload");
        assertThat(payload.path("task_group").asText()).isEqualTo("audio");
        assertThat(payload.path("task").asText()).isEqualTo("asr");
        assertThat(payload.path("function").asText()).isEqualTo("recognition");
        assertThat(payload.path("model").asText()).isEqualTo("paraformer-realtime-v2");
        assertThat(payload.path("parameters").path("format").asText()).isEqualTo("pcm");
        assertThat(payload.path("parameters").path("sample_rate").asInt()).isEqualTo(16000);
        assertThat(payload.path("input").isObject()).isTrue();
    }

    @Test
    void buildContinueTask_shouldContainText() throws Exception
    {
        String json = DashScopeWsProtocol.buildContinueTask("t2", "你好");
        JsonNode root = MAPPER.readTree(json);
        assertThat(root.path("header").path("action").asText()).isEqualTo("continue-task");
        assertThat(root.path("header").path("task_id").asText()).isEqualTo("t2");
        assertThat(root.path("payload").path("input").path("text").asText()).isEqualTo("你好");
    }

    @Test
    void buildFinishTask_shouldContainAction() throws Exception
    {
        JsonNode root = MAPPER.readTree(DashScopeWsProtocol.buildFinishTask("t3"));
        assertThat(root.path("header").path("action").asText()).isEqualTo("finish-task");
        assertThat(root.path("header").path("task_id").asText()).isEqualTo("t3");
    }

    @Test
    void parseEvent_shouldExtractKnownEvents()
    {
        assertThat(DashScopeWsProtocol.parseEvent(
                "{\"header\":{\"event\":\"task-started\"}}"))
                .isEqualTo(DashScopeWsProtocol.EVENT_TASK_STARTED);
        assertThat(DashScopeWsProtocol.parseEvent(
                "{\"header\":{\"event\":\"result-generated\"}}"))
                .isEqualTo(DashScopeWsProtocol.EVENT_RESULT_GENERATED);
        assertThat(DashScopeWsProtocol.parseEvent(
                "{\"header\":{\"event\":\"task-finished\"}}"))
                .isEqualTo(DashScopeWsProtocol.EVENT_TASK_FINISHED);
        assertThat(DashScopeWsProtocol.parseEvent(
                "{\"header\":{\"event\":\"task-failed\"}}"))
                .isEqualTo(DashScopeWsProtocol.EVENT_TASK_FAILED);
    }

    @Test
    void parseEvent_shouldReturnEmptyOnBadJson()
    {
        assertThat(DashScopeWsProtocol.parseEvent("not-json")).isEmpty();
        assertThat(DashScopeWsProtocol.parseEvent("{\"foo\":1}")).isEmpty();
        assertThat(DashScopeWsProtocol.parseEvent("")).isEmpty();
    }

    @Test
    void parseAsrSentence_partialWhenEndTimeNull()
    {
        String json = "{\"payload\":{\"output\":{\"sentence\":{\"text\":\"你好\",\"end_time\":null}}}}";
        DashScopeWsProtocol.AsrSentence s = DashScopeWsProtocol.parseAsrSentence(json);
        assertThat(s).isNotNull();
        assertThat(s.text).isEqualTo("你好");
        assertThat(s.isEnd).isFalse();
    }

    @Test
    void parseAsrSentence_finalWhenEndTimeNumber()
    {
        String json = "{\"payload\":{\"output\":{\"sentence\":{\"text\":\"你好世界\",\"end_time\":1200}}}}";
        DashScopeWsProtocol.AsrSentence s = DashScopeWsProtocol.parseAsrSentence(json);
        assertThat(s).isNotNull();
        assertThat(s.text).isEqualTo("你好世界");
        assertThat(s.isEnd).isTrue();
    }

    @Test
    void parseAsrSentence_shouldReturnNullWhenNoSentence()
    {
        assertThat(DashScopeWsProtocol.parseAsrSentence("{\"payload\":{\"output\":{}}}")).isNull();
        assertThat(DashScopeWsProtocol.parseAsrSentence("bad")).isNull();
    }

    @Test
    void parseError_shouldCombineCodeAndMessage()
    {
        String json = "{\"header\":{\"error_code\":\"InvalidApiKey\",\"error_message\":\"bad key\"}}";
        assertThat(DashScopeWsProtocol.parseError(json)).isEqualTo("InvalidApiKey: bad key");
    }

    @Test
    void buildTtsParameters_shouldContainVoiceFormatSampleRate()
    {
        JsonNode p = DashScopeWsProtocol.buildTtsParameters("longxiaochun", "pcm_16000hz_mono_16bit", 16000);
        assertThat(p.path("text_type").asText()).isEqualTo("PlainText");
        assertThat(p.path("voice").asText()).isEqualTo("longxiaochun");
        assertThat(p.path("format").asText()).isEqualTo("pcm_16000hz_mono_16bit");
        assertThat(p.path("sample_rate").asInt()).isEqualTo(16000);
    }
}
