package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrExecutionLog;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrNodeStep;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.node.IvrNodeContext;
import ai.lawyers.system.service.impl.lawyers.ivr.engine.node.IvrNodeExecutor;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrEdgeService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrExecutionLogService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrFlowService;
import ai.lawyers.system.service.lawyers.ivr.IAiIvrNodeService;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;

import jakarta.annotation.PostConstruct;

/**
 * IVR 流程执行引擎实现（门面）：负责流程装载、主循环编排（总超时/步数上限）、
 * 执行日志节流异步落库与结果回写。
 *
 * <p>P5 NodeExecutor 注册表改造：节点语义全部下沉到
 * {@link ai.lawyers.system.service.impl.lawyers.ivr.engine.node} 包下的执行器，
 * 主循环按节点类型（小写）查注册表分发；新增节点类型只需新增一个执行器组件。</p>
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /** P5 NodeExecutor 注册表：Spring 自动注入全部节点执行器组件；单测无容器时可手动装配同名字段 */
    @Autowired(required = false)
    private List<IvrNodeExecutor> nodeExecutors;

    /** 节点类型（小写）→ 执行器 注册表，懒构建（@PostConstruct 预热并校验重复注册） */
    private volatile Map<String, IvrNodeExecutor> executorRegistry;

    @PostConstruct
    void initExecutorRegistry()
    {
        executorRegistry();
    }

    private Map<String, IvrNodeExecutor> executorRegistry()
    {
        Map<String, IvrNodeExecutor> registry = executorRegistry;
        if (registry == null)
        {
            registry = new HashMap<>();
            if (nodeExecutors != null)
            {
                for (IvrNodeExecutor executor : nodeExecutors)
                {
                    for (String type : executor.supportedTypes())
                    {
                        IvrNodeExecutor prev = registry.put(type.toLowerCase(), executor);
                        if (prev != null)
                        {
                            throw new IllegalStateException("IVR节点类型重复注册: " + type
                                    + "（" + prev.getClass().getSimpleName() + " / "
                                    + executor.getClass().getSimpleName() + "）");
                        }
                    }
                }
            }
            executorRegistry = registry;
        }
        return registry;
    }

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

        // 节点执行上下文：执行器就地修改 variables/result/inputs；子流程经 flowRunner 递归（避免 Bean 成环）
        IvrNodeContext nodeContext = new IvrNodeContext(nodeMap, edges, variables, inputs,
                request, sessionId, flow, result, this::executeFlow);

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

            IvrNodeExecutor executor = executorRegistry().get(type);
            if (executor == null)
            {
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

            nodeContext.setCurrent(current);
            nodeContext.setStep(step);
            current = executor.execute(nodeContext);

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

    private IvrExecuteResult failResult(IvrExecuteResult result, String code, String message)
    {
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        result.setStatus("2");
        return result;
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
