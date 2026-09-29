package ai.lawyers.system.service.lawyers.trunk.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.system.service.lawyers.queue.CallEventDispatcher;
import ai.lawyers.system.service.lawyers.queue.CallEventPayload;
import ai.lawyers.system.service.lawyers.queue.QueueNames;
import ai.lawyers.system.service.lawyers.queue.StreamQueueService;

/**
 * P3-B2：统一呼叫事件总线。
 *
 * <p>两侧桥（ESL/AMI）生产 {@link CallEvent} 后经本总线投递：</p>
 * <ul>
 *   <li>开关 {@code call.event.stream-enabled=true}（默认）且 Stream 可用：
 *       序列化进 {@code stream:call-event}，由 {@code CallEventDispatcher} 消费组路由回
 *       {@link #onStreamEvent(CallEvent)} → {@link CallEventProcessor}，
 *       桥接线程零业务阻塞，多实例竞争消费；</li>
 *   <li>开关关闭或入队失败（Redis 异常/队列禁用）：同步直调
 *       {@link CallEventProcessor#handle(CallEvent)} 降级，保证事件不丢。</li>
 * </ul>
 *
 * <p>投递语义 at-least-once：Stream 消费失败重试/降级都可能让同一事件到达多次，
 * 幂等由生产侧第一道判重（ai_call_event_dedup）+ 状态机终态/CAS/工单存在性检查兜底。</p>
 *
 * @author ai-lawyers
 */
@Component
public class CallEventBus
{
    private static final Logger log = LoggerFactory.getLogger(CallEventBus.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * B2：PBX 事件入 Stream 总开关。true（默认）两侧桥事件统一进 {@code stream:call-event}
     * 异步消费；false 回退 V2.50 及以前的同步直调语义（应急回退用）。
     */
    @Value("${call.event.stream-enabled:true}")
    private boolean streamEnabled;

    @Autowired
    private StreamQueueService streamQueueService;

    @Autowired
    private CallEventProcessor processor;

    /**
     * 生产侧入口：投递统一事件。入队成功即返回；开关关闭/入队失败时同步降级处理。
     * 任何情况下不向调用方抛异常（PBX 事件线程不允许被打断）。
     */
    public void dispatch(CallEvent event)
    {
        if (event == null)
        {
            return;
        }
        if (streamEnabled)
        {
            try
            {
                if (streamQueueService.enqueue(QueueNames.CALL_EVENT, MAPPER.writeValueAsString(wrap(event))))
                {
                    return;
                }
            }
            catch (Exception e)
            {
                log.warn("[CallEventBus] 事件入队失败，降级同步处理: name={} session={} err={}",
                        event.getEventName(), event.getSessionId(), e.getMessage());
            }
        }
        processor.handle(event);
    }

    /**
     * 消费侧入口：Stream 消息反序列化后由 {@code CallEventDispatcher} 路由至此。
     */
    public void onStreamEvent(CallEvent event)
    {
        processor.handle(event);
    }

    /** 包装为 W1 既有消息壳（eventType=PBX_EVENT + callEvent 字段），与 DB 异步写共用队列 */
    private CallEventPayload wrap(CallEvent event)
    {
        CallEventPayload payload = new CallEventPayload();
        payload.setEventType(CallEventDispatcher.PBX_EVENT);
        payload.setCallEvent(event);
        return payload;
    }
}
