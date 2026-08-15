package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrExecutionLog;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IntentionMatchResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrEdgeService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrExecutionLogService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;
import ai.lawyers.system.service.lawyers.ivr.engine.IntentionRecognitionService;
import ai.lawyers.system.service.lawyers.voice.VoiceEngineManager;
import ai.lawyers.system.service.lawyers.voice.VoiceModelEnum;

/**
 * IVR 流程执行引擎实现。
 *
 * 支持节点类型（与前端设计器画板对齐）：
 *  start / say / answer(ASR收声) / menu / received(DTMF收号) / intention / condition /
 *  sentiment(情绪) / extract(信息抽取) / service(HTTP调用) / script(JS脚本) / child(子流程) /
 *  agent(转人工) / transfer(转外线) / variable / hangup
 *
 *  SmartCall 节点语义：
 *  - 文本类配置支持 ${表达式} SpEL 模板（如 ${lastInput}）；
 *  - 条件路由使用 SpEL 表达式，可引用流程变量（dtmf、matchedIntention、sentiment 等）。
 *
 */
@Service
public class IvrEngineServiceImpl implements IIvrEngineService
{
    private static final Logger log = LoggerFactory.getLogger(IvrEngineServiceImpl.class);

    private static final int MAX_STEPS = 200;

    private static final String NODE_START = "start";
    private static final String NODE_SAY = "say";
    private static final String NODE_MENU = "menu";
    private static final String NODE_DTMF = "dtmf";
    private static final String NODE_ANSWER = "answer";
    private static final String NODE_RECEIVED = "received";
    private static final String NODE_INTENTION = "intention";
    private static final String NODE_SENTIMENT = "sentiment";
    private static final String NODE_EXTRACT = "extract";
    private static final String NODE_SERVICE = "service";
    private static final String NODE_SCRIPT = "script";
    private static final String NODE_CHILD = "child";
    private static final String NODE_CONDITION = "condition";
    private static final String NODE_AGENT = "agent";
    private static final String NODE_TRANSFER = "transfer";
    private static final String NODE_HANGUP = "hangup";
    private static final String NODE_VARIABLE = "variable";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /** 子流程最大嵌套深度，防止循环引用 */
    private static final int MAX_CHILD_DEPTH = 3;
    private static final String CHILD_DEPTH_KEY = "__childDepth";

    @Autowired
    private IAiIvrFlowService flowService;

    @Autowired
    private IAiIvrNodeService nodeService;

    @Autowired
    private IAiIvrEdgeService edgeService;

    @Autowired
    private IAiIvrExecutionLogService executionLogService;

    @Autowired
    private IAiCallRecordService callRecordService;

    @Autowired
    private IntentionRecognitionService intentionRecognitionService;

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private VoiceEngineManager voiceEngineManager;

    @Override
    public IvrExecuteResult executeFlow(IvrExecuteRequest request)
    {
        IvrExecuteResult result = new IvrExecuteResult();
        AiIvrFlow flow = resolveFlow(request.getFlowId());
        if (flow == null)
        {
            return failResult(result, "FLOW_NOT_FOUND", "未找到可执行的IVR流程" +
                    (request.getFlowId() == null ? "（默认流程不存在）" : "（flowId=" + request.getFlowId() + "）"));
        }

        List<AiIvrNode> nodes = nodeService.selectAiIvrNodeByFlowId(flow.getFlowId());
        List<AiIvrEdge> edges = edgeService.selectAiIvrEdgeByFlowId(flow.getFlowId());
        if (nodes == null || nodes.isEmpty())
        {
            return failResult(result, "FLOW_EMPTY", "流程[" + flow.getFlowName() + "]未配置节点，请先在设计器中保存");
        }

        AiIvrNode startNode = null;
        Map<Long, AiIvrNode> nodeMap = new HashMap<>();
        for (AiIvrNode node : nodes)
        {
            nodeMap.put(node.getNodeId(), node);
            if (NODE_START.equalsIgnoreCase(node.getNodeType()))
            {
                startNode = node;
            }
        }
        if (startNode == null)
        {
            return failResult(result, "NO_START_NODE", "流程[" + flow.getFlowName() + "]缺少开始节点");
        }

        // 会话与执行日志
        String sessionId = StringUtils.isNotEmpty(request.getSessionId())
                ? request.getSessionId() : "IVR-" + UUID.randomUUID().toString().replace("-", "");
        AiIvrExecutionLog execLog = new AiIvrExecutionLog();
        execLog.setRecordId(request.getRecordId());
        execLog.setSessionId(sessionId);
        execLog.setFlowId(flow.getFlowId());
        execLog.setFlowName(flow.getFlowName());
        execLog.setStatus("0");
        execLog.setStartTime(new Date());
        executionLogService.insertAiIvrExecutionLog(execLog);

        // 流程变量
        Map<String, Object> variables = new LinkedHashMap<>();
        if (request.getVariables() != null)
        {
            variables.putAll(request.getVariables());
        }
        variables.put("callerNumber", request.getCallerNumber());
        variables.put("calleeNumber", request.getCalleeNumber());

        Deque<String> inputs = new ArrayDeque<>(request.getInputs() == null
                ? new ArrayList<>() : request.getInputs());

        result.setSuccess(true);
        result.setCode("OK");
        result.setSessionId(sessionId);
        result.setExecId(execLog.getExecId());
        result.setFlowId(flow.getFlowId());
        result.setFlowName(flow.getFlowName());
        result.setRecordId(request.getRecordId());
        result.setStatus("1");
        result.setVariables(variables);

        AiIvrNode current = startNode;
        int stepCount = 0;
        while (current != null && stepCount < MAX_STEPS)
        {
            stepCount++;
            String type = current.getNodeType() == null ? "" : current.getNodeType().toLowerCase();
            IvrNodeStep step = new IvrNodeStep(current.getNodeId(), type, current.getNodeName(), "", "");

            switch (type)
            {
                case NODE_START:
                    step.setAction("START");
                    step.setDetail("流程开始");
                    result.getSteps().add(step);
                    current = nextNode(current, nodeMap, edges, variables, null);
                    break;

                case NODE_SAY:
                    String text = render(configText(current, "text"), variables);
                    if (StringUtils.isEmpty(text) && current.getNodeConfig() != null
                            && current.getNodeConfig().contains("\"play\""))
                    {
                        text = render(configText(current, "play"), variables);
                    }
                    if (StringUtils.isEmpty(text))
                    {
                        text = current.getNodeName();
                    }
                    text = render(text, variables);
                    variables.put("lastSay", text);
                    step.setAction("PLAY");
                    String ttsNote = synthSay(current, text, variables);
                    step.setDetail("语音播报：" + text + ttsNote);
                    result.getSteps().add(step);
                    current = nextNode(current, nodeMap, edges, variables, null);
                    break;

                case NODE_MENU:
                case NODE_DTMF:
                    String rawInput = pollInput(inputs, variables);
                    String dtmf = rawInput == null ? "" : rawInput.trim();
                    if (dtmf.length() > 1)
                    {
                        dtmf = dtmf.substring(0, 1);
                    }
                    variables.put("dtmf", dtmf);
                    variables.put("lastInput", rawInput == null ? "" : rawInput);
                    step.setAction("INPUT");
                    step.setDetail("按键/语音输入：" + dtmf);
                    result.getSteps().add(step);
                    current = nextNode(current, nodeMap, edges, variables, dtmf);
                    break;

                case NODE_ANSWER:
                    String askText = render(configText(current, "jqrask"), variables);
                    if (StringUtils.isEmpty(askText))
                    {
                        askText = render(configText(current, "prompt"), variables);
                    }
                    if (StringUtils.isNotEmpty(askText))
                    {
                        variables.put("lastSay", askText);
                    }
                    String voiceInput = pollInput(inputs, variables);
                    variables.put("dtmf", voiceInput == null ? "" : voiceInput.trim());
                    variables.put("lastInput", voiceInput == null ? "" : voiceInput);
                    step.setAction("ASR_INPUT");
                    step.setDetail((StringUtils.isEmpty(askText) ? "" : "询问语：" + askText + "；")
                            + "语音输入：" + (voiceInput == null ? "" : voiceInput));
                    result.getSteps().add(step);
                    current = nextNode(current, nodeMap, edges, variables,
                            voiceInput == null ? null : voiceInput.trim());
                    break;

                case NODE_RECEIVED:
                    String receivedPrompt = render(configText(current, "jqrask"), variables);
                    if (StringUtils.isEmpty(receivedPrompt))
                    {
                        receivedPrompt = render(configText(current, "prompt"), variables);
                    }
                    if (StringUtils.isNotEmpty(receivedPrompt))
                    {
                        variables.put("lastSay", receivedPrompt);
                    }
                    String receivedInput = pollInput(inputs, variables);
                    String digits = receivedInput == null ? "" : receivedInput.trim();
                    JsonNode receivedConfig = config(current);
                    String endKey = receivedConfig.path("endKey").asText(
                            receivedConfig.path("end").asText("#"));
                    if (StringUtils.isNotEmpty(endKey) && digits.endsWith(endKey))
                    {
                        digits = digits.substring(0, digits.length() - endKey.length());
                    }
                    int maxDigits = receivedConfig.path("maxDigits").asInt(-1);
                    if (maxDigits > 0 && digits.length() > maxDigits)
                    {
                        digits = digits.substring(0, maxDigits);
                    }
                    variables.put("dtmf", digits);
                    variables.put("receivedDigits", digits);
                    variables.put("lastInput", digits);
                    step.setAction("DTMF_INPUT");
                    step.setDetail((StringUtils.isEmpty(receivedPrompt) ? "" : "收号语：" + receivedPrompt + "；")
                            + "按键输入：" + digits);
                    result.getSteps().add(step);
                    current = nextNode(current, nodeMap, edges, variables, digits);
                    break;

                case NODE_INTENTION:
                    String intentText = render(configText(current, "question"), variables);
                    if (StringUtils.isEmpty(intentText))
                    {
                        intentText = pollInput(inputs, variables);
                    }
                    if (StringUtils.isEmpty(intentText))
                    {
                        Object lastInputValue = variables.get("lastInput");
                        intentText = lastInputValue == null ? "" : lastInputValue.toString();
                    }
                    IntentionMatchResult match = intentionRecognitionService.recognize(intentText,
                            request.getRecordId(), sessionId, flow.getFlowId(), current.getNodeId());
                    variables.put("matchedIntention", match == null || !match.isMatched() ? "" : match.getIntentionCode());
                    variables.put("matchedIntentionName", match == null || !match.isMatched() ? "" : match.getIntentionName());
                    if (match != null && match.isMatched())
                    {
                        result.setMatchedIntention(match.getIntentionCode());
                        result.setMatchedIntentionName(match.getIntentionName());
                        result.setMatchMethod(match.getMatchMethod());
                        result.setCategoryId(match.getCategoryId());
                        result.setCategoryName(match.getCategoryName());
                        if (match.getCategoryId() != null)
                        {
                            variables.put("matchedCategoryId", match.getCategoryId());
                            variables.put("matchedCategoryName", match.getCategoryName());
                        }
                        step.setAction("INTENTION");
                        step.setDetail("意图识别[" + match.getMatchMethod() + "]：" + match.getIntentionName()
                                + "(" + match.getIntentionCode() + ")");
                    }
                    else
                    {
                        step.setAction("INTENTION");
                        step.setDetail("意图识别：未命中");
                    }
                    result.getSteps().add(step);
                    current = nextNode(current, nodeMap, edges, variables, null);
                    break;

                case NODE_SENTIMENT:
                    current = executeSentiment(current, nodeMap, edges, variables, step, result);
                    break;

                case NODE_CONDITION:
                    AiIvrEdge chosen = routeEdge(current, edges, variables, null);
                    String chosenLabel = chosen == null ? "无命中(默认/无连线)" : labelOf(chosen);
                    step.setAction("CONDITION");
                    step.setDetail("条件分支：" + chosenLabel);
                    result.getSteps().add(step);
                    current = chosen == null ? null : nodeMap.get(chosen.getTargetNodeId());
                    break;

                case NODE_AGENT:
                case NODE_TRANSFER:
                    String target = transferTarget(current, variables);
                    result.setTransferTarget(target);
                    step.setAction("TRANSFER");
                    step.setDetail((NODE_AGENT.equals(type) ? "转人工坐席：" : "转外线：") + target);
                    result.getSteps().add(step);
                    current = null;
                    break;

                case NODE_HANGUP:
                    step.setAction("HANGUP");
                    step.setDetail("挂断" + (StringUtils.isNotEmpty(current.getNodeName()) ? "（" + current.getNodeName() + "）" : ""));
                    result.getSteps().add(step);
                    current = null;
                    break;

                case NODE_VARIABLE:
                    assignVariable(current, variables);
                    step.setAction("VARIABLE");
                    step.setDetail("变量赋值：" + current.getNodeName());
                    result.getSteps().add(step);
                    current = nextNode(current, nodeMap, edges, variables, null);
                    break;

                case NODE_EXTRACT:
                    current = executeExtract(current, nodeMap, edges, variables, step, result);
                    break;

                case NODE_SERVICE:
                    current = executeService(current, nodeMap, edges, variables, step, result);
                    break;

                case NODE_SCRIPT:
                    current = executeScript(current, nodeMap, edges, variables, step, result);
                    break;

                case NODE_CHILD:
                    current = executeChild(current, nodeMap, edges, variables, inputs, request, step, result);
                    break;

                default:
                    step.setAction("ERROR");
                    step.setDetail("未知节点类型：" + type);
                    result.getSteps().add(step);
                    finishLog(execLog, result, "2", "未知节点类型：" + type);
                    result.setSuccess(false);
                    result.setCode("UNSUPPORTED_NODE");
                    result.setMessage("流程包含不支持节点类型：" + type);
                    result.setStatus("2");
                    result.setCurrentNodeId(current.getNodeId());
                    result.setCurrentNodeType(type);
                    result.setCurrentNodeName(current.getNodeName());
                    updateRecord(flow, result);
                    return result;
            }

            result.setCurrentNodeId(current == null ? null : current.getNodeId());
            result.setCurrentNodeType(current == null ? null : current.getNodeType());
            result.setCurrentNodeName(current == null ? null : current.getNodeName());
            result.setVariables(variables);

            // 每步回写执行日志（当前节点 + 变量快照）
            try
            {
                AiIvrExecutionLog update = new AiIvrExecutionLog();
                update.setExecId(execLog.getExecId());
                update.setCurrentNodeId(current == null ? null : current.getNodeId());
                update.setCurrentNodeType(current == null ? null : current.getNodeType());
                update.setCurrentNodeName(current == null ? null : current.getNodeName());
                update.setVariables(MAPPER.writeValueAsString(variables));
                update.setExecuteResult(MAPPER.writeValueAsString(result.getSteps()));
                executionLogService.updateAiIvrExecutionLog(update);
            }
            catch (Exception e)
            {
                log.debug("IVR执行日志回写失败: {}", e.getMessage());
            }
        }

        if (stepCount >= MAX_STEPS)
        {
            result.setSuccess(false);
            result.setCode("LOOP_LIMIT");
            result.setMessage("流程超过最大执行步数限制(" + MAX_STEPS + ")，疑似存在循环");
            result.setStatus("2");
            finishLog(execLog, result, "2", result.getMessage());
            updateRecord(flow, result);
            return result;
        }

        finishLog(execLog, result, "1", null);
        updateRecord(flow, result);
        return result;
    }

    @Override
    public AiIvrFlow resolveFlowForCaller(String callerNumber)
    {
        // 扩展点：后续可按号码段/技能组绑定具体流程；当前按默认流程兜底
        return flowService.selectDefaultFlow();
    }

    // ------------------------------------------------------------------ 内部实现

    private AiIvrFlow resolveFlow(Long flowId)
    {
        if (flowId != null)
        {
            return flowService.selectAiIvrFlowByFlowId(flowId);
        }
        return flowService.selectDefaultFlow();
    }

    // ------------------------------------------------------------------ SmartCall 节点实现

    /**
     * say 节点可选的 TTS 合成：节点配置 voiceEngine 时按所选引擎合成音频，
     * 未配置时保持纯文本播报（由语音网关执行），避免无密钥环境产生网络调用。
     */
    private String synthSay(AiIvrNode node, String text, Map<String, Object> variables)
    {
        JsonNode json = config(node);
        String engineCode = json.has("voiceEngine") ? json.get("voiceEngine").asText() : null;
        if (StringUtils.isEmpty(engineCode))
        {
            return "";
        }
        Map<String, Object> options = new HashMap<>();
        JsonNode tts = json.path("tts");
        if (tts.isObject())
        {
            Iterator<Map.Entry<String, JsonNode>> fields = tts.fields();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> entry = fields.next();
                options.put(entry.getKey(), entry.getValue().asText());
            }
        }
        String format = json.path("format").asText("wav");
        int sampleRate = json.path("sampleRate").asInt(8000);
        byte[] audio = voiceEngineManager.synthesize(VoiceModelEnum.of(engineCode), text,
                format, sampleRate, options);
        if (audio != null && audio.length > 0)
        {
            variables.put("lastSayAudioLength", audio.length);
            return "（TTS已合成" + audio.length + "字节音频）";
        }
        return "";
    }

    private AiIvrNode executeSentiment(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                                       List<AiIvrEdge> edges, Map<String, Object> variables,
                                       IvrNodeStep step, IvrExecuteResult result)
    {
        String text = render(configText(current, "text"), variables);
        if (StringUtils.isEmpty(text))
        {
            text = render(configText(current, "message"), variables);
        }
        if (StringUtils.isEmpty(text) && variables.get("lastInput") != null)
        {
            text = String.valueOf(variables.get("lastInput"));
        }
        String sentiment = detectSentiment(text);
        variables.put("sentiment", sentiment);
        step.setAction("SENTIMENT");
        step.setDetail("情绪分析：" + sentiment + "（文本：" + truncate(text, 100) + "）");
        result.getSteps().add(step);
        return nextNode(current, nodeMap, edges, variables, null);
    }

    private String detectSentiment(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return "neutral";
        }
        try
        {
            String system = "你是情绪分析助手。请判断用户话语的情绪，仅返回JSON对象："
                    + "{\"sentiment\":\"positive|negative|neutral\",\"confidence\":0.0-1.0}";
            String user = "用户话语：" + text;
            String raw = modelConfigService.chatJson(system, user);
            JsonNode node = MAPPER.readTree(cleanJson(raw));
            String value = node.path("sentiment").asText(node.path("emotion").asText("neutral"));
            return normalizeSentiment(value);
        }
        catch (Exception e)
        {
            log.debug("情绪模型识别失败，使用关键词兜底: {}", e.getMessage());
            return keywordSentiment(text);
        }
    }

    private String keywordSentiment(String text)
    {
        String[] negative = { "投诉", "不满意", "很差", "恶劣", "威胁", "辱骂", "举报", "要命", "告你们",
                "太差", "垃圾", "混蛋", "气死", "崩溃" };
        String[] positive = { "谢谢", "感谢", "满意", "很好", "太好了", "点赞", "表扬" };
        for (String word : negative)
        {
            if (text.contains(word))
            {
                return "negative";
            }
        }
        for (String word : positive)
        {
            if (text.contains(word))
            {
                return "positive";
            }
        }
        return "neutral";
    }

    private String normalizeSentiment(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return "neutral";
        }
        String v = value.trim();
        if ("正面".equals(v) || "积极".equals(v) || "front".equalsIgnoreCase(v)
                || "positive".equalsIgnoreCase(v))
        {
            return "positive";
        }
        if ("负面".equals(v) || "消极".equals(v) || "negative".equalsIgnoreCase(v))
        {
            return "negative";
        }
        return "neutral";
    }

    private AiIvrNode executeExtract(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                                     List<AiIvrEdge> edges, Map<String, Object> variables,
                                     IvrNodeStep step, IvrExecuteResult result)
    {
        String text = render(configText(current, "text"), variables);
        if (StringUtils.isEmpty(text))
        {
            text = render(configText(current, "message"), variables);
        }
        if (StringUtils.isEmpty(text) && variables.get("lastInput") != null)
        {
            text = String.valueOf(variables.get("lastInput"));
        }
        JsonNode json = config(current);
        JsonNode fields = json.path("fields");
        if (!fields.isObject())
        {
            fields = json.path("keys");
        }
        JsonNode fieldArray = null;
        if (fields.isArray())
        {
            fieldArray = fields;
            fields = null;
        }
        step.setAction("EXTRACT");
        if ((!isObject(fields) || fields.size() == 0)
                && (fieldArray == null || fieldArray.size() == 0))
        {
            step.setDetail("信息抽取节点未配置fields/keys，跳过");
            result.getSteps().add(step);
            return nextNode(current, nodeMap, edges, variables, null);
        }
        try
        {
            StringBuilder fieldDesc = new StringBuilder();
            List<String> fieldNames = new ArrayList<>();
            if (isObject(fields))
            {
                Iterator<String> names = fields.fieldNames();
                while (names.hasNext())
                {
                    String name = names.next();
                    fieldNames.add(name);
                    fieldDesc.append(" - ").append(name).append("：")
                            .append(fields.path(name).asText("")).append("\n");
                }
            }
            else
            {
                for (JsonNode item : fieldArray)
                {
                    String name = item.path("name").asText("");
                    fieldNames.add(name);
                    fieldDesc.append(" - ").append(name).append("：")
                            .append(item.path("desc").asText(item.path("description").asText("")))
                            .append("\n");
                }
            }
            String system = "你是信息抽取助手。请从用户提供的文本中提取字段，仅返回JSON对象，"
                    + "字段值缺失时使用空字符串，不要输出任何多余内容。";
            String user = "需要提取的字段：\n" + fieldDesc + "\n原始文本：" + text;
            String raw = modelConfigService.chatJson(system, user);
            JsonNode extracted = MAPPER.readTree(cleanJson(raw));
            int count = 0;
            for (String name : fieldNames)
            {
                JsonNode value = extracted.path(name);
                variables.put(name, value.isMissingNode() || value.isNull() ? ""
                        : value.isValueNode() ? value.asText() : value.toString());
                count++;
            }
            String resultVar = json.path("resultVar").asText("extractResult");
            variables.put(resultVar, extracted.toString());
            step.setDetail("信息抽取成功，共提取" + count + "个字段：" + extracted);
        }
        catch (Exception e)
        {
            variables.put("extractError", e.getMessage());
            step.setDetail("信息抽取失败：" + e.getMessage());
        }
        result.getSteps().add(step);
        return nextNode(current, nodeMap, edges, variables, null);
    }

    private AiIvrNode executeService(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                                     List<AiIvrEdge> edges, Map<String, Object> variables,
                                     IvrNodeStep step, IvrExecuteResult result)
    {
        JsonNode json = config(current);
        String url = render(json.path("url").asText(""), variables);
        String method = json.path("method").asText("GET").trim().toUpperCase();
        if (StringUtils.isEmpty(method))
        {
            method = "GET";
        }
        int timeoutMs = json.path("timeoutMs").asInt(10000);
        String resultPath = json.path("result").asText("");
        String resultVar = json.path("resultVar").asText("serviceResult");
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode headerNode = json.path("headers");
        if (headerNode.isTextual() && StringUtils.isNotEmpty(headerNode.asText()))
        {
            try
            {
                headerNode = MAPPER.readTree(headerNode.asText());
            }
            catch (Exception e)
            {
                headerNode = MAPPER.createObjectNode();
            }
        }
        if (headerNode.isObject())
        {
            Iterator<String> names = headerNode.fieldNames();
            while (names.hasNext())
            {
                String name = names.next();
                headers.put(name, render(headerNode.path(name).asText(""), variables));
            }
        }
        String body = null;
        if (json.path("body").isTextual())
        {
            body = render(json.path("body").asText(), variables);
        }
        else if (json.path("body").isObject() || json.path("body").isArray())
        {
            body = render(json.path("body").toString(), variables);
        }

        step.setAction("SERVICE");
        if (StringUtils.isEmpty(url))
        {
            variables.put("serviceError", "未配置请求地址");
            step.setDetail("HTTP调用节点未配置url");
            result.getSteps().add(step);
            return nextNode(current, nodeMap, edges, variables, null);
        }
        try
        {
            String responseText = httpCall(url, method, headers, body, timeoutMs);
            variables.put(resultVar, responseText);
            if (StringUtils.isNotEmpty(resultPath))
            {
                Object value = jsonPath(responseText, resultPath);
                variables.put("serviceValue", value == null ? "" : String.valueOf(value));
            }
            step.setDetail("HTTP调用成功：" + method + " " + url
                    + "，响应" + responseText.length() + "字节"
                    + (StringUtils.isEmpty(resultPath) ? "" : "，取值路径=" + resultPath));
        }
        catch (Exception e)
        {
            variables.put("serviceError", e.getMessage());
            step.setDetail("HTTP调用失败：" + method + " " + url + "，错误=" + e.getMessage());
        }
        result.getSteps().add(step);
        return nextNode(current, nodeMap, edges, variables, null);
    }

    private AiIvrNode executeScript(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                                    List<AiIvrEdge> edges, Map<String, Object> variables,
                                    IvrNodeStep step, IvrExecuteResult result)
    {
        JsonNode json = config(current);
        String language = json.path("scriptType").asText(json.path("language").asText("js"));
        String script = json.path("script").asText("");
        String resultVar = json.path("resultVar").asText("scriptResult");
        step.setAction("SCRIPT");
        if (StringUtils.isEmpty(script))
        {
            step.setDetail("脚本节点未配置脚本内容");
            result.getSteps().add(step);
            return nextNode(current, nodeMap, edges, variables, null);
        }
        if ("groovy".equalsIgnoreCase(language))
        {
            variables.put("scriptError", "Java8环境暂不支持Groovy，请使用js脚本");
            step.setDetail("脚本执行失败：Java8环境暂不支持Groovy，请使用js脚本");
            result.getSteps().add(step);
            return nextNode(current, nodeMap, edges, variables, null);
        }
        try
        {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            if (engine == null)
            {
                engine = new ScriptEngineManager().getEngineByName("js");
            }
            if (engine == null)
            {
                engine = new ScriptEngineManager().getEngineByName("JavaScript");
            }
            if (engine == null)
            {
                throw new IllegalStateException("当前JDK未提供JavaScript脚本引擎");
            }
            Bindings bindings = engine.createBindings();
            for (Map.Entry<String, Object> entry : variables.entrySet())
            {
                bindings.put(entry.getKey(), entry.getValue());
            }
            Object value = engine.eval(script, bindings);
            variables.put(resultVar, value == null ? "" : String.valueOf(value));
            step.setDetail("脚本执行成功，结果已写入变量" + resultVar);
        }
        catch (Exception e)
        {
            variables.put("scriptError", e.getMessage());
            step.setDetail("脚本执行失败：" + e.getMessage());
        }
        result.getSteps().add(step);
        return nextNode(current, nodeMap, edges, variables, null);
    }

    private AiIvrNode executeChild(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                                   List<AiIvrEdge> edges, Map<String, Object> variables,
                                   Deque<String> inputs, IvrExecuteRequest parentRequest,
                                   IvrNodeStep step, IvrExecuteResult result)
    {
        JsonNode json = config(current);
        int depth = parseInt(variables.get(CHILD_DEPTH_KEY), 0);
        step.setAction("CHILD");
        if (depth >= MAX_CHILD_DEPTH)
        {
            step.setDetail("子流程嵌套深度超过" + MAX_CHILD_DEPTH + "层，跳过执行");
            result.getSteps().add(step);
            return nextNode(current, nodeMap, edges, variables, null);
        }
        Long childFlowId = json.path("flowId").asLong(0L);
        String childFlowCode = json.path("flowCode").asText("");
        AiIvrFlow childFlow = null;
        if (childFlowId != null && childFlowId > 0)
        {
            childFlow = flowService.selectAiIvrFlowByFlowId(childFlowId);
        }
        else if (StringUtils.isNotEmpty(childFlowCode))
        {
            AiIvrFlow query = new AiIvrFlow();
            query.setFlowCode(childFlowCode);
            List<AiIvrFlow> flows = flowService.selectAiIvrFlowList(query);
            childFlow = flows == null || flows.isEmpty() ? null : flows.get(0);
        }
        if (childFlow == null)
        {
            step.setDetail("未找到子流程（flowId=" + childFlowId + ", flowCode=" + childFlowCode + "）");
            result.getSteps().add(step);
            return nextNode(current, nodeMap, edges, variables, null);
        }
        try
        {
            IvrExecuteRequest subRequest = new IvrExecuteRequest();
            subRequest.setFlowId(childFlow.getFlowId());
            subRequest.setSessionId(parentRequest.getSessionId());
            subRequest.setRecordId(parentRequest.getRecordId());
            subRequest.setCallerNumber(parentRequest.getCallerNumber());
            subRequest.setCalleeNumber(parentRequest.getCalleeNumber());
            subRequest.setInputs(new ArrayList<>(inputs));
            Map<String, Object> subVariables = new LinkedHashMap<>(variables);
            subVariables.remove(CHILD_DEPTH_KEY);
            subVariables.put(CHILD_DEPTH_KEY, depth + 1);
            subRequest.setVariables(subVariables);
            IvrExecuteResult subResult = executeFlow(subRequest);
            if (subResult.getVariables() != null)
            {
                variables.putAll(subResult.getVariables());
            }
            variables.remove(CHILD_DEPTH_KEY);
            variables.put("childResult", "flowName=" + subResult.getFlowName()
                    + ",success=" + subResult.isSuccess()
                    + ",intention=" + (subResult.getMatchedIntention() == null
                    ? "" : subResult.getMatchedIntention()));
            step.setDetail("子流程[" + childFlow.getFlowName() + "]执行"
                    + (subResult.isSuccess() ? "成功" : "失败：" + subResult.getMessage()));
        }
        catch (Exception e)
        {
            variables.put("childError", e.getMessage());
            step.setDetail("子流程[" + childFlow.getFlowName() + "]执行异常：" + e.getMessage());
        }
        result.getSteps().add(step);
        return nextNode(current, nodeMap, edges, variables, null);
    }

    private String httpCall(String url, String method, Map<String, String> headers, String body,
                            int timeoutMs) throws Exception
    {
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(body != null);
            for (Map.Entry<String, String> entry : headers.entrySet())
            {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if (body != null)
            {
                try (OutputStream out = connection.getOutputStream())
                {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readText(stream);
            if (status < 200 || status >= 300)
            {
                throw new IllegalStateException("HTTP " + status + "：" + truncate(response, 200));
            }
            return response;
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }

    private String readText(InputStream in) throws Exception
    {
        if (in == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 简易 JSON 路径取值：支持 a.b.c 与数组下标 a.b[0]。
     */
    private Object jsonPath(String body, String path)
    {
        if (StringUtils.isEmpty(path))
        {
            return body;
        }
        try
        {
            JsonNode node = MAPPER.readTree(body);
            String[] segments = path.trim().split("\\.");
            for (String segment : segments)
            {
                if (StringUtils.isEmpty(segment))
                {
                    continue;
                }
                int arrayIndex = -1;
                int bracket = segment.indexOf('[');
                if (bracket >= 0 && segment.endsWith("]"))
                {
                    try
                    {
                        arrayIndex = Integer.parseInt(
                                segment.substring(bracket + 1, segment.length() - 1).trim());
                    }
                    catch (Exception ignored)
                    {
                    }
                    segment = segment.substring(0, bracket);
                }
                node = node.path(segment);
                if (arrayIndex >= 0)
                {
                    node = node.path(arrayIndex);
                }
            }
            if (node.isMissingNode() || node.isNull())
            {
                return null;
            }
            return node.isValueNode() ? node.asText() : node.toString();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 渲染 ${表达式} 模板，表达式按 SpEL 在流程变量上下文中求值。
     */
    private String render(String template, Map<String, Object> variables)
    {
        if (StringUtils.isEmpty(template) || !template.contains("${"))
        {
            return template;
        }
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find())
        {
            Object value = evalExpr(matcher.group(1), variables);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Object evalExpr(String expr, Map<String, Object> variables)
    {
        try
        {
            StandardEvaluationContext context = new StandardEvaluationContext();
            if (variables != null)
            {
                variables.forEach(context::setVariable);
            }
            return SPEL_PARSER.parseExpression(expr).getValue(context);
        }
        catch (Exception e)
        {
            log.debug("SpEL模板表达式解析失败 expr={} error={}", expr, e.getMessage());
            return null;
        }
    }

    private String cleanJson(String raw)
    {
        if (StringUtils.isEmpty(raw))
        {
            return "{}";
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start)
        {
            text = text.substring(start, end + 1);
        }
        return text;
    }

    private int parseInt(Object value, int fallback)
    {
        if (value == null)
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private IvrExecuteResult failResult(IvrExecuteResult result, String code, String message)
    {
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        result.setStatus("2");
        return result;
    }

    private AiIvrNode nextNode(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                               List<AiIvrEdge> edges, Map<String, Object> variables, String inputValue)
    {
        AiIvrEdge edge = routeEdge(current, edges, variables, inputValue);
        return edge == null ? null : nodeMap.get(edge.getTargetNodeId());
    }

    /**
     * 条件路由：SpEL 表达式优先，其次按键/语音节点按标签匹配，最后默认连线（无表达式、无标签）。
     */
    private AiIvrEdge routeEdge(AiIvrNode current, List<AiIvrEdge> edges,
                                Map<String, Object> variables, String inputValue)
    {
        List<AiIvrEdge> outgoing = new ArrayList<>();
        for (AiIvrEdge e : edges)
        {
            if (current.getNodeId().equals(e.getSourceNodeId()))
            {
                outgoing.add(e);
            }
        }
        outgoing.sort((a, b) -> Integer.compare(
                a.getSortOrder() == null ? 0 : a.getSortOrder(),
                b.getSortOrder() == null ? 0 : b.getSortOrder()));

        for (AiIvrEdge edge : outgoing)
        {
            if (StringUtils.isNotEmpty(edge.getConditionExpr()))
            {
                if (evalCondition(edge.getConditionExpr(), variables))
                {
                    return edge;
                }
            }
        }
        if (inputValue != null)
        {
            for (AiIvrEdge edge : outgoing)
            {
                if (StringUtils.isEmpty(edge.getConditionExpr()) && inputValue.equals(edge.getEdgeLabel()))
                {
                    return edge;
                }
            }
        }
        for (AiIvrEdge edge : outgoing)
        {
            if (StringUtils.isEmpty(edge.getConditionExpr()) && StringUtils.isEmpty(edge.getEdgeLabel()))
            {
                return edge;
            }
        }
        return null;
    }

    private boolean evalCondition(String expr, Map<String, Object> variables)
    {
        try
        {
            Expression expression = SPEL_PARSER.parseExpression(expr);
            StandardEvaluationContext context = new StandardEvaluationContext(variables);
            if (variables != null)
            {
                variables.forEach(context::setVariable);
            }
            Object value = expression.getValue(context);
            return value != null && (Boolean.TRUE.equals(value)
                    || "true".equalsIgnoreCase(String.valueOf(value)));
        }
        catch (Exception e)
        {
            log.debug("SpEL表达式解析失败 expr={} error={}", expr, e.getMessage());
            return false;
        }
    }

    private String pollInput(Deque<String> inputs, Map<String, Object> variables)
    {
        if (inputs == null || inputs.isEmpty())
        {
            return null;
        }
        return inputs.poll();
    }

    private JsonNode config(AiIvrNode node)
    {
        if (node == null || StringUtils.isEmpty(node.getNodeConfig()))
        {
            return MAPPER.createObjectNode();
        }
        try
        {
            return MAPPER.readTree(node.getNodeConfig());
        }
        catch (Exception e)
        {
            return MAPPER.createObjectNode();
        }
    }

    private String configText(AiIvrNode node, String key)
    {
        JsonNode json = config(node);
        JsonNode value = json.get(key);
        if (value == null)
        {
            value = json.get("value");
        }
        return value == null ? null : value.asText();
    }

    private String transferTarget(AiIvrNode node, Map<String, Object> variables)
    {
        JsonNode json = config(node);
        if (json.has("agentExtension"))
        {
            return "坐席分机 " + json.get("agentExtension").asText();
        }
        if (json.has("agentId"))
        {
            return "坐席ID " + json.get("agentId").asText();
        }
        if (json.has("number"))
        {
            return json.get("number").asText();
        }
        if (json.has("target"))
        {
            return json.get("target").asText();
        }
        return "人工坐席";
    }

    private void assignVariable(AiIvrNode node, Map<String, Object> variables)
    {
        JsonNode json = config(node);
        JsonNode list = json.path("variables");
        if (list.isArray() && list.size() > 0)
        {
            for (JsonNode item : list)
            {
                assignOne(item.path("key").asText(""), item.path("val").asText(""), variables);
            }
            return;
        }
        assignOne(json.path("name").asText(""), json.path("value").asText(""), variables);
    }

    private void assignOne(String name, String value, Map<String, Object> variables)
    {
        if (StringUtils.isEmpty(name))
        {
            return;
        }
        value = render(value, variables);
        if (value.startsWith("${") && value.endsWith("}"))
        {
            String key = value.substring(2, value.length() - 1);
            variables.put(name, variables.get(key));
        }
        else
        {
            variables.put(name, value);
        }
    }

    private boolean isObject(JsonNode node)
    {
        return node != null && node.isObject();
    }

    private String labelOf(AiIvrEdge edge)
    {
        if (StringUtils.isNotEmpty(edge.getConditionExpr()))
        {
            return edge.getConditionExpr();
        }
        if (StringUtils.isNotEmpty(edge.getEdgeLabel()))
        {
            return edge.getEdgeLabel();
        }
        return "默认";
    }

    private void finishLog(AiIvrExecutionLog execLog, IvrExecuteResult result, String status, String errorMsg)
    {
        try
        {
            AiIvrExecutionLog update = new AiIvrExecutionLog();
            update.setExecId(execLog.getExecId());
            update.setStatus(status);
            update.setEndTime(new Date());
            update.setErrorMsg(errorMsg);
            update.setExecuteResult(MAPPER.writeValueAsString(result.getSteps()));
            update.setVariables(MAPPER.writeValueAsString(result.getVariables()));
            executionLogService.updateAiIvrExecutionLog(update);
        }
        catch (Exception e)
        {
            log.warn("IVR执行日志结束回写失败: {}", e.getMessage());
        }
    }

    /**
     * 融合点 3.5.1：流程节点执行结果更新到通话记录。
     * 意图命中时回写咨询分类；发生转接时标记通话为已转接；摘要写入备注。
     */
    private void updateRecord(AiIvrFlow flow, IvrExecuteResult result)
    {
        if (result.getRecordId() == null)
        {
            return;
        }
        try
        {
            AiCallRecord record = callRecordService.selectAiCallRecordByRecordId(result.getRecordId());
            if (record == null)
            {
                return;
            }
            AiCallRecord update = new AiCallRecord();
            update.setRecordId(record.getRecordId());
            if (result.getCategoryId() != null && record.getCategoryId() == null)
            {
                update.setCategoryId(result.getCategoryId());
            }
            if (StringUtils.isNotEmpty(result.getTransferTarget()))
            {
                update.setStatus("2");
            }
            String oldRemark = record.getRemark() == null ? "" : record.getRemark();
            StringBuilder sb = new StringBuilder(oldRemark);
            if (StringUtils.isNotEmpty(oldRemark))
            {
                sb.append("；");
            }
            sb.append("【IVR】流程[").append(flow.getFlowName()).append("]");
            if (StringUtils.isNotEmpty(result.getMatchedIntention()))
            {
                sb.append(" 意图=").append(result.getMatchedIntentionName())
                        .append("(").append(result.getMatchedIntention()).append(")");
            }
            if (StringUtils.isNotEmpty(result.getTransferTarget()))
            {
                sb.append(" 转接=").append(result.getTransferTarget());
            }
            if (result.getSteps() != null)
            {
                sb.append(" 节点数=").append(result.getSteps().size());
            }
            update.setRemark(truncate(sb.toString(), 500));
            callRecordService.updateAiCallRecord(update);
        }
        catch (Exception e)
        {
            log.warn("IVR结果回写通话记录失败: {}", e.getMessage());
        }
    }

    private String truncate(String s, int max)
    {
        if (s == null)
        {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
