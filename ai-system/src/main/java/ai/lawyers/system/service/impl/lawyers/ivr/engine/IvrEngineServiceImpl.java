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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

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
import ai.lawyers.system.domain.lawyers.agent.AgentChatResult;
import ai.lawyers.system.service.lawyers.agent.IAgentChatService;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiModelConfigService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrEdgeService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrExecutionLogService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;
import ai.lawyers.system.service.lawyers.ivr.engine.IntentionRecognitionService;
import ai.lawyers.system.service.lawyers.sms.ISmsService;
import ai.lawyers.system.service.lawyers.voice.VoiceEngineManager;
import ai.lawyers.system.service.lawyers.voice.VoiceModelEnum;

/**
 * IVR 流程执行引擎实现。
 *
 * 支持节点类型（与前端设计器画板对齐）：
 *  start / say / answer(ASR收声) / menu / received(DTMF收号) / intention / condition /
 *  sentiment(情绪) / extract(信息抽取) / service(HTTP调用) / script(JS脚本) / child(子流程) /
 *  agentChat(智能体对话) / agent(转人工) / transfer(转外线) / variable / hangup
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
    private static final String NODE_AGENT_CHAT = "agentchat";
    private static final String NODE_TRANSFER = "transfer";
    private static final String NODE_HANGUP = "hangup";
    private static final String NODE_VARIABLE = "variable";
    private static final String NODE_SMS = "sms";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /** 子流程最大嵌套深度，防止循环引用 */
    private static final int MAX_CHILD_DEPTH = 3;
    private static final String CHILD_DEPTH_KEY = "__childDepth";

    /** T1-1 执行日志节流落库间隔（毫秒）：间隔内的节点只更新内存，到时或关键节点才落库 */
    @org.springframework.beans.factory.annotation.Value("${ivr.log.flush-ms:1000}")
    private long logFlushIntervalMs;

    /** T1-2 单次流程总超时（毫秒），超时走兜底挂断，防止 AI 节点慢响应拖死呼叫线程 */
    @org.springframework.beans.factory.annotation.Value("${ivr.flow.timeout-ms:180000}")
    private long flowTimeoutMs;

    /** 单线程日志落库执行器：把每步全量序列化/UPDATE 从呼叫主链路剥离（T1-1，消除 O(N²) 主链路开销） */
    private final java.util.concurrent.ExecutorService logWriter =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ivr-log-writer");
                t.setDaemon(true);
                return t;
            });

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

    @Autowired
    private IAgentChatService agentChatService;

    @Autowired
    private ai.lawyers.system.service.lawyers.skill.IAgentDispatchService agentDispatchService;

    @Autowired
    private ISmsService smsService;

    /** P3-D1 高频置底：IVR 识别文本关键词规则判定（REJECT 终止流程 / PRIORITY 转人工降权） */
    @Autowired
    private ai.lawyers.system.service.lawyers.IAiHotspotSuppressService hotspotSuppressService;

    /** 高频置底总开关（与入站/外呼共用） */
    @org.springframework.beans.factory.annotation.Value("${hotspot.suppress.enabled:true}")
    private boolean hotspotEnabled;

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
        // T1-2 流程总超时截止时间
        final long deadline = System.currentTimeMillis() + flowTimeoutMs;
        // T1-1 节流落库：最近一次落库时间，间隔内只更新内存，到时/终止节点异步落库
        long lastFlush = System.currentTimeMillis();
        boolean timedOut = false;
        while (current != null && stepCount < MAX_STEPS)
        {
            // T1-2 总超时控制：超时则记录兜底步骤并终止，避免长流程/慢 AI 拖死呼叫线程
            if (System.currentTimeMillis() > deadline)
            {
                IvrNodeStep timeoutStep = new IvrNodeStep(current.getNodeId(),
                        current.getNodeType() == null ? "" : current.getNodeType().toLowerCase(),
                        current.getNodeName(), "TIMEOUT", "流程总超时(" + flowTimeoutMs + "ms)，兜底挂断");
                result.getSteps().add(timeoutStep);
                timedOut = true;
                break;
            }
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
                    // T1-4 menu/dtmf 支持多位按键（maxDigits 默认 1 保持旧行为；endKey 结束键默认 #）
                    JsonNode menuCfg = config(current);
                    String menuEndKey = menuCfg.path("endKey").asText(menuCfg.path("end").asText("#"));
                    if (StringUtils.isNotEmpty(menuEndKey) && dtmf.endsWith(menuEndKey))
                    {
                        dtmf = dtmf.substring(0, dtmf.length() - menuEndKey.length());
                    }
                    int menuMaxDigits = menuCfg.path("maxDigits").asInt(1);
                    if (menuMaxDigits > 1)
                    {
                        if (dtmf.length() > menuMaxDigits)
                        {
                            dtmf = dtmf.substring(0, menuMaxDigits);
                        }
                    }
                    else if (dtmf.length() > 1)
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
                    // P3-D1：高频置底关键词拦截——识别文本命中 REJECT 规则即终止流程（挂断语义，复用 hangup 终止标记）；
                    // 命中 PRIORITY 规则写入 dispatchPriority 变量，后续转人工节点 dispatchAgent 自动读取降权沉底
                    if (hotspotEnabled && StringUtils.isNotEmpty(intentText))
                    {
                        String hotspotDirection = StringUtils.isNotEmpty(request.getCalleeNumber()) ? "OUTBOUND" : "INBOUND";
                        ai.lawyers.system.domain.lawyers.AiHotspotSuppress kwSuppress =
                                hotspotSuppressService.matchKeyword(intentText, request.getCallerNumber(), sessionId, hotspotDirection);
                        if (kwSuppress != null && "REJECT".equals(kwSuppress.getAction()))
                        {
                            log.warn("[IVR] 命中高频置底关键词规则[{}]，流程终止（拦截挂断） sessionId={}",
                                    kwSuppress.getRuleName(), sessionId);
                            step.setAction("HANGUP");
                            step.setDetail("命中高频置底关键词规则[" + kwSuppress.getRuleName() + "]，拦截挂断");
                            result.getSteps().add(step);
                            variables.put("__terminated", "hangup");
                            current = null;
                            break;
                        }
                        if (kwSuppress != null && "PRIORITY".equals(kwSuppress.getAction()))
                        {
                            int kwPriority = kwSuppress.getPriorityLevel() != null ? kwSuppress.getPriorityLevel() : -100;
                            variables.put("dispatchPriority", kwPriority);
                            log.info("[IVR] 命中置底降权关键词规则[{}]，转人工降权 priority={} sessionId={}",
                                    kwSuppress.getRuleName(), kwPriority, sessionId);
                        }
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

                case NODE_AGENT_CHAT:
                    current = executeAgentChat(current, nodeMap, edges, variables, inputs,
                            request, sessionId, flow, step, result);
                    break;

                case NODE_AGENT:
                case NODE_TRANSFER:
                    String target;
                    JsonNode tcfg = config(current);
                    String dispatchMode = tcfg.has("dispatchMode") ? tcfg.get("dispatchMode").asText() : "static";
                    if ("dispatch".equals(dispatchMode))
                    {
                        // B5 智能队列分配：按 groupId 或 B1 写入的 agentCategoryId 动态选坐席
                        target = dispatchAgent(tcfg, variables, request, sessionId, step, result, NODE_AGENT.equals(type));
                        if (target == null)
                        {
                            // 排队中：沿排队连线（变量 dispatchQueued=true）继续，不终止流程
                            variables.put("dispatchQueued", true);
                            result.getSteps().add(step);
                            current = nextNode(current, nodeMap, edges, variables, null);
                            break;
                        }
                        variables.put("dispatchQueued", false);
                    }
                    else
                    {
                        target = transferTarget(current, variables);
                    }
                    result.setTransferTarget(target);
                    step.setAction("TRANSFER");
                    step.setDetail((NODE_AGENT.equals(type) ? "转人工坐席：" : "转外线：") + target);
                    result.getSteps().add(step);
                    // T1-3 标记终止原因，供子流程冒泡判断
                    variables.put("__terminated", "transfer");
                    current = null;
                    break;

                case NODE_HANGUP:
                    step.setAction("HANGUP");
                    step.setDetail("挂断" + (StringUtils.isNotEmpty(current.getNodeName()) ? "（" + current.getNodeName() + "）" : ""));
                    result.getSteps().add(step);
                    // T1-3 标记终止原因，供子流程冒泡判断
                    variables.put("__terminated", "hangup");
                    current = null;
                    break;

                case NODE_VARIABLE:
                    assignVariable(current, variables);
                    step.setAction("VARIABLE");
                    step.setDetail("变量赋值：" + current.getNodeName());
                    result.getSteps().add(step);
                    current = nextNode(current, nodeMap, edges, variables, null);
                    break;

                case NODE_SMS:
                    current = executeSms(current, nodeMap, edges, variables, request, sessionId, step, result);
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

            // T1-1 执行日志节流 + 异步落库：
            //  - 终止节点（hangup/transfer，current=null）或到达节流间隔才落库，其余步骤仅更新内存；
            //  - 序列化与 UPDATE 投递到单线程 logWriter，呼叫主链路不再每步同步全量序列化。
            boolean terminal = current == null;
            long nowMs = System.currentTimeMillis();
            if (terminal || nowMs - lastFlush >= logFlushIntervalMs)
            {
                lastFlush = nowMs;
                final AiIvrNode snapshotNode = current;
                final IvrExecuteResult snapshotResult = result;
                final Map<String, Object> snapshotVars = variables;
                queueLogWrite(execLog.getExecId(), snapshotNode, snapshotResult, snapshotVars);
            }
        }

        if (timedOut)
        {
            result.setSuccess(false);
            result.setCode("FLOW_TIMEOUT");
            result.setMessage("流程执行超过总超时(" + flowTimeoutMs + "ms)，已兜底终止");
            result.setStatus("2");
            flushLogSync(execLog, result);
            finishLog(execLog, result, "2", result.getMessage());
            updateRecord(flow, result);
            return result;
        }

        if (stepCount >= MAX_STEPS)
        {
            result.setSuccess(false);
            result.setCode("LOOP_LIMIT");
            result.setMessage("流程超过最大执行步数限制(" + MAX_STEPS + ")，疑似存在循环");
            result.setStatus("2");
            flushLogSync(execLog, result);
            finishLog(execLog, result, "2", result.getMessage());
            updateRecord(flow, result);
            return result;
        }

        flushLogSync(execLog, result);
        finishLog(execLog, result, "1", null);
        updateRecord(flow, result);
        return result;
    }

    /**
     * T1-1：异步落库一条执行日志快照。队列满/异常时降级为同步落库，保证不丢日志。
     */
    private void queueLogWrite(Long execId, AiIvrNode current, IvrExecuteResult result,
                               Map<String, Object> variables)
    {
        try
        {
            final String varsJson = MAPPER.writeValueAsString(variables);
            final String stepsJson = MAPPER.writeValueAsString(result.getSteps());
            final Long nodeId = current == null ? null : current.getNodeId();
            final String nodeType = current == null ? null : current.getNodeType();
            final String nodeName = current == null ? null : current.getNodeName();
            logWriter.submit(() -> {
                try
                {
                    AiIvrExecutionLog update = new AiIvrExecutionLog();
                    update.setExecId(execId);
                    update.setCurrentNodeId(nodeId);
                    update.setCurrentNodeType(nodeType);
                    update.setCurrentNodeName(nodeName);
                    update.setVariables(varsJson);
                    update.setExecuteResult(stepsJson);
                    executionLogService.updateAiIvrExecutionLog(update);
                }
                catch (Exception e)
                {
                    log.debug("IVR执行日志异步回写失败: {}", e.getMessage());
                }
            });
        }
        catch (Exception e)
        {
            // 提交失败（如线程池已关闭）则同步兜底
            try
            {
                AiIvrExecutionLog update = new AiIvrExecutionLog();
                update.setExecId(execId);
                update.setCurrentNodeId(current == null ? null : current.getNodeId());
                update.setCurrentNodeType(current == null ? null : current.getNodeType());
                update.setCurrentNodeName(current == null ? null : current.getNodeName());
                update.setVariables(MAPPER.writeValueAsString(variables));
                update.setExecuteResult(MAPPER.writeValueAsString(result.getSteps()));
                executionLogService.updateAiIvrExecutionLog(update);
            }
            catch (Exception ex)
            {
                log.debug("IVR执行日志同步兜底回写失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * T1-1：流程结束时等待已排队的日志落库完成，保证最终态（变量/步骤完整）可见。
     */
    private void flushLogSync(AiIvrExecutionLog execLog, IvrExecuteResult result)
    {
        try
        {
            java.util.concurrent.Future<?> last = logWriter.submit(() -> { });
            last.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            log.debug("IVR执行日志收尾等待失败: {}", e.getMessage());
        }
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

    private AiIvrNode executeAgentChat(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                                       List<AiIvrEdge> edges, Map<String, Object> variables,
                                       Deque<String> inputs, IvrExecuteRequest request,
                                       String sessionId, AiIvrFlow flow,
                                       IvrNodeStep step, IvrExecuteResult result)
    {
        JsonNode json = config(current);
        long agentId = json.path("agentId").asLong(0L);
        String welcome = render(json.path("welcome").asText(""), variables);
        int maxTurns = json.path("maxTurns").asInt(5);
        String resultVar = json.path("resultVar").asText("agentReply");
        String handoffVar = json.path("handoffVar").asText("agentHandoff");

        // 累计本轮会话在该流程中的对话轮数，达到上限强制转人工
        int turnCount = variables.get("agentTurnCount") instanceof Number
                ? ((Number) variables.get("agentTurnCount")).intValue() : 0;

        String reply;
        boolean handoff;
        String reason;

        if (agentId <= 0L)
        {
            reply = "智能体未正确配置，为您转接人工。";
            handoff = true;
            reason = "节点未配置agentId";
        }
        else if (turnCount >= maxTurns)
        {
            reply = "已达最大对话轮数，为您转接人工坐席。";
            handoff = true;
            reason = "达到最大轮数(" + maxTurns + ")";
        }
        else
        {
            // 取用户输入：优先消费输入队列，兜底取上一个 answer 节点写入的 lastInput
            String userInput = pollInput(inputs, variables);
            if (StringUtils.isEmpty(userInput) && variables.get("lastInput") != null)
            {
                userInput = String.valueOf(variables.get("lastInput"));
            }
            // 首轮无输入时直接播报欢迎语，不调用模型
            if (StringUtils.isEmpty(userInput) && StringUtils.isNotEmpty(welcome))
            {
                reply = welcome;
                handoff = false;
                reason = null;
            }
            else
            {
                turnCount++;
                AgentChatResult chatResult = agentChatService.chat(agentId, sessionId, userInput,
                        request.getRecordId(), flow == null ? null : flow.getFlowId(),
                        current.getNodeId(), request.getCallerNumber());
                reply = chatResult.getReply();
                handoff = chatResult.isHandoff();
                reason = chatResult.getReason();
                if (chatResult.getCategoryId() != null)
                {
                    variables.put("agentCategoryId", chatResult.getCategoryId());
                }
            }
        }

        variables.put(resultVar, reply);
        variables.put(handoffVar, handoff);
        variables.put("agentTurnCount", turnCount);
        if (handoff)
        {
            result.setTransferTarget("人工坐席");
        }

        step.setAction("AGENT_CHAT");
        String detail = "智能体回复（第" + turnCount + "轮）：" + truncate(reply, 200);
        if (handoff)
        {
            detail += " [转人工" + (StringUtils.isNotEmpty(reason) ? "：" + reason : "") + "]";
        }
        step.setDetail(detail);
        result.getSteps().add(step);
        return nextNode(current, nodeMap, edges, variables, null);
    }

    /**
     * B6 短信通知节点：给来电者发送短信（排队告知 / 工单编号 / 文书链接等）。
     *
     * <p>节点配置支持：templateId（短信模板ID）、phoneVar（收件号码变量，默认 callerNumber，
     * 若流程变量取不到则视为字面号码）、params（模板变量，支持 ${表达式} 渲染）。
     * 短信失败不阻断流程，写入 smsStatus/smsError 后沿下一节点继续。</p>
     */
    private AiIvrNode executeSms(AiIvrNode current, Map<Long, AiIvrNode> nodeMap,
                                 List<AiIvrEdge> edges, Map<String, Object> variables,
                                 IvrExecuteRequest request, String sessionId,
                                 IvrNodeStep step, IvrExecuteResult result)
    {
        JsonNode json = config(current);
        long templateId = json.path("templateId").asLong(0L);
        String phoneVar = json.path("phoneVar").asText("callerNumber");

        Object phoneObj = variables.get(phoneVar);
        String phone = phoneObj == null ? phoneVar : String.valueOf(phoneObj);
        if (StringUtils.isEmpty(phone))
        {
            phone = request.getCallerNumber();
        }

        Map<String, String> params = new LinkedHashMap<>();
        JsonNode paramsNode = json.path("params");
        if (paramsNode.isTextual() && StringUtils.isNotEmpty(paramsNode.asText()))
        {
            try
            {
                paramsNode = MAPPER.readTree(paramsNode.asText());
            }
            catch (Exception e)
            {
                paramsNode = MAPPER.createObjectNode();
            }
        }
        if (paramsNode.isObject())
        {
            Iterator<String> names = paramsNode.fieldNames();
            while (names.hasNext())
            {
                String name = names.next();
                params.put(name, render(paramsNode.path(name).asText(""), variables));
            }
        }

        step.setAction("SMS");
        if (templateId <= 0L)
        {
            variables.put("smsStatus", "fail");
            variables.put("smsError", "未配置短信模板");
            step.setDetail("短信节点未配置templateId");
            result.getSteps().add(step);
            return nextNode(current, nodeMap, edges, variables, null);
        }

        try
        {
            SmsResult smsResult = smsService.send(phone, templateId, params, sessionId, request.getRecordId());
            variables.put("smsStatus", smsResult.isSuccess() ? "success" : "fail");
            variables.put("smsMsgId", smsResult.getMsgId());
            variables.put("smsContent", smsResult.getContent());
            variables.put("smsError", smsResult.isSuccess() ? "" : smsResult.getMessage());
            step.setDetail("发送短信至 " + phone + "：" + (smsResult.isSuccess()
                    ? "成功（" + smsResult.getMsgId() + "）" : "失败（" + smsResult.getMessage() + "）"));
        }
        catch (Exception e)
        {
            variables.put("smsStatus", "fail");
            variables.put("smsError", e.getMessage());
            step.setDetail("短信发送异常：" + e.getMessage());
        }
        result.getSteps().add(step);
        return nextNode(current, nodeMap, edges, variables, null);
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
            String raw = modelConfigService.chatJson(system, user,
                    ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder.SCENE_EMOTION);
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
            String raw = modelConfigService.chatJson(system, user,
                    ai.lawyers.system.service.lawyers.stat.AiModelCallLogRecorder.SCENE_EXTRACT);
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
        // 安全收口(S5)：SSRF 防护，仅允许 http/https 且目标不得为内网/回环/保留地址
        String ssrfError = checkUrlAllowed(url);
        if (ssrfError != null)
        {
            variables.put("serviceError", ssrfError);
            step.setDetail("HTTP调用被安全策略拦截：" + ssrfError);
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
            // 安全收口(S4)：Nashorn 加 ClassFilter 沙箱，禁止脚本访问任意 Java 类（防 Java.type 逃逸 RCE）
            ScriptEngine engine = createSandboxScriptEngine();
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
                // T1-3 子流程冒泡：先取回终止标记，合并变量后清除内部标记，再决定是否终止父流程
                Object subTerminated = subResult.getVariables().get("__terminated");
                variables.putAll(subResult.getVariables());
                variables.remove("__terminated");
                if (subResult.getTransferTarget() != null)
                {
                    result.setTransferTarget(subResult.getTransferTarget());
                }
                if (subResult.getMatchedIntention() != null)
                {
                    result.setMatchedIntention(subResult.getMatchedIntention());
                    result.setMatchedIntentionName(subResult.getMatchedIntentionName());
                    result.setMatchMethod(subResult.getMatchMethod());
                    result.setCategoryId(subResult.getCategoryId());
                    result.setCategoryName(subResult.getCategoryName());
                }
                variables.remove(CHILD_DEPTH_KEY);
                variables.put("childResult", "flowName=" + subResult.getFlowName()
                        + ",success=" + subResult.isSuccess()
                        + ",terminated=" + (subTerminated == null ? "" : subTerminated)
                        + ",intention=" + (subResult.getMatchedIntention() == null
                        ? "" : subResult.getMatchedIntention()));
                step.setDetail("子流程[" + childFlow.getFlowName() + "]执行"
                        + (subResult.isSuccess() ? "成功" : "失败：" + subResult.getMessage())
                        + (subTerminated != null ? "；子流程" + ("hangup".equals(subTerminated) ? "挂断" : "转接") + "，冒泡终止父流程" : ""));
                result.getSteps().add(step);
                // 子流程以挂断/转接结束时，父流程必须立即终止并上抛语义（L1 修复）
                if (subTerminated != null)
                {
                    return null;
                }
                return nextNode(current, nodeMap, edges, variables, null);
            }
        }
        catch (Exception e)
        {
            variables.put("childError", e.getMessage());
            step.setDetail("子流程[" + childFlow.getFlowName() + "]执行异常：" + e.getMessage());
        }
        result.getSteps().add(step);
        return nextNode(current, nodeMap, edges, variables, null);
    }

    /**
     * 安全收口(S4)：创建带 ClassFilter 沙箱的 Nashorn 引擎。
     * ClassFilter.exposeToScripts 恒返回 false，脚本无法通过 Java.type/反射访问任何 Java 类，
     * 仅能使用 JS 内置对象与注入的流程变量（变量值均为字符串/数字）。
     */
    private ScriptEngine createSandboxScriptEngine()
    {
        try
        {
            NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
            // --no-java 禁止 Java 包访问；ClassFilter 双重兜底拒绝所有类暴露
            return factory.getScriptEngine(new String[] { "--no-java" },
                    Thread.currentThread().getContextClassLoader(),
                    new ClassFilter()
                    {
                        @Override
                        public boolean exposeToScripts(String className)
                        {
                            log.warn("IVR脚本尝试访问Java类已被沙箱拦截：{}", className);
                            return false;
                        }
                    });
        }
        catch (Throwable t)
        {
            // 极端情况下 Nashorn 不可用（如裁剪版 JRE），返回 null 由上层降级
            log.warn("创建Nashorn沙箱脚本引擎失败：{}", t.getMessage());
            return null;
        }
    }

    /**
     * 安全收口(S5)：校验 service 节点目标 URL，防 SSRF。
     * 仅允许 http/https；解析目标 IP 后拒绝回环/内网/链路本地/保留地址及云元数据地址。
     *
     * @return 非 null 表示拦截原因；null 表示放行
     */
    private String checkUrlAllowed(String url)
    {
        try
        {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)))
            {
                return "仅允许 http/https 协议";
            }
            String host = uri.getHost();
            if (StringUtils.isEmpty(host))
            {
                return "URL缺少主机名";
            }
            // 云元数据地址显式拦截
            String lowerHost = host.toLowerCase();
            if (lowerHost.startsWith("169.254.") || lowerHost.contains("metadata.google.internal"))
            {
                return "禁止访问云元数据地址";
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress())
            {
                return "禁止访问内网/回环/保留地址：" + host;
            }
            return null;
        }
        catch (Exception e)
        {
            return "URL解析失败：" + e.getMessage();
        }
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
            // 安全收口(S5)：禁止自动跟随重定向，防止 302 跳转绕过 SSRF 校验指向内网
            connection.setInstanceFollowRedirects(false);
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
            // 安全收口(S3)：只读数据绑定上下文，禁止类型引用(T(...))/构造器/方法调用/赋值，杜绝 RCE；
            // 追加 MapAccessor 以支持 ${#var} 之外的 Map 键属性式访问
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            context.getPropertyAccessors().add(0, new MapAccessor());
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
            // 安全收口(S3)：只读数据绑定上下文，禁止 T(...) 类型表达式与任意方法调用；
            // 追加 MapAccessor 并以变量 Map 为根对象，支持 root 键属性式访问。
            // Spring 5.2.x 的 SimpleEvaluationContext 无 setRootObject，根对象须在 builder 阶段 withRootObject 传入
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding()
                    .withRootObject(variables == null ? new HashMap<>() : variables)
                    .build();
            context.getPropertyAccessors().add(0, new MapAccessor());
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

    /**
     * B5 智能队列分配：按技能组动态选择坐席。
     *
     * <p>节点配置支持：groupId（固定技能组）、categoryVar（从流程变量取咨询分类ID，默认
     * agentCategoryId，承接 B1 agentChat 结果）、enqueueIfNoAgent（无空闲是否排队，默认 true）。
     * 分配成功返回坐席标识；若排队中返回 null（调用方沿排队连线继续）。</p>
     */
    private String dispatchAgent(JsonNode cfg, Map<String, Object> variables,
                                 IvrExecuteRequest request, String sessionId,
                                 IvrNodeStep step, IvrExecuteResult result, boolean isAgentNode)
    {
        Long groupId = cfg.has("groupId") && !cfg.get("groupId").isNull() ? cfg.get("groupId").asLong() : null;
        String categoryVar = cfg.has("categoryVar") && StringUtils.isNotEmpty(cfg.get("categoryVar").asText())
                ? cfg.get("categoryVar").asText() : "agentCategoryId";
        boolean enqueue = !cfg.has("enqueueIfNoAgent") || cfg.get("enqueueIfNoAgent").asBoolean(true);

        ai.lawyers.system.domain.lawyers.skill.DispatchContext ctx =
                ai.lawyers.system.domain.lawyers.skill.DispatchContext.of(
                        sessionId, request.getRecordId(), request.getCallerNumber());
        ctx.setEnqueueIfNoAgent(enqueue);
        Object prio = variables.get("dispatchPriority");
        if (prio instanceof Number)
        {
            ctx.setPriority(((Number) prio).intValue());
        }

        ai.lawyers.system.domain.lawyers.skill.DispatchResult dr;
        if (groupId != null)
        {
            dr = agentDispatchService.dispatch(groupId, ctx);
        }
        else
        {
            Object catObj = variables.get(categoryVar);
            Long categoryId = catObj instanceof Number ? ((Number) catObj).longValue() : null;
            if (categoryId == null)
            {
                // 未指定技能组且无分类信息，回退到静态目标
                String fallback = cfg.has("fallbackTarget") ? cfg.get("fallbackTarget").asText() : "人工坐席";
                step.setAction("TRANSFER");
                step.setDetail("智能分配未指定技能组/分类，回退到：" + fallback);
                return fallback;
            }
            dr = agentDispatchService.dispatchByCategory(categoryId, ctx);
        }

        // 回写流程变量，供后续节点/SpEL 使用
        variables.put("dispatchSuccess", dr.isSuccess());
        variables.put("dispatchMessage", dr.getMessage());
        if (dr.getGroupId() != null)
        {
            variables.put("dispatchGroupId", dr.getGroupId());
            variables.put("dispatchGroupName", dr.getGroupName());
        }
        if (dr.isSuccess())
        {
            variables.put("dispatchAgentId", dr.getAgentId());
            variables.put("dispatchAgentName", dr.getAgentName());
            step.setAction("TRANSFER");
            step.setDetail("智能分配坐席：" + dr.getAgentName()
                    + "（" + dr.getGroupName() + "，策略：" + dr.getStrategy() + "）");
            return "坐席ID " + dr.getAgentId();
        }
        else if (dr.getQueueId() != null)
        {
            variables.put("dispatchQueueId", dr.getQueueId());
            variables.put("dispatchQueuePosition", dr.getQueuePosition());
            step.setAction("QUEUE");
            step.setDetail(dr.getMessage() + "（技能组：" + dr.getGroupName() + "）");
            return null;
        }
        else
        {
            step.setAction("TRANSFER");
            step.setDetail("智能分配失败：" + dr.getMessage());
            return "人工坐席";
        }
    }

    private String transferTarget(AiIvrNode node, Map<String, Object> variables) {
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
