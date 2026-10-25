package ai.lawyers.system.service.lawyers.trunk.event;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.service.impl.lawyers.storage.RecordingUploadService;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;
import ai.lawyers.system.service.lawyers.queue.QualityTranscribeDispatcher;

/**
 * P3-B2：统一呼叫事件处理器（Stream 消费侧 / 降级直调共用）。
 *
 * <p>集中原 ESL/AMI 两侧桥的事件消费逻辑，语义与原实现保持一致：</p>
 * <ul>
 *   <li>{@code INBOUND} → {@link InboundCallHandler}（来电弹屏/排队/IVR）；</li>
 *   <li>{@code RINGING}/{@code ANSWERED}/{@code HANGUP} → {@link ICallDispatchService#onCallEvent}
 *       状态机（B4 终态幂等兜底在此下游继续生效）；</li>
 *   <li>{@code ANSWERED}/{@code BRIDGED}/{@code HANGUP}/{@code DTMF} → 坐席 WebSocket 推送
 *       （dialLog 反查坐席 → variable_ai_record_id 兜底 → recordId 反查，定向否则广播）；</li>
 *   <li>{@code HANGUP} 且 payload.autoTicket=true（仅 ESL 入站正常挂断生产侧置位）→ 自动建工单；</li>
 *   <li>{@code RECORD_STOP} → 录音路径/时长回写 + 对象存储上传排队 + 质检转写投递；</li>
 *   <li>{@code AGENT_STATUS} → 队列成员设备态同步坐席状态（变化才写库）。</li>
 * </ul>
 *
 * <p>at-least-once 说明：Stream 重试/降级都可能造成同一事件重复到达，幂等由
 * 生产侧第一道判重 + 状态机终态/CAS 更新/工单存在性检查共同兜底。</p>
 *
 * @author ai-lawyers
 */
@Service
public class CallEventProcessor implements ICallEventProcessor
{
    private static final Logger log = LoggerFactory.getLogger(CallEventProcessor.class);

    /** P10/D1：下游动作统一走进程内事件，避免反向依赖调度/坐席状态/入站处理器形成环 */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private CallEventPublisher callEventPublisher;

    @Autowired
    private AiCallAgentStatusMapper agentStatusMapper;

    @Autowired
    private IAiCallTicketService callTicketService;

    @Autowired
    private AiCallTicketMapper callTicketMapper;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired(required = false)
    private QualityTranscribeDispatcher qualityTranscribeDispatcher;

    @Autowired(required = false)
    private HotlineMetrics metrics;

    /** F2：录音对象存储上传服务（对象存储模式下接管上传） */
    @Autowired(required = false)
    private RecordingUploadService recordingUploadService;

    /** 事件总入口：按归一事件名分发，单事件异常不影响后续事件 */
    public void handle(CallEvent event)
    {
        if (event == null || StringUtils.isEmpty(event.getEventName()))
        {
            return;
        }
        try
        {
            switch (event.getEventName())
            {
                case CallEvent.INBOUND:
                    onInbound(event);
                    break;
                case CallEvent.RINGING:
                    onRinging(event);
                    break;
                case CallEvent.ANSWERED:
                    onAnswered(event);
                    break;
                case CallEvent.BRIDGED:
                    onBridged(event);
                    break;
                case CallEvent.HANGUP:
                    onHangup(event);
                    break;
                case CallEvent.DTMF:
                    onDtmf(event);
                    break;
                case CallEvent.RECORD_STOP:
                    onRecordStop(event);
                    break;
                case CallEvent.AGENT_STATUS:
                    onAgentStatus(event);
                    break;
                default:
                    log.debug("[CallEvent] 未识别事件名已忽略: source={} name={} session={}",
                            event.getSource(), event.getEventName(), event.getSessionId());
            }
        }
        catch (Exception e)
        {
            log.error("[CallEvent] 处理事件异常: source={} name={} session={}",
                    event.getSource(), event.getEventName(), event.getSessionId(), e);
        }
    }

    /** 入站来话：触发入站处理（来电弹屏 → 排队 → 分配坐席） */
    private void onInbound(CallEvent event)
    {
        String uuid = event.getSessionId();
        log.info("[CallEvent] 入站来话: source={} uuid={} {} -> {}",
                event.getSource(), uuid, event.getCaller(), event.getCallee());
        // P10/D1：host 取 source 中 "ESL:/AMI:" 之后部分，由监听器按原 InboundCallHandler 入参组装
        eventPublisher.publishEvent(new InboundCallEvent(
                sourceHost(event.getSource()), uuid, event.getCaller(), event.getCallee()));
    }

    /** 外呼振铃：进状态机（AMI Dial[Begin] 产生；ESL 侧振铃由调度服务自身写拨号日志） */
    private void onRinging(CallEvent event)
    {
        if (!event.hasSession())
        {
            return;
        }
        log.info("[CallEvent] 外呼振铃: source={} uuid={}", event.getSource(), event.getSessionId());
        eventPublisher.publishEvent(new CallStateChangeEvent(event.getSessionId(), "RINGING", new HashMap<>()));
    }

    /** 接通：指标 + 坐席推送 + 状态机 */
    private void onAnswered(CallEvent event)
    {
        if (!event.hasSession())
        {
            return;
        }
        log.info("[CallEvent] 通道接通: source={} uuid={}", event.getSource(), event.getSessionId());
        if (metrics != null)
        {
            metrics.incrementCall("answered");
        }
        pushEventToAgent(event, "ANSWERED", null);
        Map<String, Object> params = new HashMap<>();
        params.put("answerTime", new Date(event.getTimestamp()));
        eventPublisher.publishEvent(new CallStateChangeEvent(event.getSessionId(), "ANSWERED", params));
    }

    /** 桥接：坐席推送 */
    private void onBridged(CallEvent event)
    {
        if (!event.hasSession())
        {
            return;
        }
        log.info("[CallEvent] 通道桥接: source={} uuid={}", event.getSource(), event.getSessionId());
        pushEventToAgent(event, "BRIDGED", null);
    }

    /** 挂断：状态机 + 未接指标 + 坐席推送 + 入站自动建工单（payload.autoTicket 旗标门控） */
    private void onHangup(CallEvent event)
    {
        if (!event.hasSession())
        {
            return;
        }
        String uuid = event.getSessionId();
        String cause = event.payloadString("hangupCause");
        int talkDuration = event.payloadInt("talkDuration", 0);
        log.info("[CallEvent] 通道挂断: source={} uuid={} cause={} talk={}s",
                event.getSource(), uuid, cause, talkDuration);

        Map<String, Object> params = new HashMap<>();
        params.put("hangupCause", cause);
        params.put("talkDuration", talkDuration);
        eventPublisher.publishEvent(new CallStateChangeEvent(uuid, "HANGUP", params));

        // T5-1：未接/丢弃（无通话时长）计数
        if (metrics != null && talkDuration == 0)
        {
            metrics.incrementCall("abandoned");
        }

        pushEventToAgent(event, "HANGUP", cause);

        // 入站通话正常结束自动建工单：由生产侧（仅 ESL 入站 NORMAL_CLEARING 且有通话时长）置旗标，
        // AMI 侧不置位（保持 V2.50 边界：AMI 无自动建工单）；消费侧再做存在性幂等
        if (event.payloadBool("autoTicket"))
        {
            tryAutoCreateInboundTicket(uuid);
        }
    }

    /** DTMF：透传坐席（extra=按键） */
    private void onDtmf(CallEvent event)
    {
        if (!event.hasSession())
        {
            return;
        }
        String digit = event.payloadString("digit");
        log.debug("[CallEvent] DTMF: source={} uuid={} digit={}", event.getSource(), event.getSessionId(), digit);
        pushEventToAgent(event, "DTMF", digit);
    }

    /**
     * 录音停止：把录音文件路径与时长回写到话单，再排队对象存储上传与质检转写。
     * 关联键：recordId 已由生产侧解析透传（ESL 侧按 uuid/Other-Leg/Channel-Call-UUID/
     * 拨号日志/variable_ai_record_id 多候选解析）。
     */
    private void onRecordStop(CallEvent event)
    {
        Long recordId = event.getRecordId();
        String recordPath = event.payloadString("recordPath");
        Integer recordSeconds = null;
        int seconds = event.payloadInt("recordSeconds", -1);
        if (seconds >= 0)
        {
            recordSeconds = seconds;
        }
        log.info("[CallEvent] 录音停止: source={} uuid={} recordId={} file={} seconds={}",
                event.getSource(), event.getSessionId(), recordId, recordPath, recordSeconds);
        if (recordId == null)
        {
            log.warn("[CallEvent] RECORD_STOP 未找到关联话单: source={} uuid={} file={}",
                    event.getSource(), event.getSessionId(), recordPath);
            return;
        }

        try
        {
            AiCallRecord update = new AiCallRecord();
            update.setRecordId(recordId);
            if (StringUtils.isNotEmpty(recordPath))
            {
                // F2：对象存储模式下，先暂存本地路径（供上传服务读取），上传成功后再回写 object key
                update.setRecordFile(recordPath);
                // 对外访问 URL 统一走后端播放接口，前端可直接用 <audio src=...>
                update.setRecordingUrl("/lawyers/call/record/" + recordId + "/play");
            }
            if (recordSeconds != null)
            {
                update.setRecordDuration(recordSeconds);
            }
            callRecordMapper.updateRecordingInfo(update);
            log.info("[CallEvent] 已回写录音信息: recordId={} file={} duration={}s",
                    recordId, recordPath, recordSeconds);

            // F2/P0-5：排队异步处理（S3 上传/本地加密）。不再预判 exists——
            // RECORD_STOP 与落盘存在竞态，worker 内部会等待文件就绪确认
            if (recordingUploadService != null && StringUtils.isNotEmpty(recordPath))
            {
                try
                {
                    recordingUploadService.enqueue(recordId, new File(recordPath));
                }
                catch (Exception ex)
                {
                    log.warn("[CallEvent] 录音任务排队失败 recordId={}: {}", recordId, ex.getMessage());
                }
            }

            // T4-1 录音文件已就绪，投递质检转写队列（队列不可用时内部同步降级）
            if (qualityTranscribeDispatcher != null && StringUtils.isNotEmpty(recordPath))
            {
                try
                {
                    qualityTranscribeDispatcher.enqueue(recordId);
                }
                catch (Exception ex)
                {
                    log.warn("[CallEvent] 投递质检任务失败 recordId={}: {}", recordId, ex.getMessage());
                }
            }
        }
        catch (Exception e)
        {
            log.error("[CallEvent] 回写录音信息失败: recordId={} file={}", recordId, recordPath, e);
        }
    }

    /** 队列成员设备态 → 发布同步事件（仅变化时写库的判定在监听器内完成） */
    private void onAgentStatus(CallEvent event)
    {
        String ext = event.payloadString("ext");
        String target = event.payloadString("targetStatus");
        if (StringUtils.isEmpty(ext) || StringUtils.isEmpty(target))
        {
            return;
        }
        eventPublisher.publishEvent(new AgentStatusSyncEvent(ext, target));
    }

    /**
     * 入站通话挂断后自动创建工单。
     * <p>条件：能通过 uuid 找到 ai_call_record；该 recordId 尚未生成工单；
     * 通话状态为已完成（非未接）。已存在工单则跳过，避免重复创建（at-least-once 幂等兜底）。</p>
     */
    private void tryAutoCreateInboundTicket(String uuid)
    {
        try
        {
            AiCallRecord record = callRecordMapper.selectAiCallRecordByCallUuid(uuid);
            if (record == null)
            {
                // 入站 PSTN 通话可能未写 call_uuid，尝试通过拨号日志反查
                AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(uuid);
                if (dialLog != null && dialLog.getRecordId() != null)
                {
                    record = callRecordMapper.selectAiCallRecordByRecordId(dialLog.getRecordId());
                }
            }
            if (record == null || record.getRecordId() == null)
            {
                return;
            }
            // 未接通话不创建工单
            if ("3".equals(record.getStatus()))
            {
                return;
            }
            // 已存在工单则跳过
            AiCallTicket existQuery = new AiCallTicket();
            existQuery.setRecordId(record.getRecordId());
            List<AiCallTicket> exist = callTicketMapper.selectAiCallTicketList(existQuery);
            if (exist != null && !exist.isEmpty())
            {
                log.info("[CallEvent] 入站话单 recordId={} 已存在工单，跳过自动创建", record.getRecordId());
                return;
            }

            AiCallTicket ticket = new AiCallTicket();
            ticket.setTicketNo(callTicketService.generateTicketNo());
            ticket.setRecordId(record.getRecordId());
            String caller = record.getCallerNumber();
            ticket.setTitle("来电咨询-" + (caller == null ? "未知号码" : caller));
            String content = record.getContent();
            if (StringUtils.isEmpty(content))
            {
                content = "来电号码:" + (caller == null ? "" : caller)
                        + (StringUtils.isNotEmpty(record.getCallerName()) ? " 来电人:" + record.getCallerName() : "")
                        + " 通话时长:" + (record.getDuration() == null ? 0 : record.getDuration()) + "秒";
            }
            ticket.setContent(content);
            ticket.setCallerNumber(caller);
            ticket.setCallerName(record.getCallerName());
            ticket.setPriority("2");
            ticket.setStatus("0");
            ticket.setCreateBy("call-event");
            callTicketService.insertAiCallTicket(ticket);
            log.info("[CallEvent] 入站通话自动创建工单 recordId={} ticketNo={}",
                    record.getRecordId(), ticket.getTicketNo());
        }
        catch (Exception e)
        {
            log.warn("[CallEvent] 入站通话自动创建工单失败 uuid={} err={}", uuid, e.getMessage());
        }
    }

    /**
     * 通过拨号日志找到外呼对应的坐席，把事件推送给其 WebSocket（原 ESL 侧富版本语义）。
     * 查找顺序：拨号日志 call_uuid → payload 透传的 variable_ai_record_id → recordId 反查坐席；
     * 能找到坐席 userId 则定向推送，否则广播给所有在线坐席。
     */
    private void pushEventToAgent(CallEvent event, String eventType, String extra)
    {
        String uuid = event.getSessionId();
        if (StringUtils.isEmpty(uuid))
        {
            return;
        }
        try
        {
            AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(uuid);
            Long agentId = null;
            Long recordId = null;
            String caller = null;
            String callee = null;
            if (dialLog != null)
            {
                agentId = dialLog.getAgentId();
                recordId = dialLog.getRecordId();
                caller = dialLog.getCallerNumber();
                callee = dialLog.getCalleeNumber();
            }

            // 若外呼事件携带了业务变量 ai_record_id（originate 时透传），也可关联话单
            if (recordId == null)
            {
                String varRecordId = event.payloadString("varRecordId");
                if (varRecordId != null && varRecordId.matches("\\d+"))
                {
                    recordId = Long.parseLong(varRecordId);
                }
            }

            if (agentId == null && recordId != null)
            {
                try
                {
                    AiCallRecord record = callRecordMapper.selectAiCallRecordByRecordId(recordId);
                    if (record != null && record.getAgentId() != null)
                    {
                        agentId = record.getAgentId();
                    }
                }
                catch (Exception ex)
                {
                    log.warn("[CallEvent] 反查 recordId={} 坐席失败: {}", recordId, ex.getMessage());
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("uuid", uuid);
            data.put("event", eventType);
            data.put("recordId", recordId);
            data.put("agentId", agentId);
            data.put("caller", caller);
            data.put("callee", callee);
            data.put("extra", extra);
            data.put("ts", System.currentTimeMillis());

            if (callEventPublisher != null)
            {
                if (agentId != null)
                {
                    // P10/D1：只读坐席信息走 Mapper，避免为一次查询依赖坐席状态服务（会重新引入环）
                    AiCallAgentStatus agent = agentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
                    if (agent != null && agent.getUserId() != null)
                    {
                        callEventPublisher.publishToUser(agent.getUserId(), eventType, data);
                        return;
                    }
                }
                callEventPublisher.broadcast(eventType, data);
            }
        }
        catch (Exception e)
        {
            log.error("[CallEvent] 推送事件失败: uuid={} type={}", uuid, eventType, e);
        }
    }

    /** 从 source（"ESL:host" / "AMI:host"）截取 host 部分；无前缀原样返回 */
    static String sourceHost(String source)
    {
        if (source == null)
        {
            return null;
        }
        int idx = source.indexOf(':');
        return idx >= 0 ? source.substring(idx + 1) : source;
    }
}
