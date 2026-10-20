package ai.lawyers.system.service.impl.lawyers.outbound;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundTask;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundTaskMapper;
import ai.lawyers.system.service.lawyers.IAiHotspotSuppressService;
import ai.lawyers.system.service.lawyers.compliance.ComplianceGuard;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

/**
 * 外呼拨号执行协作类（P5 超大类拆分）。
 *
 * <p>从 {@link OutboundExecutionServiceImpl} 抽出的"单号码拨号"叶子职责：
 * 合规拦截、高频置底判定、DialRequest 组装下发、模拟接通、IVR 流程执行、失败重试。
 * 依赖 {@link OutboundCallFinalizer} 落库收尾；不反向依赖门面，无循环依赖。</p>
 *
 * @author ai-lawyers
 */
@Component
public class OutboundDialer
{
    private static final Logger log = LoggerFactory.getLogger(OutboundDialer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 外呼置底沉底偏移量：外呼内存队列按 priority 升序（越小越优先），与入站 ACD desc 相反，
     * 沉底=抬高数值；加固定偏移而非取极大值，保持同批被压制号码间仍按任务原优先级有序。
     */
    private static final int HOTSPOT_SINK_OFFSET = 10000;

    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired
    private IIvrEngineService ivrEngineService;

    @Autowired
    private AiOutboundCalleeMapper calleeMapper;

    @Autowired
    private AiOutboundTaskMapper taskMapper;

    /** W4：合规守卫（可选 Bean，缺失时视为不拦截） */
    @Autowired(required = false)
    private ComplianceGuard complianceGuard;

    /** T5-1：外呼指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    @Autowired
    private OutboundCallFinalizer finalizer;

    /** P3-D1 高频置底：外呼拨号前号码规则判定（REJECT 跳过 / PRIORITY 沉底） */
    @Autowired
    private IAiHotspotSuppressService hotspotSuppressService;

    /** 高频置底总开关（与入站共用） */
    @Value("${hotspot.suppress.enabled:true}")
    private boolean hotspotEnabled;

    /** 模拟接通：无真实网关时直接完成全链路 */
    @Value("${call.outbound.simulateAnswer:true}")
    private boolean simulateAnswer;

    /** 模拟接通后的通话时长（秒），仅模拟模式使用 */
    @Value("${call.outbound.simulateTalkSeconds:45}")
    private int simulateTalkSeconds;

    /** T2-2 失败重试退避基数（毫秒）：base * 2^(retryTimes-1)，上限 10 分钟 */
    @Value("${call.outbound.retry.backoffBaseMs:15000}")
    private long retryBackoffBaseMs;

    /** 模拟接通多步写库事务模板 */
    private TransactionTemplate transactionTemplate;

    @jakarta.annotation.PostConstruct
    void initTransactionTemplate()
    {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Autowired
    private PlatformTransactionManager transactionManager;

    public void dialCallee(AiOutboundTask task, AiOutboundCallee callee)
    {
        // W4：退订号码拦截（已回复"T"退订的号码禁止外呼）
        if (complianceGuard != null && complianceGuard.isUnsubscribed(callee.getCalleeNumber()))
        {
            log.info("外呼号码已退订，跳过拨号 taskId={} calleeId={}", task.getTaskId(), callee.getCalleeId());
            finalizer.markCalleeSkipped(callee, "UNSUBSCRIBED");
            return;
        }
        // P3-D1：高频置底外呼侧判定——命中 REJECT 跳过拨号（状态4跳过、不计失败、计入完成防任务卡死）；
        // 命中 PRIORITY 抬高排队优先级数值沉底（外呼队列升序，方向与入站相反）
        AiHotspotSuppress suppress = null;
        if (hotspotEnabled)
        {
            suppress = hotspotSuppressService.matchOutbound(callee.getCalleeNumber(), task.getTaskId());
        }
        if (suppress != null && "REJECT".equals(suppress.getAction()))
        {
            log.info("外呼号码命中高频置底规则[{}]，跳过拨号 taskId={} calleeId={}",
                    suppress.getRuleName(), task.getTaskId(), callee.getCalleeId());
            finalizer.markCalleeSkipped(callee, "高频置底拦截:" + suppress.getRuleName());
            return;
        }
        Date dialTime = new Date();
        // 号码已由 executeTask 通过 claimCallee 原子置为呼叫中(1)，此处不再重复置状态

        DialRequest request = new DialRequest();
        request.setCalleeNumber(callee.getCalleeNumber());
        request.setCallerNumber(task.getCallerNumber());
        request.setTaskId(task.getTaskId());
        // C5：把被叫ID透传到 dial_log，回调事件按 callUuid→dialLog→calleeId 精确定位
        request.setCalleeId(callee.getCalleeId());
        int dialPriority = task.getPriority() == null ? 100 : task.getPriority();
        if (suppress != null && "PRIORITY".equals(suppress.getAction()))
        {
            log.info("外呼号码命中置底降权规则[{}]，排队优先级 {} -> {} taskId={} calleeId={}",
                    suppress.getRuleName(), dialPriority, dialPriority + HOTSPOT_SINK_OFFSET,
                    task.getTaskId(), callee.getCalleeId());
            dialPriority += HOTSPOT_SINK_OFFSET;
        }
        request.setPriority(dialPriority);
        request.setAnswerAction(task.getIvrFlowId() == null ? "BRIDGE_AGENT" : "IVR");
        request.setIvrFlowId(task.getIvrFlowId());
        request.setEnableRecord(true);
        request.setCreateBy(task.getCreateBy() == null ? "outbound-executor" : task.getCreateBy());
        request.setRemark("外呼任务:" + task.getTaskNo());

        DialResult result = callDispatchService.dialWithQueue(request);
        // T5-1：外呼下发计数（含重试再拨，每次成功下发计 1）
        if (metrics != null && result.isSuccess())
        {
            metrics.incrementOutbound("dial");
        }
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
        AiCallRecord record = finalizer.createCallRecord(task.getTaskId(), callee);
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

            finalizer.createOutboundResult(fTask, fCallee, fRecord.getRecordId(), "1", fDialTime, new Date(),
                    talkSeconds, fFlowResult);
            taskMapper.incrementAnsweredCount(fTask.getTaskId());
            taskMapper.incrementCompletedCount(fTask.getTaskId());
        });

        if (flowResult != null)
        {
            finalizer.updateRecordWithFlow(record.getRecordId(), flowResult);
        }
        finalizer.maybeCreateTicket(task, callee, record.getRecordId(), flowResult);
        // T5-1：外呼接通计数（模拟模式）
        if (metrics != null)
        {
            metrics.incrementOutbound("answer");
        }
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

        if (retryTimes < retryCount && OutboundRetryPolicy.isRetryableError(result))
        {
            // 重试：号码回到待呼叫队列，等待下一轮扫描
            AiOutboundCallee upd = new AiOutboundCallee();
            upd.setCalleeId(callee.getCalleeId());
            upd.setCallStatus("0");
            upd.setRetryTimes(retryTimes + 1);
            upd.setLastRetryTime(new Date());
            upd.setFailReason(OutboundRetryPolicy.truncate(reason, 500));
            upd.setUpdateBy("outbound-executor");
            calleeMapper.updateCalleeStatus(upd);
            // T2-2 指数退避：记录下次可重试时间，未到期号码扫描时不捞出，避免失败号码被每 15s 反复重拨
            calleeMapper.updateNextRetryTime(callee.getCalleeId(),
                    OutboundRetryPolicy.backoffRetryTime(retryTimes + 1, retryBackoffBaseMs), "outbound-executor");
            // T5-1：外呼重试计数
            if (metrics != null)
            {
                metrics.incrementOutbound("retry");
            }
            log.info("外呼失败进入重试 taskId={} calleeId={} 第{}次 reason={}",
                    task.getTaskId(), callee.getCalleeId(), retryTimes + 1, reason);
            return;
        }

        finalizer.markCalleeFailed(task, callee, reason);
    }
}
