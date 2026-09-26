package ai.lawyers.framework.websocket.voice;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * DashScope 全双工 WebSocket 协议助手（P3-A2/A3）。
 *
 * <p>协议规范：wss://dashscope.aliyuncs.com/api-ws/v1/inference，
 * 请求头 Authorization: Bearer {apiKey}。
 * 客户端发送 JSON 文本指令 + 二进制 PCM 帧；服务端回 JSON 事件 + 二进制音频帧。</p>
 *
 * <p>本类仅负责消息构造与解析，不持有连接，线程安全。</p>
 *
 * @author ai-lawyers
 */
public final class DashScopeWsProtocol
{
    /** DashScope 全双工 WebSocket 端点 */
    public static final String WS_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

    /** 任务类型：语音识别 */
    public static final String TASK_ASR = "asr";

    /** 任务类型：语音合成 */
    public static final String TASK_TTS = "tts";

    /** ASR 功能：流式识别 */
    public static final String FUNCTION_RECOGNITION = "recognition";

    /** TTS 功能：流式合成 */
    public static final String FUNCTION_SPEECH_SYNTHESIZER = "SpeechSynthesizer";

    /** 事件：任务启动成功 */
    public static final String EVENT_TASK_STARTED = "task-started";

    /** 事件：结果生成（ASR 文本 / TTS 音频二进制） */
    public static final String EVENT_RESULT_GENERATED = "result-generated";

    /** 事件：任务完成 */
    public static final String EVENT_TASK_FINISHED = "task-finished";

    /** 事件：任务失败 */
    public static final String EVENT_TASK_FAILED = "task-failed";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DashScopeWsProtocol()
    {
    }

    /** 生成任务 ID（32 位十六进制，DashScope 要求） */
    public static String newTaskId()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构造 run-task 启动指令（JSON 文本帧）。
     *
     * @param taskId 任务 ID
     * @param taskGroup 任务组（audio）
     * @param task 任务类型（asr/tts）
     * @param function 功能名
     * @param model 模型名
     * @param parameters 模型参数（JSON 对象，可为 null）
     * @return JSON 字符串
     */
    public static String buildRunTask(String taskId, String taskGroup, String task,
            String function, String model, JsonNode parameters)
    {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "run-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");
        ObjectNode payload = root.putObject("payload");
        payload.put("task_group", taskGroup);
        payload.put("task", task);
        payload.put("function", function);
        payload.put("model", model);
        if (parameters != null && parameters.isObject())
        {
            payload.set("parameters", parameters);
        }
        else
        {
            payload.putObject("parameters");
        }
        payload.putObject("input");
        return root.toString();
    }

    /**
     * 构造 continue-task 指令（TTS 发送待合成文本）。
     *
     * @param taskId 任务 ID
     * @param text 待合成文本
     * @return JSON 字符串
     */
    public static String buildContinueTask(String taskId, String text)
    {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "continue-task");
        header.put("task_id", taskId);
        ObjectNode payload = root.putObject("payload");
        ObjectNode input = payload.putObject("input");
        input.put("text", text);
        return root.toString();
    }

    /**
     * 构造 finish-task 指令（结束输入）。
     *
     * @param taskId 任务 ID
     * @return JSON 字符串
     */
    public static String buildFinishTask(String taskId)
    {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "finish-task");
        header.put("task_id", taskId);
        root.putObject("payload").putObject("input");
        return root.toString();
    }

    /**
     * 解析服务端事件类型。
     *
     * @param json 服务端 JSON 文本帧
     * @return 事件名（task-started / result-generated / task-finished / task-failed），未知返回空串
     */
    public static String parseEvent(String json)
    {
        try
        {
            JsonNode root = MAPPER.readTree(json);
            JsonNode header = root.path("header");
            return header.path("event").asText("");
        }
        catch (Exception e)
        {
            return "";
        }
    }

    /**
     * 解析 ASR 结果（Sentence）。
     *
     * @param json result-generated 事件 JSON
     * @return Sentence 结果；无有效句返回 null
     */
    public static AsrSentence parseAsrSentence(String json)
    {
        try
        {
            JsonNode root = MAPPER.readTree(json);
            JsonNode sentence = root.path("payload").path("output").path("sentence");
            if (sentence.isMissingNode() || sentence.isNull())
            {
                return null;
            }
            AsrSentence s = new AsrSentence();
            s.text = sentence.path("text").asText("");
            JsonNode endTime = sentence.path("end_time");
            s.isEnd = !endTime.isNull() && endTime.isNumber();
            return s;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 解析 task-failed 错误信息。
     *
     * @param json task-failed 事件 JSON
     * @return 错误描述
     */
    public static String parseError(String json)
    {
        try
        {
            JsonNode root = MAPPER.readTree(json);
            JsonNode header = root.path("header");
            String code = header.path("error_code").asText("UNKNOWN");
            String msg = header.path("error_message").asText("");
            return code + ": " + msg;
        }
        catch (Exception e)
        {
            return "parse error: " + e.getMessage();
        }
    }

    /**
     * 构造 ASR 参数对象（format + sample_rate）。
     */
    public static ObjectNode buildAsrParameters(String format, int sampleRate)
    {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("format", format);
        p.put("sample_rate", sampleRate);
        return p;
    }

    /**
     * 构造 TTS 参数对象（text_type + voice + format + sample_rate）。
     */
    public static ObjectNode buildTtsParameters(String voice, String format, int sampleRate)
    {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("text_type", "PlainText");
        p.put("voice", voice);
        p.put("format", format);
        p.put("sample_rate", sampleRate);
        return p;
    }

    /** ASR 句子结果 */
    public static final class AsrSentence
    {
        public String text;
        /** true=本句结束（end_time 非空） */
        public boolean isEnd;
    }
}
