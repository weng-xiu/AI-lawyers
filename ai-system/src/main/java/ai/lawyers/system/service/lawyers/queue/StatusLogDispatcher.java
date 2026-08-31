package ai.lawyers.system.service.lawyers.queue;

import java.util.Date;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.lawyers.system.domain.lawyers.quality.AiAgentStatusLog;
import ai.lawyers.system.mapper.lawyers.quality.AiAgentStatusLogMapper;

/**
 * T4-3 坐席状态流水：status-log 队列生产者 + 消费者。
 *
 * <p>生产端在坐席状态切换收口处（AiCallAgentStatusServiceImpl / ACD 抢占）调
 * {@link #log(Long, Long, String, String, String, String, String, Long, Date)} 投递；
 * Stream 不可用时同步降级直接落库。消费端落库前回填该坐席上一条流水的 duration
 * （以 log_time 差值计），幂等：已回填（duration&gt;0）不重复写。</p>
 *
 * @author ai-lawyers
 */
@Component
public class StatusLogDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(StatusLogDispatcher.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private StreamQueueService streamQueueService;

    @Autowired
    private AiAgentStatusLogMapper statusLogMapper;

    @PostConstruct
    public void init()
    {
        streamQueueService.registerHandler(QueueNames.STATUS_LOG, payload ->
        {
            AiAgentStatusLog entry = MAPPER.readValue(payload, AiAgentStatusLog.class);
            persist(entry);
        });
        log.info("坐席状态流水消费者已注册 queue={}", QueueNames.STATUS_LOG);
    }

    /**
     * 投递一条状态变更流水（状态无变化时不记录，避免噪声）。
     *
     * @param agentId       坐席ID
     * @param userId        绑定用户ID
     * @param eventType     事件类型
     * @param fromStatus    原在线状态
     * @param toStatus      新在线状态
     * @param fromCallStatus 原通话状态
     * @param toCallStatus  新通话状态
     * @param recordId      关联通话记录ID（可空）
     * @param logTime       切换时刻（可空取当前）
     */
    public void log(Long agentId, Long userId, String eventType,
                    String fromStatus, String toStatus,
                    String fromCallStatus, String toCallStatus,
                    Long recordId, Date logTime)
    {
        if (agentId == null)
        {
            return;
        }
        // 状态无变化不产生流水
        boolean same = eq(fromStatus, toStatus) && eq(fromCallStatus, toCallStatus);
        if (same)
        {
            return;
        }
        AiAgentStatusLog entry = new AiAgentStatusLog();
        entry.setAgentId(agentId);
        entry.setUserId(userId);
        entry.setEventType(eventType);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setFromCallStatus(fromCallStatus);
        entry.setToCallStatus(toCallStatus);
        entry.setRecordId(recordId);
        entry.setLogTime(logTime != null ? logTime : new Date());
        try
        {
            if (streamQueueService.enqueue(QueueNames.STATUS_LOG, MAPPER.writeValueAsString(entry)))
            {
                return;
            }
        }
        catch (Exception e)
        {
            log.warn("状态流水投递队列失败，降级同步落库 agentId={} event={}: {}",
                    agentId, eventType, e.getMessage());
        }
        persist(entry);
    }

    /** 落库 + 回填上一条流水 duration（消费端与同步降级共用） */
    private void persist(AiAgentStatusLog entry)
    {
        try
        {
            AiAgentStatusLog prev = statusLogMapper.selectLatestLog(entry.getAgentId());
            if (prev != null && prev.getLogTime() != null && entry.getLogTime() != null
                    && (prev.getDuration() == null || prev.getDuration() == 0))
            {
                long secs = (entry.getLogTime().getTime() - prev.getLogTime().getTime()) / 1000L;
                if (secs > 0)
                {
                    statusLogMapper.updateDuration(prev.getLogId(), (int) Math.min(secs, Integer.MAX_VALUE));
                }
            }
            statusLogMapper.insertAiAgentStatusLog(entry);
        }
        catch (Exception e)
        {
            log.warn("状态流水落库失败 agentId={} event={}: {}",
                    entry.getAgentId(), entry.getEventType(), e.getMessage());
        }
    }

    private static boolean eq(String a, String b)
    {
        return a == null ? b == null : a.equals(b);
    }
}
