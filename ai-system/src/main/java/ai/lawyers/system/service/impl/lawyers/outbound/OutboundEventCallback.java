package ai.lawyers.system.service.impl.lawyers.outbound;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.lawyers.outbound.IAiOutboundTaskService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * 外呼网关事件回调协作类（P5 超大类拆分）。
 *
 * <p>从 {@link OutboundExecutionServiceImpl} 抽出的"网关模式事件处理"叶子职责：
 * 按 callUuid→dialLog→calleeId 精确定位被叫，ANSWERED 建话单置已接通，
 * HANGUP/FAILED 幂等终态化；落库收尾统一走 {@link OutboundCallFinalizer}。
 * 不反向依赖门面，无循环依赖。</p>
 *
 * @author ai-lawyers
 */
@Component
public class OutboundEventCallback
{
    private static final Logger log = LoggerFactory.getLogger(OutboundEventCallback.class);

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private AiOutboundCalleeMapper calleeMapper;

    @Autowired
    private IAiOutboundTaskService taskService;

    @Autowired
    private OutboundCallFinalizer finalizer;

    /** T5-1：外呼指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    /**
     * 网关事件回写入口（ANSWERED / HANGUP / FAILED）。
     */
    public void handle(String callUuid, String eventType, Map<String, Object> params)
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
            handleAnswered(taskId, dialLog, target);
            return;
        }

        if ("HANGUP".equals(event) || "FAILED".equals(event))
        {
            handleHangupOrFailed(taskId, dialLog, target, event, params);
        }
    }

    private void handleAnswered(Long taskId, AiCallDialLog dialLog, AiOutboundCallee target)
    {
        // 幂等：仅呼叫中(1)的号码处理接通，已接通/已终态的重复事件直接跳过
        if (!"1".equals(target.getCallStatus()))
        {
            log.debug("ANSWERED事件忽略：号码不在呼叫中 taskId={} calleeId={} status={}",
                    taskId, target.getCalleeId(), target.getCallStatus());
            return;
        }
        AiCallRecord record = finalizer.createCallRecord(taskId, target);
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
        finalizer.maybeCreateTicket(taskService.selectAiOutboundTaskByTaskId(taskId), target,
                record.getRecordId(), null);
        // T5-1：外呼接通计数（网关模式，ANSWERED 事件）
        if (metrics != null)
        {
            metrics.incrementOutbound("answer");
        }
        log.info("外呼接通 taskId={} calleeId={} recordId={}", taskId, target.getCalleeId(), record.getRecordId());
    }

    private void handleHangupOrFailed(Long taskId, AiCallDialLog dialLog, AiOutboundCallee target,
                                      String event, Map<String, Object> params)
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
        finalizer.finalizeCallee(taskId, target, connected, getTalkSeconds(params, dialLog), event);
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
        String[] prefer = "ANSWERED".equals(event) ? new String[] { "1", "2" } : new String[] { "2", "1" };
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
}
