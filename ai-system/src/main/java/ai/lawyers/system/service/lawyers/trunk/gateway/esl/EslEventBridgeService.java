package ai.lawyers.system.service.lawyers.trunk.gateway.esl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;

/**
 * FreeSWITCH ESL 事件桥接服务
 *
 * <p>职责：</p>
 * <ol>
 *   <li>启动时连接所有 {@code vendor=FREESWITCH} 的中继线路，建立入站长连接；</li>
 *   <li>接收 CHANNEL_CREATE / CHANNEL_ANSWER / CHANNEL_HANGUP_COMPLETE /
 *       DTMF 等事件，转换为内部业务事件；</li>
 *   <li>外呼：通过拨号日志关联 recordId/agentId，更新话单状态并把事件
 *       经 {@link CallWebSocketServer} 推送给坐席工作台；</li>
 *   <li>入站：识别到来自网关的来话（Call-Direction=inbound）时，触发
 *       入站处理（来电弹屏 → 排队 → 分配坐席）。</li>
 * </ol>
 *
 * <p>本类是 README 架构中 "SIP 网关 → IVR/排队机 → 坐席工作台" 事件链路的核心胶水层。</p>
 *
 * @author ai-lawyers
 */
@Service
public class EslEventBridgeService implements EslEventListener
{
    private static final Logger log = LoggerFactory.getLogger(EslEventBridgeService.class);

    @Value("${call.gateway.esl.enabled:false}")
    private boolean eslEnabled;

    @Value("${call.gateway.freeswitch.eslPort:8021}")
    private int defaultEslPort;

    @Value("${call.gateway.freeswitch.eslPassword:ClueCon}")
    private String defaultEslPassword;

    @Value("${call.gateway.freeswitch.context:default}")
    private String defaultContext;

    @Autowired
    private AiCallTrunkMapper trunkMapper;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired
    private IAiCallAgentStatusService agentStatusService;

    @Autowired(required = false)
    private InboundCallHandler inboundCallHandler;

    @Autowired(required = false)
    private CallEventPublisher callEventPublisher;

    @Autowired
    private IAiCallTicketService callTicketService;

    @Autowired
    private AiCallTicketMapper callTicketMapper;

    /** host:port -> client */
    private final Map<String, FreeSwitchEslInboundClient> clients = new ConcurrentHashMap<>();

    @PostConstruct
    public void init()
    {
        if (!eslEnabled)
        {
            log.info("[ESL-Bridge] call.gateway.esl.enabled=false，跳过 FreeSWITCH 事件监听启动");
            return;
        }
        // 启动连接由调度线程异步执行，避免阻塞应用启动
        Thread starter = new Thread(this::connectAll, "esl-bridge-starter");
        starter.setDaemon(true);
        starter.start();
    }

    @PreDestroy
    public void destroy()
    {
        for (FreeSwitchEslInboundClient c : clients.values())
        {
            try { c.stop(); } catch (Exception ignored) {}
        }
        clients.clear();
    }

    /**
     * 连接/重连所有 FreeSWITCH 类型的中继。供管理界面在新增/修改线路后手动刷新调用。
     */
    public synchronized void connectAll()
    {
        AiCallTrunk query = new AiCallTrunk();
        query.setVendor("FREESWITCH");
        List<AiCallTrunk> trunks = trunkMapper.selectAiCallTrunkList(query);
        if (trunks == null || trunks.isEmpty())
        {
            log.info("[ESL-Bridge] 未发现 FREESWITCH 类型的中继线路");
            return;
        }
        for (AiCallTrunk t : trunks)
        {
            if (StringUtils.isEmpty(t.getGatewayHost())) continue;
            String key = t.getGatewayHost() + ":" + defaultEslPort;
            if (clients.containsKey(key)) continue; // 已连接
            try
            {
                FreeSwitchEslInboundClient client = new FreeSwitchEslInboundClient(
                        t.getGatewayHost(), defaultEslPort, defaultEslPassword);
                client.addListener(this);
                client.start();
                clients.put(key, client);
                log.info("[ESL-Bridge] 已连接 FreeSWITCH: {} (trunk={})", key, t.getTrunkCode());
            }
            catch (Exception e)
            {
                log.error("[ESL-Bridge] 连接 FreeSWITCH 失败: {}", key, e);
            }
        }
    }

    /**
     * 通过 ESL 在指定 FreeSWITCH 节点上挂断指定通道。
     *
     * @param host 网关主机；为空时使用任一已连接节点
     * @param uuid 通道 UUID
     * @return true 表示命令已下发（不代表对端已挂断）
     */
    public boolean hangupCall(String host, String uuid)
    {
        if (StringUtils.isEmpty(uuid))
        {
            return false;
        }
        FreeSwitchEslInboundClient client = pickClient(host);
        if (client == null)
        {
            log.warn("[ESL-Bridge] 无可用 ESL 连接，挂断失败 uuid={} host={}", uuid, host);
            return false;
        }
        String cmd = "api uuid_kill " + uuid;
        String resp = client.sendCommand(cmd);
        log.info("[ESL-Bridge] 挂断命令已下发 host={} uuid={} resp={}", client.getHost(), uuid, resp);
        return true;
    }

    private FreeSwitchEslInboundClient pickClient(String host)
    {
        if (clients.isEmpty())
        {
            return null;
        }
        if (StringUtils.isNotEmpty(host))
        {
            // 优先精确匹配 host:port
            FreeSwitchEslInboundClient c = clients.get(host + ":" + defaultEslPort);
            if (c != null) return c;
            // 退化到 host 前缀匹配
            for (Map.Entry<String, FreeSwitchEslInboundClient> e : clients.entrySet())
            {
                if (e.getKey().startsWith(host + ":")) return e.getValue();
            }
        }
        return clients.values().iterator().next();
    }

    // ---------------------------------------------------------------- 事件处理

    @Override
    public void onEvent(String host, EslEvent event)
    {
        String name = event.getEventName();
        String uuid = event.getCallUuid();
        if (name == null) return;

        try
        {
            switch (name)
            {
                case "CHANNEL_CREATE":
                    onChannelCreate(host, event, uuid);
                    break;
                case "CHANNEL_ANSWER":
                    onChannelAnswer(uuid, event);
                    break;
                case "CHANNEL_BRIDGE":
                    onChannelBridge(uuid, event);
                    break;
                case "CHANNEL_HANGUP_COMPLETE":
                case "CHANNEL_HANGUP":
                    onChannelHangup(uuid, event);
                    break;
                case "DTMF":
                    onDtmf(uuid, event);
                    break;
                case "RECORD_STOP":
                    onRecordStop(uuid, event);
                    break;
                default:
                    break;
            }
        }
        catch (Exception e)
        {
            log.error("[ESL-Bridge] 处理事件异常: host={} event={} uuid={}", host, name, uuid, e);
        }
    }

    private void onChannelCreate(String host, EslEvent event, String uuid)
    {
        String direction = event.get("Call-Direction");
        String caller = event.get("Caller-Caller-ID-Number");
        String callee = event.get("Caller-Destination-Number");

        if ("inbound".equalsIgnoreCase(direction))
        {
            log.info("[ESL] 入站来话: uuid={} {} -> {}", uuid, caller, callee);
            if (inboundCallHandler != null)
            {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("host", host);
                ctx.put("uuid", uuid);
                ctx.put("caller", caller);
                ctx.put("callee", callee);
                ctx.put("dnis", callee);
                inboundCallHandler.handleIncomingCall(ctx);
            }
        }
        else
        {
            log.debug("[ESL] 出站通道创建: uuid={} {} -> {}", uuid, caller, callee);
        }
    }

    private void onChannelAnswer(String uuid, EslEvent event)
    {
        log.info("[ESL] 通道接通: uuid={}", uuid);
        // 外呼场景：通过拨号日志找到关联坐席并推送
        pushEventToAgent(uuid, "ANSWERED", event, null);
        // 通知调度服务更新状态
        Map<String, Object> params = new HashMap<>();
        params.put("answerTime", new Date());
        callDispatchService.onCallEvent(uuid, "ANSWERED", params);
    }

    private void onChannelBridge(String uuid, EslEvent event)
    {
        log.info("[ESL] 通道桥接: uuid={}", uuid);
        pushEventToAgent(uuid, "BRIDGED", event, null);
    }

    private void onChannelHangup(String uuid, EslEvent event)
    {
        String cause = event.get("Hangup-Cause");
        String billSec = event.get("variable_billsec");
        String duration = event.get("variable_duration");
        String direction = event.get("Call-Direction");
        log.info("[ESL] 通道挂断: uuid={} cause={} billsec={} direction={}", uuid, cause, billSec, direction);

        int talkDuration = 0;
        if (billSec != null)
        {
            try { talkDuration = Integer.parseInt(billSec); } catch (Exception ignored) {}
        }
        else if (duration != null)
        {
            try { talkDuration = Integer.parseInt(duration); } catch (Exception ignored) {}
        }

        Map<String, Object> params = new HashMap<>();
        params.put("hangupCause", cause);
        params.put("talkDuration", talkDuration);
        callDispatchService.onCallEvent(uuid, "HANGUP", params);

        // 推送挂断事件给坐席
        pushEventToAgent(uuid, "HANGUP", event, cause);

        // 入站通话正常结束且有通话时长时，自动创建工单（外呼已有 maybeCreateTicket 逻辑，不重复）
        if ("inbound".equalsIgnoreCase(direction)
                && "NORMAL_CLEARING".equalsIgnoreCase(cause)
                && talkDuration > 0)
        {
            tryAutoCreateInboundTicket(uuid);
        }
    }

    /**
     * 入站通话挂断后自动创建工单。
     * <p>条件：能通过 uuid 找到 ai_call_record；该 recordId 尚未生成工单；
     * 通话状态为已完成（非未接）。已存在工单则跳过，避免重复创建。</p>
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
                log.info("[ESL-Bridge] 入站话单 recordId={} 已存在工单，跳过自动创建", record.getRecordId());
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
            ticket.setCreateBy("esl-bridge");
            callTicketService.insertAiCallTicket(ticket);
            log.info("[ESL-Bridge] 入站通话自动创建工单 recordId={} ticketNo={}",
                    record.getRecordId(), ticket.getTicketNo());
        }
        catch (Exception e)
        {
            log.warn("[ESL-Bridge] 入站通话自动创建工单失败 uuid={} err={}", uuid, e.getMessage());
        }
    }

    private void onDtmf(String uuid, EslEvent event)
    {
        String digit = event.get("DTMF-Digit");
        if (digit == null) digit = event.get("DTMF-Source");
        log.debug("[ESL] DTMF: uuid={} digit={}", uuid, digit);
        pushEventToAgent(uuid, "DTMF", event, digit);
    }

    /**
     * 处理 FreeSWITCH RECORD_STOP 事件：把录音文件路径与时长回写到话单。
     *
     * <p>事件字段兼容：</p>
     * <ul>
     *   <li>录音文件路径：{@code Record-File-Path}（标准字段），回退到
     *       {@code variable_record_path} / {@code record_path}；</li>
     *   <li>录音时长（秒）：{@code variable_record_seconds}，回退到
     *       {@code variable_recording_duration} / {@code record_seconds}；</li>
     *   <li>关联键：优先按事件 uuid（一般是 A-leg Unique-ID）查
     *       {@code call_uuid}，未命中时再尝试通过拨号日志的 recordId 关联。</li>
     * </ul>
     */
    private void onRecordStop(String uuid, EslEvent event)
    {
        String recordPath = event.get("Record-File-Path");
        if (StringUtils.isEmpty(recordPath))
        {
            recordPath = event.get("variable_record_path");
        }
        if (StringUtils.isEmpty(recordPath))
        {
            recordPath = event.get("record_path");
        }
        if (StringUtils.isEmpty(recordPath))
        {
            recordPath = event.get("Record-File");
        }

        Integer recordSeconds = parseInteger(
                firstNonEmpty(event, "variable_record_seconds",
                        "variable_recording_duration", "record_seconds", "Record-Duration"));

        // RECORD_STOP 事件的 uuid 可能是录音腿 uuid，与话单建立时写入的 A-leg uuid 不一定相同，
        // 这里同时尝试 Other-Leg-Unique-ID / Channel-Call-UUID 作为候选
        String otherLeg = event.get("Other-Leg-Unique-ID");
        String callUuidHeader = event.get("Channel-Call-UUID");

        log.info("[ESL] 录音停止: uuid={} file={} seconds={} otherLeg={} callUuid={}",
                uuid, recordPath, recordSeconds, otherLeg, callUuidHeader);

        Long recordId = resolveRecordIdForCall(uuid, otherLeg, callUuidHeader, event);
        if (recordId == null)
        {
            log.warn("[ESL-Bridge] RECORD_STOP 未找到关联话单: uuid={} file={}", uuid, recordPath);
            return;
        }

        try
        {
            AiCallRecord update = new AiCallRecord();
            update.setRecordId(recordId);
            if (StringUtils.isNotEmpty(recordPath))
            {
                update.setRecordFile(recordPath);
                // 对外访问 URL 统一走后端播放接口，前端可直接用 <audio src=...>
                update.setRecordingUrl("/lawyers/call/record/" + recordId + "/play");
            }
            if (recordSeconds != null)
            {
                update.setRecordDuration(recordSeconds);
            }
            callRecordMapper.updateRecordingInfo(update);
            log.info("[ESL-Bridge] 已回写录音信息: recordId={} file={} duration={}s",
                    recordId, recordPath, recordSeconds);
        }
        catch (Exception e)
        {
            log.error("[ESL-Bridge] 回写录音信息失败: recordId={} file={}", recordId, recordPath, e);
        }
    }

    /**
     * 在 RECORD_STOP 等事件中按多个候选 UUID 找到对应的话单主键。
     * 查找顺序：
     * <ol>
     *   <li>事件 uuid / Other-Leg-Unique-ID / Channel-Call-UUID 直接查 ai_call_record.call_uuid；</li>
     *   <li>通过 ai_call_dial_log.call_uuid 反查 recordId（外呼场景）；</li>
     *   <li>事件携带的 variable_ai_record_id 透传变量。</li>
     * </ol>
     */
    private Long resolveRecordIdForCall(String uuid, String otherLeg, String callUuidHeader, EslEvent event)
    {
        Long recordId = tryFindRecordIdByCallUuid(uuid);
        if (recordId == null && StringUtils.isNotEmpty(otherLeg))
        {
            recordId = tryFindRecordIdByCallUuid(otherLeg);
        }
        if (recordId == null && StringUtils.isNotEmpty(callUuidHeader))
        {
            recordId = tryFindRecordIdByCallUuid(callUuidHeader);
        }
        if (recordId == null && StringUtils.isNotEmpty(uuid))
        {
            try
            {
                AiCallDialLog dialLog = dialLogMapper.selectByCallUuid(uuid);
                if (dialLog != null && dialLog.getRecordId() != null)
                {
                    recordId = dialLog.getRecordId();
                }
            }
            catch (Exception ex)
            {
                log.debug("[ESL-Bridge] 按拨号日志反查 recordId 失败: uuid={} err={}", uuid, ex.getMessage());
            }
        }
        if (recordId == null)
        {
            String varRecordId = event.get("variable_ai_record_id");
            if (varRecordId != null && varRecordId.matches("\\d+"))
            {
                recordId = Long.parseLong(varRecordId);
            }
        }
        return recordId;
    }

    private Long tryFindRecordIdByCallUuid(String callUuid)
    {
        if (StringUtils.isEmpty(callUuid)) return null;
        try
        {
            AiCallRecord record = callRecordMapper.selectAiCallRecordByCallUuid(callUuid);
            return record == null ? null : record.getRecordId();
        }
        catch (Exception e)
        {
            log.debug("[ESL-Bridge] 按 callUuid={} 查话单失败: {}", callUuid, e.getMessage());
            return null;
        }
    }

    private String firstNonEmpty(EslEvent event, String... keys)
    {
        if (keys == null) return null;
        for (String k : keys)
        {
            String v = event.get(k);
            if (StringUtils.isNotEmpty(v)) return v;
        }
        return null;
    }

    private Integer parseInteger(String s)
    {
        if (StringUtils.isEmpty(s)) return null;
        try
        {
            // FreeSWITCH 有时返回 "12.34" 秒，截断小数
            int dot = s.indexOf('.');
            if (dot > 0) s = s.substring(0, dot);
            return Integer.parseInt(s.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * 通过拨号日志找到外呼对应的坐席，把事件推送给其 WebSocket。
     */
    private void pushEventToAgent(String uuid, String eventType, EslEvent event, String extra)
    {
        if (uuid == null) return;
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
                String varRecordId = event.get("variable_ai_record_id");
                if (varRecordId != null && varRecordId.matches("\\d+"))
                {
                    recordId = Long.parseLong(varRecordId);
                }
            }

            // 入站 PSTN 通话只写了 ai_call_record（含 agentId），没有 dial_log，
            // 这里通过 recordId 反查坐席，避免挂断时只能广播
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
                    log.warn("[ESL-Bridge] 反查 recordId={} 坐席失败: {}", recordId, ex.getMessage());
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

            // 如果能找到坐席的 userId，定向推送；否则广播给所有在线坐席
            if (callEventPublisher != null)
            {
                if (agentId != null)
                {
                    AiCallAgentStatus agent = agentStatusService.selectAiCallAgentStatusByAgentId(agentId);
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
            log.error("[ESL-Bridge] 推送事件失败: uuid={} type={}", uuid, eventType, e);
        }
    }
}
