package ai.lawyers.system.service.lawyers.trunk.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.InboundCallHandler;

/**
 * P10/D1：呼叫事件的进程内下游监听器。
 *
 * <p>把原 {@link CallEventProcessor} 直接调用调度服务/坐席状态服务/入站处理器的三条反向依赖，
 * 反转为「处理器发布事件 → 本监听器消费」的单向数据流：</p>
 * <ul>
 *   <li>{@link InboundCallEvent} → 入站弹屏/排队/IVR；</li>
 *   <li>{@link CallStateChangeEvent} → {@code ICallDispatchService#onCallEvent} 状态机；</li>
 *   <li>{@link AgentStatusSyncEvent} → 队列成员设备态同步坐席状态（仅变化时写库）。</li>
 * </ul>
 *
 * <p>Spring 事件默认同步派发，处理顺序与发送顺序一致，异常仍会回抛到处理器
 * {@code handle()} 的统一 try/catch，保持单事件异常不扩散的原语义。</p>
 *
 * @author ai-lawyers
 */
@Component
public class CallEventListeners
{
    private static final Logger log = LoggerFactory.getLogger(CallEventListeners.class);

    @Autowired
    private ICallDispatchService callDispatchService;

    @Autowired
    private IAiCallAgentStatusService agentStatusService;

    @Autowired(required = false)
    private InboundCallHandler inboundCallHandler;

    /** 入站来话：转交入站处理器（来电弹屏 → 排队 → 分配坐席） */
    @EventListener
    public void onInbound(InboundCallEvent event)
    {
        if (inboundCallHandler == null)
        {
            return;
        }
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("host", event.getHost());
        ctx.put("uuid", event.getSessionId());
        ctx.put("caller", event.getCaller());
        ctx.put("callee", event.getCallee());
        ctx.put("dnis", event.getCallee());
        inboundCallHandler.handleIncomingCall(ctx);
    }

    /** 状态机：振铃/接通/挂断统一由调度服务推进（终态幂等仍由状态机下游兜底） */
    @EventListener
    public void onCallStateChange(CallStateChangeEvent event)
    {
        callDispatchService.onCallEvent(event.getSessionId(), event.getState(), event.getParams());
    }

    /** 队列成员设备态 → 坐席状态同步（同状态跳过；异常只告警不扩散） */
    @EventListener
    public void onAgentStatusSync(AgentStatusSyncEvent event)
    {
        try
        {
            AiCallAgentStatus query = new AiCallAgentStatus();
            query.setSipExtension(event.getExtension());
            List<AiCallAgentStatus> agents = agentStatusService.selectAiCallAgentStatusList(query);
            if (agents == null)
            {
                return;
            }
            for (AiCallAgentStatus agent : agents)
            {
                if (agent == null || event.getTargetStatus().equals(agent.getStatus()))
                {
                    continue;
                }
                log.info("[CallEvent] 队列成员状态同步坐席: ext={} agentId={} {} -> {}",
                        event.getExtension(), agent.getAgentId(), agent.getStatus(), event.getTargetStatus());
                agentStatusService.updateAgentStatus(agent.getAgentId(), event.getTargetStatus());
            }
        }
        catch (Exception e)
        {
            log.warn("[CallEvent] 队列成员状态同步坐席失败 ext={} err={}",
                    event.getExtension(), e.getMessage());
        }
    }
}
