package ai.lawyers.system.service.impl.lawyers.outbound;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundResultService;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.outbound.IOutboundExecutionService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;

/**
 * 智能外呼执行引擎实现。
 *
 * 双模式：
 *  - 模拟模式（call.outbound.simulateAnswer=true，默认）：下发成功后直接模拟接通，
 *    进入 IVR 流程并生成话单/结果，便于无语音网关环境联调；
 *  - 网关模式：下发后保持"呼叫中"，由 FreeSWITCH/Asterisk 事件回调最终化。
 *
 * 重试策略：可重试失败（无可用线路/线路忙/网关错误等）在 retryCount 内自动回拨，
 * 重试次数用尽或永久失败后计入失败统计。
 */
@Service
public class OutboundExecutionServiceImpl implements IOutboundExecutionService
{
    private static final Logger log = LoggerFactory.getLogger(OutboundExecutionServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private IAiOutboundTaskService taskService;

    @Autowired
    private IAiOutboundResultService resultService;

    @Autowired
    private IAiCallRecordService callRecordService;

    @Autowired
    private IAiCallTicketService callTicketService;

    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired
    private IIvrEngineService ivrEngineService;

    @Autowired
    private AiOutboundTaskMapper taskMapper;

    @Autowired
    private AiOutboundCalleeMapper calleeMapper;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private Environment environment;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 多步写库（号码状态 + 结果 + 任务计数）统一在此事务模板内提交，保证一致性 */
    private TransactionTemplate transactionTemplate;

    /** 模拟接通：无真实网关时直接完成全链路 */
    @Value("${call.outbound.simulateAnswer:true}")
    private boolean simulateAnswer;

    /** 模拟接通后的通话时长（秒），仅模拟模式使用 */
    @Value("${call.outbound.simulateTalkSeconds:45}")
    private int simulateTalkSeconds;

    /** 是否启用定时扫描执行 */
    @Value("${call.outbound.scanEnabled:true}")
    private boolean scanEnabled;

    /** 回访/转人工类外呼接通后自动创建工单（融合点：外呼可触发工单创建） */
    @Value("${call.outbound.autoCreateTicket:false}")
    private boolean autoCreateTicket;

    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /**
     * R11 防护：模拟接通开关仅允许在开发/测试环境开启。
     * 生产环境若误开，将直接产生假话单/假接通统计，启动时强告警以便第一时间发现配置错误。
     */
    @PostConstruct
    public void checkSimulateSwitch()
    {
        transactionTemplate = new TransactionTemplate(transactionManager);
        if (!simulateAnswer)
        {
            return;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        boolean prodLike = false;
        for (String profile : activeProfiles)
        {
            String p = profile == null ? "" : profile.toLowerCase();
            if (p.contains("prod") || p.contains("pro") || p.contains("release"))
            {
                prodLike = true;
                break;
            }
        }
        if (prodLike)
        {
            log.error("========== 高风险配置告警 ==========");
            log.error("当前为生产环境但 call.outbound.simulateAnswer=true，外呼将被模拟接通并生成虚假话单/统计！");
            log.error("请立即设置环境变量 CALL_OUTBOUND_SIMULATEANSWER=false 或关闭配置后重启！");
            log.error("====================================");
        }
        else
        {
            log.warn("外呼模拟接通已开启(simulateAnswer=true)，仅可用于开发/测试联调，生产环境必须关闭。");
        }
    }

    @Override
    public int executeTask(Long taskId)
    {
        AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        if (task == null)
        {
            log.warn("外呼任务不存在 taskId={}", taskId);
            return 0;
        }
        if (!"1".equals(task.getStatus()))
        {
            log.debug("外呼任务不在执行中状态 taskId={} status={}", taskId, task.getStatus());
            return 0;
        }

        int batchSize = task.getMaxConcurrent() == null || task.getMaxConcurrent() <= 0
                ? 10 : Math.min(task.getMaxConcurrent(), 100);
        List<AiOutboundCallee> pending = calleeMapper.selectPendingCallees(taskId, batchSize);
        if (pending.isEmpty())
        {
            checkTaskCompletion(taskId);
            return 0;
        }

        for (AiOutboundCallee callee : pending)
        {
            // C3：号码级原子领取，多实例并发扫描时只有一个实例能领取成功，影响行数=0 直接跳过
            int claimed = calleeMapper.claimCallee(callee.getCalleeId(), "outbound-executor");
            if (claimed <= 0)
            {
                log.debug("号码已被其他实例领取，跳过 taskId={} calleeId={}", taskId, callee.getCalleeId());
                continue;
            }
            callee.setCallStatus("1");
            callee.setCallTime(new Date());
            try
            {
                dialCallee(task, callee);
            }
            catch (Exception e)
            {
                log.error("外呼号码执行异常 taskId={} calleeId={}", taskId, callee.getCalleeId(), e);
                markCalleeFailed(task, callee, "执行异常：" + e.getMessage());
            }
        }
        checkTaskCompletion(taskId);
        return pending.size();
    }

    @Override
    @Scheduled(fixedDelayString = "${call.outbound.scanIntervalMs:15000}")
    public void scanRunningTasks()
    {
        if (!scanEnabled || !scanning.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            List<AiOutboundTask> running = taskMapper.selectRunningTasks();
            int scanned = 0;
            for (AiOutboundTask task : running)
            {
                if (scanned >= 50)
                {
                    break;
                }
                executeTask(task.getTaskId());
                scanned++;
            }
        }
        catch (Exception e)
        {
            log.error("外呼任务定时扫描异常", e);
        }
        finally
        {
            scanning.set(false);
        }
    }

    @Override
    public void onCallEvent(String callUuid, String eventType, Map<String, Object> params)
    {
        if (callUuid == null || eventType == null)
        {
            return;
        }
        AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(callUuid);
        if (dialLog == null || dialLog.getTaskId() == null)
        {
            return;
        }
        Long taskId = dialLog.getTaskId();
        String event = eventType.toUpperCase();

        // C5：按 callUuid → dialLog → calleeId 精确定位被叫，避免任务内按状态猜号码导致错配；
        // dialLog 无 calleeId（历史数据或人工外呼）时，回退按 recordId/号码匹配。
        AiOutboundCallee target = null;
        if (dialLog.getCalleeId() != null)
        {
            target = calleeMapper.selectAiOutboundCalleeByCalleeId(dialLog.getCalleeId());
        }
        if (target == null)
        {
            List<AiOutboundCallee> callees = calleeMapper.selectAiOutboundCalleeByTaskId(taskId);
            target = findCallee(callees, dialLog, event);
        }
        if (target == null)
        {
            return;
        }

        if ("ANSWERED".equals(event))
        {
            // 幂等：仅呼叫中(1)的号码处理接通，已接通/已终态的重复事件直接跳过
            if (!"1".equals(target.getCallStatus()))
            {
                log.debug("ANSWERED事件忽略：号码不在呼叫中 taskId={} calleeId={} status={}",
                        taskId, target.getCalleeId(), target.getCallStatus());
                return;
            }
            AiCallRecord record = createCallRecord(taskId, target);
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(target.getCalleeId());
            upd.setCallStatus("2");
            upd.setRecordId(record.getRecordId());
            upd.setUpdateBy("outbound-event");
            calleeMapper.updateCalleeStatus(upd);
            // 回写 dialLog.recordId/calleeId，保证后续 HANGUP/FAILED 事件可按 recordId 精确关联
            AiCallDialLog logUpd = new AiCallDialLog();
            logUpd.setLogId(dialLog.getLogId());
            logUpd.setRecordId(record.getRecordId());
            logUpd.setCalleeId(target.getCalleeId());
            logUpd.setUpdateBy("outbound-event");
            dialLogMapper.updateAiCallDialLog(logUpd);
            maybeCreateTicket(taskService.selectAiOutboundTaskByTaskId(taskId), target,
                    record.getRecordId(), null);
            log.info("外呼接通 taskId={} calleeId={} recordId={}", taskId, target.getCalleeId(), record.getRecordId());
            return;
        }

        if ("HANGUP".equals(event) || "FAILED".equals(event))
        {
            // 幂等：已达终态(3未接/4失败/5完成)的号码不再重复处理
            if ("3".equals(target.getCallStatus()) || "4".equals(target.getCallStatus())
                    || "5".equals(target.getCallStatus()))
            {
                log.debug("HANGUP/FAILED事件忽略：号码已终态 taskId={} calleeId={} status={}",
                        taskId, target.getCalleeId(), target.getCallStatus());
                return;
            }
            boolean connected = isConnected(params, dialLog);
            finalizeCallee(taskId, target, connected, getTalkSeconds(params, dialLog), event);
        }
    }

    // ------------------------------------------------------------------ 内部实现

    private void dialCallee(AiOutboundTask task, AiOutboundCallee callee)
    {
        Date dialTime = new Date();
        // 号码已由 executeTask 通过 claimCallee 原子置为呼叫中(1)，此处不再重复置状态

        DialRequest request = new DialRequest();
        request.setCalleeNumber(callee.getCalleeNumber());
        request.setCallerNumber(task.getCallerNumber());
        request.setTaskId(task.getTaskId());
        // C5：把被叫ID透传到 dial_log，回调事件按 callUuid→dialLog→calleeId 精确定位
        request.setCalleeId(callee.getCalleeId());
        request.setPriority(task.getPriority() == null ? 100 : task.getPriority());
        request.setAnswerAction(task.getIvrFlowId() == null ? "BRIDGE_AGENT" : "IVR");
        request.setIvrFlowId(task.getIvrFlowId());
        request.setEnableRecord(true);
        request.setCreateBy(task.getCreateBy() == null ? "outbound-executor" : task.getCreateBy());
        request.setRemark("外呼任务:" + task.getTaskNo());

        DialResult result = callDispatchService.dialWithQueue(request);
        if (!result.isSuccess())
        {
            handleDialFailed(task, callee, result);
            return;
        }

        if (simulateAnswer)
        {
            handleSimulatedAnswered(task, callee, dialTime);
        }
        else
        {
            log.info("外呼已下发，等待网关事件 taskId={} calleeId={} uuid={}",
                    task.getTaskId(), callee.getCalleeId(), result.getCallUuid());
        }
    }

    private void handleSimulatedAnswered(AiOutboundTask task, AiOutboundCallee callee, Date dialTime)
    {
        AiCallRecord record = createCallRecord(task.getTaskId(), callee);
        // IVR 流程为模型/远程调用，放在事务外执行，避免长事务占用数据库连接
        IvrExecuteResult flowResult = runIvrFlow(task, callee, record.getRecordId());

        final int talkSeconds = simulateTalkSeconds > 0 ? simulateTalkSeconds : 0;
        final AiOutboundTask fTask = task;
        final AiOutboundCallee fCallee = callee;
        final AiCallRecord fRecord = record;
        final IvrExecuteResult fFlowResult = flowResult;
        final Date fDialTime = dialTime;
        // C4：号码状态、外呼结果、任务计数多步写库放在同一事务内，失败整体回滚，保证计数一致
        transactionTemplate.executeWithoutResult(status ->
        {
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(fCallee.getCalleeId());
            upd.setCallStatus("5");
            upd.setRecordId(fRecord.getRecordId());
            upd.setCallDuration(talkSeconds);
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);

            createOutboundResult(fTask, fCallee, fRecord.getRecordId(), "1", fDialTime, new Date(),
                    talkSeconds, fFlowResult);
            taskMapper.incrementAnsweredCount(fTask.getTaskId());
            taskMapper.incrementCompletedCount(fTask.getTaskId());
        });

        if (flowResult != null)
        {
            updateRecordWithFlow(record.getRecordId(), flowResult);
        }
        maybeCreateTicket(task, callee, record.getRecordId(), flowResult);
        log.info("外呼模拟接通完成 taskId={} calleeId={} recordId={} 意图={}",
                task.getTaskId(), callee.getCalleeId(), record.getRecordId(),
                flowResult == null ? null : flowResult.getMatchedIntentionName());
    }

    private IvrExecuteResult runIvrFlow(AiOutboundTask task, AiOutboundCallee callee, Long recordId)
    {
        if (task.getIvrFlowId() == null)
        {
            return null;
        }
        IvrExecuteRequest req = new IvrExecuteRequest();
        req.setFlowId(task.getIvrFlowId());
        req.setRecordId(recordId);
        req.setCallerNumber(task.getCallerNumber());
        req.setCalleeNumber(callee.getCalleeNumber());
        String sampleInput = sampleInput(callee);
        req.getInputs().add(sampleInput);
        req.getVariables().put("taskId", task.getTaskId());
        req.getVariables().put("taskNo", task.getTaskNo());
        req.getVariables().put("taskName", task.getTaskName());
        req.getVariables().put("calleeName", callee.getCalleeName());
        req.getVariables().put("calleeNumber", callee.getCalleeNumber());
        try
        {
            return ivrEngineService.executeFlow(req);
        }
        catch (Exception e)
        {
            log.warn("外呼IVR流程执行异常 taskId={} calleeId={} error={}",
                    task.getTaskId(), callee.getCalleeId(), e.getMessage());
            return null;
        }
    }

    /** 演示用输入：优先取号码附加参数中的 input 字段，其次姓名，最后默认法律咨询话术 */
    private String sampleInput(AiOutboundCallee callee)
    {
        if (StringUtils.isNotEmpty(callee.getCalleeParams()))
        {
            try
            {
                Map<String, Object> params = MAPPER.readValue(callee.getCalleeParams(),
                        MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                if (params.get("input") != null && StringUtils.isNotEmpty(params.get("input").toString()))
                {
                    return params.get("input").toString();
                }
            }
            catch (Exception ignored)
            {
            }
        }
        if (StringUtils.isNotEmpty(callee.getCalleeName()))
        {
            return "我想咨询" + callee.getCalleeName() + "相关的法律问题";
        }
        return "我想咨询一下法律问题，比如合同纠纷";
    }

    private void handleDialFailed(AiOutboundTask task, AiOutboundCallee callee, DialResult result)
    {
        String reason = result == null ? "未知错误" : result.getMessage();
        int retryTimes = callee.getRetryTimes() == null ? 0 : callee.getRetryTimes();
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();

        if (retryTimes < retryCount && isRetryableError(result))
        {
            // 重试：号码回到待呼叫队列，等待下一轮扫描
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("0");
            upd.setRetryTimes(retryTimes + 1);
            upd.setLastRetryTime(new Date());
            upd.setFailReason(truncate(reason, 500));
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);
            log.info("外呼失败进入重试 taskId={} calleeId={} 第{}次 reason={}",
                    task.getTaskId(), callee.getCalleeId(), retryTimes + 1, reason);
            return;
        }

        markCalleeFailed(task, callee, reason);
    }

    private void markCalleeFailed(final AiOutboundTask task, final AiOutboundCallee callee, final String reason)
    {
        // C4：号码失败状态、外呼结果、任务失败/完成计数同事务提交
        transactionTemplate.executeWithoutResult(status ->
        {
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("4");
            upd.setCallTime(new Date());
            upd.setFailReason(truncate(reason, 500));
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);

            createOutboundResult(task, callee, null, "7", new Date(), new Date(), 0, null);
            taskMapper.incrementFailedCount(task.getTaskId());
            taskMapper.incrementCompletedCount(task.getTaskId());
        });
    }

    private AiCallRecord createCallRecord(Long taskId, AiOutboundCallee callee)
    {
        AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(task == null ? null : task.getCallerNumber());
        record.setCallerName(callee.getCalleeName());
        record.setCallerAddress(callee.getCalleeAddress());
        record.setCallTime(new Date());
        record.setStatus("1");
        record.setContent("外呼任务[" + (task == null ? "" : task.getTaskNo())
                + "] 被叫号码:" + callee.getCalleeNumber());
        record.setRemark("智能外呼接通，任务:" + (task == null ? "" : task.getTaskNo()));
        record.setCreateBy(task == null ? "outbound-executor" : task.getCreateBy());
        callRecordService.insertAiCallRecord(record);
        return record;
    }

    private void updateRecordWithFlow(Long recordId, IvrExecuteResult flowResult)
    {
        try
        {
            AiCallRecord update = new AiCallRecord();
            update.setRecordId(recordId);
            if (flowResult.getCategoryId() != null)
            {
                update.setCategoryId(flowResult.getCategoryId());
            }
            if (StringUtils.isNotEmpty(flowResult.getTransferTarget()))
            {
                update.setStatus("2");
            }
            update.setRemark("【IVR】流程[" + flowResult.getFlowName() + "] 意图="
                    + flowResult.getMatchedIntentionName() + "(" + flowResult.getMatchedIntention() + ")"
                    + " 分类=" + flowResult.getCategoryName());
            callRecordService.updateAiCallRecord(update);
        }
        catch (Exception e)
        {
            log.warn("外呼话单回写IVR结果失败 recordId={} error={}", recordId, e.getMessage());
        }
    }

    /**
     * 融合点 3.5.3：外呼任务与工单集成——回访类任务或转人工类外呼接通后自动创建工单。
     */
    private void maybeCreateTicket(AiOutboundTask task, AiOutboundCallee callee,
                                   Long recordId, IvrExecuteResult flowResult)
    {
        if (!autoCreateTicket || task == null || recordId == null)
        {
            return;
        }
        boolean visitTask = "2".equals(task.getTaskType());
        boolean transferNeeded = flowResult != null && StringUtils.isNotEmpty(flowResult.getTransferTarget());
        if (!visitTask && !transferNeeded)
        {
            return;
        }
        try
        {
            AiCallTicket ticket = new AiCallTicket();
            ticket.setRecordId(recordId);
            ticket.setTitle((visitTask ? "回访工单" : "跟进工单") + "：" + task.getTaskName());
            ticket.setContent("外呼任务[" + task.getTaskNo() + "] 被叫号码:" + callee.getCalleeNumber()
                    + " 被叫姓名:" + (callee.getCalleeName() == null ? "" : callee.getCalleeName())
                    + (flowResult != null && StringUtils.isNotEmpty(flowResult.getMatchedIntentionName())
                            ? " 识别意图:" + flowResult.getMatchedIntentionName() : "")
                    + "。请及时跟进处理。");
            ticket.setPriority("2");
            ticket.setStatus("0");
            ticket.setAssignUserName(task.getCallerName());
            ticket.setCreateBy(task.getCreateBy() == null ? "outbound-executor" : task.getCreateBy());
            callTicketService.insertAiCallTicket(ticket);
            log.info("外呼自动创建工单 taskId={} calleeId={} ticketNo={}",
                    task.getTaskId(), callee.getCalleeId(), ticket.getTicketNo());
        }
        catch (Exception e)
        {
            log.warn("外呼自动创建工单失败: {}", e.getMessage());
        }
    }

    private void createOutboundResult(AiOutboundTask task, AiOutboundCallee callee, Long recordId,
                                      String callResult, Date startTime, Date endTime,
                                      int duration, IvrExecuteResult flowResult)
    {
        AiOutboundResult result = new AiOutboundResult();
        result.setTaskId(task.getTaskId());
        result.setCalleeId(callee.getCalleeId());
        result.setCalleeNumber(callee.getCalleeNumber());
        result.setCalleeName(callee.getCalleeName());
        result.setRecordId(recordId);
        result.setCallResult(callResult);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setCallDuration(duration);
        if (flowResult != null)
        {
            result.setIntentionCode(flowResult.getMatchedIntention());
            result.setIntentionName(flowResult.getMatchedIntentionName());
            result.setSummary("IVR流程[" + flowResult.getFlowName() + "]执行完成，共"
                    + (flowResult.getSteps() == null ? 0 : flowResult.getSteps().size()) + "个节点");
            try
            {
                result.setFlowData(MAPPER.writeValueAsString(flowResult));
            }
            catch (Exception ignored)
            {
            }
        }
        else
        {
            result.setSummary("外呼完成（无IVR流程）");
        }
        result.setCreateBy(task.getCreateBy());
        resultService.insertAiOutboundResult(result);
    }

    /** 网关事件：最终化号码（接通/未接/失败）并生成结果 */
    private void finalizeCallee(final Long taskId, final AiOutboundCallee callee, final boolean connected,
                                final int talkSeconds, final String event)
    {
        final AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        if (task == null)
        {
            return;
        }
        final int retryTimes = callee.getRetryTimes() == null ? 0 : callee.getRetryTimes();
        final int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();

        // C4：号码状态流转、外呼结果、任务计数在同一事务内提交，避免中途失败导致计数与状态不一致
        transactionTemplate.executeWithoutResult(status ->
        {
            if (connected)
            {
                AiOutboundCallee upd = new AiOutboundCallee();
                upd.setCalleeId(callee.getCalleeId());
                upd.setCallStatus("5");
                upd.setCallDuration(talkSeconds);
                upd.setUpdateBy("outbound-event");
                calleeMapper.updateCalleeStatus(upd);

                createOutboundResult(task, callee, callee.getRecordId(), "1",
                        callee.getCallTime(), new Date(), talkSeconds, null);
                taskMapper.incrementAnsweredCount(taskId);
                taskMapper.incrementCompletedCount(taskId);
                return;
            }

            if (retryTimes < retryCount && !"FAILED".equalsIgnoreCase(event))
            {
                AiOutboundCallee upd = new AiOutboundCallee();
                upd.setCalleeId(callee.getCalleeId());
                upd.setCallStatus("0");
                upd.setRetryTimes(retryTimes + 1);
                upd.setLastRetryTime(new Date());
                upd.setUpdateBy("outbound-event");
                calleeMapper.updateCalleeStatus(upd);
                log.info("外呼未接通进入重试 taskId={} calleeId={} 第{}次", taskId, callee.getCalleeId(), retryTimes + 1);
                return;
            }

            String callResult = "FAILED".equalsIgnoreCase(event) ? "4" : "2";
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("FAILED".equalsIgnoreCase(event) ? "4" : "3");
            upd.setCallDuration(0);
            upd.setFailReason("网关事件:" + event);
            upd.setUpdateBy("outbound-event");
            calleeMapper.updateCalleeStatus(upd);

            createOutboundResult(task, callee, callee.getRecordId(), callResult,
                    callee.getCallTime(), new Date(), 0, null);
            if ("FAILED".equalsIgnoreCase(event))
            {
                taskMapper.incrementFailedCount(taskId);
            }
            else
            {
                taskMapper.incrementNoAnswerCount(taskId);
            }
            taskMapper.incrementCompletedCount(taskId);
        });
    }

    private void checkTaskCompletion(Long taskId)
    {
        AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        if (task == null || !"1".equals(task.getStatus()))
        {
            return;
        }
        int pending = calleeMapper.countByTaskIdAndStatus(taskId, "0");
        int calling = calleeMapper.countByTaskIdAndStatus(taskId, "1");
        int answered = calleeMapper.countByTaskIdAndStatus(taskId, "2");
        if (pending + calling + answered == 0)
        {
            AiOutboundTask upd = new AiOutboundTask();
            upd.setTaskId(taskId);
            upd.setStatus("2");
            upd.setUpdateBy("outbound-executor");
            taskService.updateAiOutboundTask(upd);
            log.info("外呼任务全部执行完成 taskId={}", taskId);
        }
    }

    /**
     * 回退匹配（dialLog 无 calleeId 时使用）：优先 recordId 精确匹配，其次被叫号码精确匹配，
     * 最后按事件类型选择状态匹配（ANSWERED→呼叫中1/已接通2，HANGUP/FAILED→已接通2）。
     */
    private AiOutboundCallee findCallee(List<AiOutboundCallee> callees, AiCallDialLog dialLog, String event)
    {
        if (callees == null || callees.isEmpty())
        {
            return null;
        }
        if (dialLog != null && dialLog.getRecordId() != null)
        {
            for (AiOutboundCallee c : callees)
            {
                if (dialLog.getRecordId().equals(c.getRecordId()))
                {
                    return c;
                }
            }
        }
        if (dialLog != null && StringUtils.isNotEmpty(dialLog.getCalleeNumber()))
        {
            for (AiOutboundCallee c : callees)
            {
                if (dialLog.getCalleeNumber().equals(c.getCalleeNumber()))
                {
                    return c;
                }
            }
        }
        String[] prefer = "ANSWERED".equals(event) ? new String[]{"1", "2"} : new String[]{"2", "1"};
        for (String status : prefer)
        {
            for (AiOutboundCallee c : callees)
            {
                if (status.equals(c.getCallStatus()))
                {
                    return c;
                }
            }
        }
        return null;
    }

    private boolean isConnected(Map<String, Object> params, AiCallDialLog dialLog)
    {
        if (dialLog != null && ("1".equals(dialLog.getIsConnected()) || dialLog.getAnswerTime() != null))
        {
            return true;
        }
        if (params == null)
        {
            return false;
        }
        Object connected = params.get("isConnected");
        if (connected != null)
        {
            return "1".equals(connected.toString());
        }
        Object talk = params.get("talkDuration");
        if (talk == null)
        {
            return false;
        }
        // R12：回调参数可能为非数字，parseInt 需防护，避免异常中断事件处理
        try
        {
            return Integer.parseInt(talk.toString()) > 0;
        }
        catch (NumberFormatException e)
        {
            log.warn("talkDuration 参数非数字，按未接通处理 value={}", talk);
            return false;
        }
    }

    private int getTalkSeconds(Map<String, Object> params, AiCallDialLog dialLog)
    {
        if (params != null && params.get("talkDuration") != null)
        {
            try
            {
                return Integer.parseInt(params.get("talkDuration").toString());
            }
            catch (Exception ignored)
            {
            }
        }
        if (dialLog != null && dialLog.getTalkDuration() != null)
        {
            return dialLog.getTalkDuration();
        }
        return 0;
    }

    private boolean isRetryableError(DialResult result)
    {
        if (result == null || result.getErrorCode() == null)
        {
            return false;
        }
        switch (result.getErrorCode())
        {
            case "NO_AVAILABLE_TRUNK":
            case "TRUNK_BUSY":
            case "CPS_LIMIT":
            case "QUEUE_FULL":
            case "GATEWAY_ERROR":
            case "GATEWAY_REJECT":
            case "DIAL_FAILED":
                return true;
            default:
                return false;
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
