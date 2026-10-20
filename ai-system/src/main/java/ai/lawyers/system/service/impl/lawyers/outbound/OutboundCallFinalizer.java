package ai.lawyers.system.service.impl.lawyers.outbound;

import static ai.lawyers.system.service.impl.lawyers.outbound.OutboundRetryPolicy.truncate;

import java.util.Date;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundResultService;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.queue.CallEventDispatcher;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * 外呼落库收尾协作类（P5 超大类拆分）。
 *
 * <p>从 {@link OutboundExecutionServiceImpl} 抽出的"写库收尾"叶子职责：
 * 号码状态流转、外呼结果落库、任务计数、话单生成/回写、自动工单。
 * 多步写库统一在本类事务模板内提交，保证计数一致性；不反向依赖门面，无循环依赖。</p>
 *
 * @author ai-lawyers
 */
@Component
public class OutboundCallFinalizer
{
    private static final Logger log = LoggerFactory.getLogger(OutboundCallFinalizer.class);

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
    private AiOutboundTaskMapper taskMapper;

    @Autowired
    private AiOutboundCalleeMapper calleeMapper;

    @Autowired
    private CallEventDispatcher callEventDispatcher;

    /** T5-1：外呼指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** 回访/转人工类外呼接通后自动创建工单（融合点：外呼可触发工单创建） */
    @Value("${call.outbound.autoCreateTicket:false}")
    private boolean autoCreateTicket;

    /** T2-2 失败重试退避基数（毫秒）：base * 2^(retryTimes-1)，上限 10 分钟 */
    @Value("${call.outbound.retry.backoffBaseMs:15000}")
    private long retryBackoffBaseMs;

    /** 多步写库（号码状态 + 结果 + 任务计数）统一在此事务模板内提交，保证一致性 */
    private TransactionTemplate transactionTemplate;

    @jakarta.annotation.PostConstruct
    void initTransactionTemplate()
    {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * W4：合规拦截（退订/非时段）时标记被叫为已跳过，不重试、不计失败，仅计入完成数避免任务卡死。
     */
    public void markCalleeSkipped(AiOutboundCallee callee, String reason)
    {
        try
        {
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("4");
            upd.setCallTime(new Date());
            upd.setFailReason(truncate("合规拦截:" + reason, 500));
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);
            taskMapper.incrementCompletedCount(callee.getTaskId());
        }
        catch (Exception e)
        {
            log.warn("标记被叫跳过失败 calleeId={} reason={}: {}", callee.getCalleeId(), reason, e.getMessage());
        }
    }

    public void markCalleeFailed(final AiOutboundTask task, final AiOutboundCallee callee, final String reason)
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
        // T5-1：外呼终态失败计数（重试耗尽/永久失败；未接不计入此口径）
        if (metrics != null)
        {
            metrics.incrementOutbound("fail");
        }
    }

    public AiCallRecord createCallRecord(Long taskId, AiOutboundCallee callee)
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

    public void updateRecordWithFlow(Long recordId, IvrExecuteResult flowResult)
    {
        try
        {
            AiCallRecord update = new AiCallRecord();
            update.setRecordId(recordId);
            if (flowResult.getCategoryId() != null)
            {
                update.setCategoryId(flowResult.getCategoryId());
            }
            if (ai.lawyers.common.utils.StringUtils.isNotEmpty(flowResult.getTransferTarget()))
            {
                update.setStatus("2");
            }
            update.setRemark("【IVR】流程[" + flowResult.getFlowName() + "] 意图="
                    + flowResult.getMatchedIntentionName() + "(" + flowResult.getMatchedIntention() + ")"
                    + " 分类=" + flowResult.getCategoryName());
            callEventDispatcher.updateCallRecordAsync(update);
        }
        catch (Exception e)
        {
            log.warn("外呼话单回写IVR结果失败 recordId={} error={}", recordId, e.getMessage());
        }
    }

    /**
     * 融合点 3.5.3：外呼任务与工单集成——回访类任务或转人工类外呼接通后自动创建工单。
     */
    public void maybeCreateTicket(AiOutboundTask task, AiOutboundCallee callee,
                                  Long recordId, IvrExecuteResult flowResult)
    {
        if (!autoCreateTicket || task == null || recordId == null)
        {
            return;
        }
        boolean visitTask = "2".equals(task.getTaskType());
        boolean transferNeeded = flowResult != null && ai.lawyers.common.utils.StringUtils.isNotEmpty(flowResult.getTransferTarget());
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
                    + (flowResult != null && ai.lawyers.common.utils.StringUtils.isNotEmpty(flowResult.getMatchedIntentionName())
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

    public void createOutboundResult(AiOutboundTask task, AiOutboundCallee callee, Long recordId,
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
    public void finalizeCallee(final Long taskId, final AiOutboundCallee callee, final boolean connected,
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
                // T2-2 指数退避：网关事件判定未接通的重试同样设置下次重试窗口
                calleeMapper.updateNextRetryTime(callee.getCalleeId(),
                        OutboundRetryPolicy.backoffRetryTime(retryTimes + 1, retryBackoffBaseMs), "outbound-event");
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
}
