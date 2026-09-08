package ai.lawyers.system.service.lawyers.queue;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.trunk.AiCallDialLog;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallDialLogMapper;

/**
 * W1: call-event 队列生产者 + 消费者。
 *
 * <p>将通话过程中的高频写操作（话单状态更新、拨号日志状态更新）投递到
 * {@code call-event} Stream 异步落库，降低通话高峰时数据库写入压力。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>话单 INSERT 保持同步（需要 recordId 供后续 ACD/IVR 流程使用）；</li>
 *   <li>话单 UPDATE（坐席归属、状态、IVR 结果等）异步化；</li>
 *   <li>拨号日志 RINGING/ANSWERED 状态更新异步化；</li>
 *   <li>HANGUP 终态保持同步（finishCall 依赖拨号日志数据，且终态一致性优先）；</li>
 *   <li>Stream/Redis 不可用时降级同步直写，保证数据不丢。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
@Component
public class CallEventDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(CallEventDispatcher.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 事件类型：更新话单 */
    public static final String UPDATE_CALL_RECORD = "UPDATE_CALL_RECORD";

    /** 事件类型：更新拨号日志 */
    public static final String UPDATE_DIAL_LOG = "UPDATE_DIAL_LOG";

    @Autowired
    private StreamQueueService streamQueueService;

    @Autowired
    private AiCallRecordMapper callRecordMapper;

    @Autowired
    private AiCallDialLogMapper dialLogMapper;

    @PostConstruct
    public void init()
    {
        streamQueueService.registerHandler(QueueNames.CALL_EVENT, payload ->
        {
            CallEventPayload event = MAPPER.readValue(payload, CallEventPayload.class);
            processEvent(event);
        });
        log.info("通话事件异步消费者已注册 queue={}", QueueNames.CALL_EVENT);
    }

    /**
     * 异步更新话单（坐席归属、状态、分类、IVR 结果等非终态字段）。
     * Stream 不可用时同步降级。
     */
    public void updateCallRecordAsync(AiCallRecord record)
    {
        if (record == null || record.getRecordId() == null)
        {
            return;
        }
        CallEventPayload event = new CallEventPayload();
        event.setEventType(UPDATE_CALL_RECORD);
        event.setCallRecord(record);
        try
        {
            if (streamQueueService.enqueue(QueueNames.CALL_EVENT, MAPPER.writeValueAsString(event)))
            {
                return;
            }
        }
        catch (Exception e)
        {
            log.warn("话单更新投递队列失败，降级同步 recordId={}: {}",
                    record.getRecordId(), e.getMessage());
        }
        callRecordMapper.updateAiCallRecord(record);
    }

    /**
     * 异步更新拨号日志（RINGING/ANSWERED 等中间态）。
     * HANGUP 终态不走此方法，保持同步。
     */
    public void updateDialLogAsync(AiCallDialLog dialLog)
    {
        if (dialLog == null || dialLog.getLogId() == null)
        {
            return;
        }
        CallEventPayload event = new CallEventPayload();
        event.setEventType(UPDATE_DIAL_LOG);
        event.setDialLog(dialLog);
        try
        {
            if (streamQueueService.enqueue(QueueNames.CALL_EVENT, MAPPER.writeValueAsString(event)))
            {
                return;
            }
        }
        catch (Exception e)
        {
            log.warn("拨号日志更新投递队列失败，降级同步 logId={}: {}",
                    dialLog.getLogId(), e.getMessage());
        }
        dialLogMapper.updateAiCallDialLog(dialLog);
    }

    /** 消费端：按事件类型分发到对应 Mapper */
    private void processEvent(CallEventPayload event)
    {
        if (event == null || event.getEventType() == null)
        {
            return;
        }
        switch (event.getEventType())
        {
            case UPDATE_CALL_RECORD:
                if (event.getCallRecord() != null)
                {
                    callRecordMapper.updateAiCallRecord(event.getCallRecord());
                }
                break;
            case UPDATE_DIAL_LOG:
                if (event.getDialLog() != null)
                {
                    dialLogMapper.updateAiCallDialLog(event.getDialLog());
                }
                break;
            default:
                log.warn("未知通话事件类型: {}", event.getEventType());
        }
    }
}
