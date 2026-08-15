package ai.lawyers.system.service.impl.lawyers.outbound;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
        List<AiOutboundCallee> callees = calleeMapper.selectAiOutboundCalleeByTaskId(taskId);
        if (callees == null || callees.isEmpty())
        {
            return;
        }

        if ("ANSWERED".equals(event))
        {
            AiOutboundCallee target = findCallee(callees, null, "1");
            if (target != null)
            {
                AiCallRecord record = createCallRecord(taskId, target);
                maybeCreateTicket(taskService.selectAiOutboundTaskByTaskId(taskId), target,
                        record.getRecordId(), null);
                AiOutboundCallee upd = new AiOutboundCallee();
                upd.setCalleeId(target.getCalleeId());
                upd.setCallStatus("2");
                upd.setRecordId(record.getRecordId());
                upd.setUpdateBy("outbound-event");
                calleeMapper.updateCalleeStatus(upd);
                log.info("外呼接通 taskId={} calleeId={} recordId={}", taskId, target.getCalleeId(), record.getRecordId());
            }
            return;
        }

        if ("HANGUP".equals(event) || "FAILED".equals(event))
        {
            AiOutboundCallee target = findCallee(callees, dialLog.getRecordId(), null);
            if (target != null)
            {
                boolean connected = isConnected(params, dialLog);
                finalizeCallee(taskId, target, connected, getTalkSeconds(params, dialLog), event);
            }
        }
    }

    // ------------------------------------------------------------------ 内部实现

    private void dialCallee(AiOutboundTask task, AiOutboundCallee callee)
    {
        Date dialTime = new Date();
        markCalleeCalling(callee, dialTime);

        DialRequest request = new DialRequest();
        request.setCalleeNumber(callee.getCalleeNumber());
        request.setCallerNumber(task.getCallerNumber());
        request.setTaskId(task.getTaskId());
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
        IvrExecuteResult flowResult = runIvrFlow(task, callee, record.getRecordId());

        int talkSeconds = simulateTalkSeconds > 0 ? simulateTalkSeconds : 0;
        AiOutboundCallee upd = new AiOutboundCallee();
        upd.setCalleeId(callee.getCalleeId());
        upd.setCallStatus("5");
        upd.setRecordId(record.getRecordId());
        upd.setCallDuration(talkSeconds);
        upd.setUpdateBy("outbound-executor");
        calleeMapper.updateCalleeStatus(upd);

        createOutboundResult(task, callee, record.getRecordId(), "1", dialTime, new Date(),
                talkSeconds, flowResult);
        taskMapper.incrementAnsweredCount(task.getTaskId());
        taskMapper.incrementCompletedCount(task.getTaskId());

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

    private void markCalleeFailed(AiOutboundTask task, AiOutboundCallee callee, String reason)
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
    }

    private void markCalleeCalling(AiOutboundCallee callee, Date dialTime)
    {
        AiOutboundCallee upd = new AiOutboundCallee();
        upd.setCalleeId(callee.getCalleeId());
        upd.setCallStatus("1");
        upd.setCallTime(dialTime);
        upd.setUpdateBy("outbound-executor");
        calleeMapper.updateCalleeStatus(upd);
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
    private void finalizeCallee(Long taskId, AiOutboundCallee callee, boolean connected,
                                int talkSeconds, String event)
    {
        AiOutboundTask task = taskService.selectAiOutboundTaskByTaskId(taskId);
        if (task == null)
        {
            return;
        }
        int retryTimes = callee.getRetryTimes() == null ? 0 : callee.getRetryTimes();
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();

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

    private AiOutboundCallee findCallee(List<AiOutboundCallee> callees, Long recordId, String preferStatus)
    {
        for (AiOutboundCallee c : callees)
        {
            if (recordId != null && recordId.equals(c.getRecordId()))
            {
                return c;
            }
        }
        for (AiOutboundCallee c : callees)
        {
            if (preferStatus != null && preferStatus.equals(c.getCallStatus()))
            {
                return c;
            }
        }
        for (AiOutboundCallee c : callees)
        {
            if ("2".equals(c.getCallStatus()) || "1".equals(c.getCallStatus()))
            {
                return c;
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
        return talk != null && Integer.parseInt(talk.toString()) > 0;
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
