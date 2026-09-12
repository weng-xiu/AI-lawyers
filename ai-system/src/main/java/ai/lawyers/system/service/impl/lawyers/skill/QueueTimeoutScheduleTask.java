package ai.lawyers.system.service.impl.lawyers.skill;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;

/**
 * 排队超时扫描定时任务（L3）
 *
 * <p>每 30 秒扫描 ai_call_queue 中排队中（queue_status='0'）且入队时间超过技能组
 * max_wait（秒）的记录：幂等地置为超时溢出（queue_status='2'）并写出队时间/等待时长；
 * 若技能组配置了溢出技能组 overflow_group_id，则向溢出组重新分配一次（不再链式溢出、
 * 不再入队），无可用坐席则保持超时溢出终态。</p>
 *
 * <p>N7：多实例通过 {@link RedisLeaderLock} 单主锁保证全组仅一个实例扫描
 * （markTimeoutOverflow 本身幂等，锁为第一道防线）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class QueueTimeoutScheduleTask
{
    private static final Logger log = LoggerFactory.getLogger(QueueTimeoutScheduleTask.class);

    /** TTL 60s：大于单轮最坏耗时，崩溃后下一轮自动恢复 */
    private static final String LOCK_NAME = "job:queue-timeout";
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);

    @Autowired
    private AiCallQueueMapper queueMapper;

    @Autowired
    private AiSkillGroupMapper skillGroupMapper;

    @Autowired
    private IAgentDispatchService agentDispatchService;

    @Autowired
    private RedisLeaderLock leaderLock;

    /**
     * 排队超时扫描：每 30 秒一次（N7：集群内单实例执行）
     */
    @Scheduled(cron = "*/30 * * * * ?")
    public void scanTimeoutQueues()
    {
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doScanTimeoutQueues);
    }

    private void doScanTimeoutQueues()
    {
        List<AiCallQueue> timeouts;
        try
        {
            timeouts = queueMapper.selectTimeoutQueuing();
        }
        catch (Exception e)
        {
            log.error("排队超时扫描查询异常", e);
            return;
        }
        if (timeouts == null || timeouts.isEmpty())
        {
            return;
        }
        for (AiCallQueue queue : timeouts)
        {
            try
            {
                handleOne(queue);
            }
            catch (Exception e)
            {
                // 单条失败不影响其余记录
                log.error("排队超时处理异常：queueId={} sessionId={}", queue.getQueueId(), queue.getSessionId(), e);
            }
        }
    }

    private void handleOne(AiCallQueue queue)
    {
        // 幂等：条件更新仅对 queue_status='0' 生效，影响行数 0 说明已被其他流程处理
        if (queueMapper.markTimeoutOverflow(queue.getQueueId()) == 0)
        {
            return;
        }
        log.info("排队超时溢出：queueId={} sessionId={} 技能组[{}]",
                queue.getQueueId(), queue.getSessionId(), queue.getGroupId());

        AiSkillGroup group = skillGroupMapper.selectAiSkillGroupByGroupId(queue.getGroupId());
        Long overflowGroupId = group == null ? null : group.getOverflowGroupId();
        if (overflowGroupId == null || overflowGroupId.equals(queue.getGroupId()))
        {
            return;
        }

        // 向溢出技能组重新分配一次：不再链式溢出、无可用坐席也不再入队（保持超时溢出终态）
        DispatchContext ctx = DispatchContext.of(queue.getSessionId(), queue.getRecordId(), queue.getCallerNumber());
        ctx.setPriority(queue.getPriority());
        ctx.setAllowOverflow(false);
        ctx.setEnqueueIfNoAgent(false);
        DispatchResult result = agentDispatchService.dispatch(overflowGroupId, ctx);
        if (result.isSuccess())
        {
            log.info("超时排队转溢出技能组分配成功：queueId={} sessionId={} -> 坐席[{}] 技能组[{}]",
                    queue.getQueueId(), queue.getSessionId(), result.getAgentId(), overflowGroupId);
        }
        else
        {
            log.info("超时排队转溢出技能组[{}]无可用坐席，保持超时溢出终态：queueId={} sessionId={}",
                    overflowGroupId, queue.getQueueId(), queue.getSessionId());
        }
    }
}
