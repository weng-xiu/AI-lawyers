package ai.lawyers.system.service.impl.lawyers.trunk;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.IAiCallBlacklistService;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;
import ai.lawyers.system.service.lawyers.skill.IAiSkillGroupService;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.EslEventBridgeService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.InboundCallHandler;

/**
 * 入站呼叫处理器（默认实现）
 *
 * <p>串联 "SIP 网关 → IVR/排队 → 坐席" 的业务胶水层：</p>
 * <ol>
 *   <li>收到 ESL 入站 CHANNEL_CREATE 事件后，建立一条话单（status=0 接通中）；</li>
 *   <li>根据被叫号码（DNIS）匹配技能组，找不到则使用默认组；</li>
 *   <li>调用 {@link IAgentDispatchService} 选择空闲坐席：</li>
 *   <li>分配成功 → 经网关适配器把通话桥接到坐席分机，并通过 WebSocket
 *       向坐席工作台推送 {@code INBOUND_RING} 弹屏事件；</li>
 *   <li>无空闲坐席 → 进入排队，WebSocket 广播排队状态，等待
 *       {@code tryDispatchNextForAgent} 在坐席空闲时唤醒。</li>
 * </ol>
 *
 * @author ai-lawyers
 */
@Service
public class DefaultInboundCallHandler implements InboundCallHandler
{
    private static final Logger log = LoggerFactory.getLogger(DefaultInboundCallHandler.class);

    @Value("${call.inbound.defaultGroupId:1}")
    private Long defaultGroupId;

    @Value("${call.inbound.enabled:true}")
    private boolean inboundEnabled;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    @Autowired
    private IAgentDispatchService agentDispatchService;

    @Autowired
    private IAiSkillGroupService skillGroupService;

    @Autowired
    private IAiCallAgentStatusService agentStatusService;

    @Autowired
    private CallGatewayFactory gatewayFactory;

    @Autowired
    private AiCallTrunkMapper trunkMapper;

    @Autowired
    private IAiCallBlacklistService blacklistService;

    @Autowired(required = false)
    private EslEventBridgeService eslEventBridgeService;

    @Autowired(required = false)
    private CallEventPublisher callEventPublisher;

    @Override
    public void handleIncomingCall(Map<String, Object> ctx)
    {
        if (!inboundEnabled)
        {
            log.info("[Inbound] 入站处理已开关闭，忽略来电: {}", ctx);
            return;
        }

        String uuid = str(ctx.get("uuid"));
        String caller = str(ctx.get("caller"));
        String dnis = str(ctx.get("callee"));
        if (StringUtils.isEmpty(dnis)) dnis = str(ctx.get("dnis"));
        String host = str(ctx.get("host"));

        log.info("[Inbound] 收到来话 uuid={} caller={} dnis={} host={}", uuid, caller, dnis, host);

        // 黑名单拦截：命中生效中的黑名单号码，直接挂断且不落话单
        if (StringUtils.isNotEmpty(caller) && blacklistService.isBlacklisted(caller))
        {
            log.warn("[Inbound] 黑名单来电拦截: {}", caller);
            if (eslEventBridgeService != null)
            {
                try
                {
                    eslEventBridgeService.hangupCall(host, uuid);
                }
                catch (Exception ex)
                {
                    log.warn("[Inbound] 黑名单号码挂断指令下发失败 uuid={} caller={}: {}", uuid, caller, ex.getMessage());
                }
            }
            return;
        }

        try
        {
            // 1. 建立话单
            AiCallRecord record = new AiCallRecord();
            record.setCallerNumber(caller);
            record.setCallTime(new Date());
            record.setStatus("0"); // 接通中
            // 写入通道 UUID，供 RECORD_STOP / HANGUP 等 ESL 事件回写录音与时长
            record.setCallUuid(uuid);
            record.setRemark("ESL入站 uuid=" + uuid + " dnis=" + dnis);
            record.setCreateBy("esl-inbound");
            record.setCreateTime(new Date());
            callRecordMapper.insertAiCallRecord(record);
            Long recordId = record.getRecordId();

            // 2. 选择技能组（按 DNIS 匹配，未命中走默认组）
            Long groupId = resolveGroupId(dnis);

            // 3. 分配坐席
            DispatchContext dctx = DispatchContext.of(uuid, recordId, caller);
            DispatchResult result = agentDispatchService.dispatch(groupId, dctx);

            if (result.isSuccess())
            {
                bridgeToAssignedAgent(uuid, result, recordId, host);
            }
            else
            {
                log.info("[Inbound] 暂无空闲坐席，进入排队 groupId={} queueId={} position={}",
                        groupId, result.getQueueId(), result.getQueuePosition());
                broadcastEvent("QUEUED", buildData(uuid, recordId, null, caller, dnis,
                        result.getQueueId(), result.getQueuePosition(), result.getMessage()));
            }
        }
        catch (Exception e)
        {
            log.error("[Inbound] 处理入站呼叫失败: uuid={} caller={}", uuid, caller, e);
        }
    }

    /**
     * 分配到坐席后，把通话桥接给坐席分机并推送弹屏。
     */
    private void bridgeToAssignedAgent(String uuid, DispatchResult result, Long recordId, String host)
    {
        Long agentId = result.getAgentId();

        // 更新话单归属坐席
        AiCallRecord update = new AiCallRecord();
        update.setRecordId(recordId);
        update.setAgentId(agentId);
        update.setStatus("0");
        callRecordMapper.updateAiCallRecord(update);

        // 加载坐席记录（用于拿 userId 和绑定的 SIP 分机号）
        AiCallAgentStatus agent = agentStatusService.selectAiCallAgentStatusByAgentId(agentId);
        Long userId = agent != null ? agent.getUserId() : null;
        String extension = resolveExtension(agentId, agent);

        // 推送来电弹屏给坐席（前端收到后振铃/弹屏）
        Map<String, Object> data = buildData(uuid, recordId, agentId,
                null, null, null, null, "来电已分配");
        data.put("agentName", result.getAgentName());
        data.put("extension", extension);
        if (callEventPublisher != null)
        {
            if (userId != null)
            {
                callEventPublisher.publishToUser(userId, "INBOUND_RING", data);
            }
            else
            {
                callEventPublisher.broadcast("INBOUND_RING", data);
            }
        }

        // 若坐席配置了分机且有可用网关，把媒体腿桥接到分机
        if (StringUtils.isNotEmpty(extension))
        {
            try
            {
                AiCallTrunk trunk = findLocalTrunk(host);
                if (trunk != null)
                {
                    ICallGatewayAdapter adapter = gatewayFactory.get(trunk);
                    boolean ok = adapter.bridgeToAgent(trunk, uuid, extension);
                    log.info("[Inbound] 桥接坐席 ext={} uuid={} result={}", extension, uuid, ok);
                }
            }
            catch (Exception e)
            {
                log.warn("[Inbound] 桥接坐席分机失败（坐席可能使用软电话自行应答）: {}", e.getMessage());
            }
        }
    }

    /**
     * 根据被叫号码（DNIS）匹配技能组。可后续扩展为号码路由表，
     * 当前实现：查全部启用组，groupCode 与 dnis 相等则命中，否则默认组。
     */
    private Long resolveGroupId(String dnis)
    {
        if (StringUtils.isNotEmpty(dnis))
        {
            List<AiSkillGroup> groups = skillGroupService.selectEnabledGroups();
            if (groups != null)
            {
                for (AiSkillGroup g : groups)
                {
                    if (dnis.equals(g.getGroupCode()))
                    {
                        return g.getGroupId();
                    }
                }
            }
        }
        return defaultGroupId;
    }

    /**
     * 解析坐席分机号：优先取坐席记录中绑定的 sipExtension，未配置时回退到
     * 默认约定 分机=1000+agentId。
     */
    private String resolveExtension(Long agentId, AiCallAgentStatus agent)
    {
        if (agent != null && StringUtils.isNotEmpty(agent.getSipExtension()))
        {
            return agent.getSipExtension();
        }
        return agentId == null ? null : String.valueOf(1000 + agentId);
    }

    private AiCallTrunk findLocalTrunk(String host)
    {
        try
        {
            AiCallTrunk query = new AiCallTrunk();
            query.setVendor("FREESWITCH");
            List<AiCallTrunk> list = trunkMapper.selectAiCallTrunkList(query);
            if (StringUtils.isNotEmpty(host))
            {
                for (AiCallTrunk t : list)
                {
                    if (host.equals(t.getGatewayHost())) return t;
                }
            }
            return list != null && !list.isEmpty() ? list.get(0) : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private void broadcastEvent(String type, Map<String, Object> data)
    {
        if (callEventPublisher != null)
        {
            callEventPublisher.broadcast(type, data);
        }
    }

    private Map<String, Object> buildData(String uuid, Long recordId, Long agentId,
            String caller, String dnis, Long queueId, Integer pos, String msg)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("uuid", uuid);
        m.put("recordId", recordId);
        m.put("agentId", agentId);
        m.put("caller", caller);
        m.put("dnis", dnis);
        m.put("queueId", queueId);
        m.put("queuePosition", pos);
        m.put("message", msg);
        m.put("ts", System.currentTimeMillis());
        return m;
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
}
