package ai.lawyers.system.service.impl.lawyers.skill;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.skill.AiCallQueue;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;
import ai.lawyers.system.domain.lawyers.skill.DispatchContext;
import ai.lawyers.system.domain.lawyers.skill.DispatchResult;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiCallQueueMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMapper;
import ai.lawyers.system.mapper.lawyers.skill.AiSkillGroupMemberMapper;
import ai.lawyers.system.service.lawyers.skill.IAiSkillGroupService;
import ai.lawyers.system.service.lawyers.skill.IAgentDispatchService;

/**
 * 坐席智能分配（ACD）实现
 *
 * <p>支持策略：round_robin 轮询 / least_recent 最久未接 / least_calls 最少通话 / all_ring 全员振铃（取首位）。
 * 无可用坐席时入队，支持溢出到 overflow_group_id。轮询指针存 Redis。</p>
 *
 * @author ai-lawyers
 */
@Service
public class AgentDispatchServiceImpl implements IAgentDispatchService
{
    private static final Logger log = LoggerFactory.getLogger(AgentDispatchServiceImpl.class);

    private static final String RR_KEY_PREFIX = "skill:rr:";

    @Autowired
    private AiSkillGroupMapper skillGroupMapper;

    @Autowired
    private AiSkillGroupMemberMapper memberMapper;

    @Autowired
    private AiCallQueueMapper queueMapper;

    @Autowired
    private AiCallAgentStatusMapper agentStatusMapper;

    @Autowired
    private IAiSkillGroupService skillGroupService;

    @Autowired
    private RedisCache redisCache;

    @Override
    @Transactional
    public DispatchResult dispatch(Long groupId, DispatchContext ctx)
    {
        return doDispatch(groupId, ctx, true);
    }

    private DispatchResult doDispatch(Long groupId, DispatchContext ctx, boolean allowOverflow)
    {
        AiSkillGroup group = skillGroupMapper.selectAiSkillGroupByGroupId(groupId);
        if (group == null || !"1".equals(group.getStatus()))
        {
            return DispatchResult.failed("技能组不存在或已停用");
        }

        // 1. 取可用坐席
        List<AiSkillGroupMember> available = memberMapper.selectAvailableByGroupId(groupId);
        if (available != null && !available.isEmpty())
        {
            AiSkillGroupMember chosen = chooseByStrategy(group, available);
            if (chosen != null)
            {
                return assignToAgent(group, chosen, ctx);
            }
        }

        // 2. 溢出：无可用坐席且允许溢出到 overflow_group_id
        if (allowOverflow && ctx.isAllowOverflow() && group.getOverflowGroupId() != null
                && !group.getOverflowGroupId().equals(groupId))
        {
            log.info("技能组[{}]无可用坐席，溢出到[{}]", groupId, group.getOverflowGroupId());
            DispatchResult overflow = doDispatch(group.getOverflowGroupId(), ctx, false);
            if (overflow.isSuccess())
            {
                return overflow;
            }
        }

        // 3. 入队或返回失败
        if (!ctx.isEnqueueIfNoAgent())
        {
            return DispatchResult.failed("技能组[" + group.getGroupName() + "]暂无空闲坐席");
        }
        return enqueue(group, ctx);
    }

    @Override
    public DispatchResult dispatchByCategory(Long categoryId, DispatchContext ctx)
    {
        AiSkillGroup group = skillGroupService.selectByCategoryId(categoryId);
        if (group == null)
        {
            return DispatchResult.failed("未找到咨询分类[" + categoryId + "]对应的技能组");
        }
        return doDispatch(group.getGroupId(), ctx, true);
    }

    /**
     * 按策略选择一个坐席
     */
    private AiSkillGroupMember chooseByStrategy(AiSkillGroup group, List<AiSkillGroupMember> available)
    {
        // available 已按 skill_level desc, priority desc 排序
        String strategy = group.getStrategy() == null ? "round_robin" : group.getStrategy();
        switch (strategy)
        {
            case "least_recent":
                // 最久未通话：call_start_time 最早（含从未通话者，为 null 排最前）
                return available.stream().min(Comparator.comparing(
                        m -> {
                            Date t = memberMapper.selectLastCallStartTime(m.getAgentId());
                            return t == null ? new Date(0) : t;
                        })).orElse(available.get(0));
            case "least_calls":
                // 今日完成通话最少
                return available.stream().min(Comparator.comparingInt(
                        m -> {
                            Integer c = memberMapper.countTodayCompletedByAgent(m.getAgentId());
                            return c == null ? 0 : c;
                        })).orElse(available.get(0));
            case "all_ring":
                // 全员振铃：真实 PBX 会并行呼叫所有成员；无 PBX 环境取等级最高者（列表首位）
                return available.get(0);
            case "round_robin":
            default:
                return roundRobin(group.getGroupId(), available);
        }
    }

    /**
     * 轮询：用 Redis 记录上次分配的成员ID，从其后开始取下一个可用者
     */
    private AiSkillGroupMember roundRobin(Long groupId, List<AiSkillGroupMember> available)
    {
        String key = RR_KEY_PREFIX + groupId;
        Long lastId = redisCache.getCacheObject(key);
        if (lastId == null)
        {
            AiSkillGroupMember first = available.get(0);
            redisCache.setCacheObject(key, first.getAgentId(), 24, TimeUnit.HOURS);
            return first;
        }
        int startIdx = 0;
        for (int i = 0; i < available.size(); i++)
        {
            if (lastId.equals(available.get(i).getAgentId()))
            {
                startIdx = (i + 1) % available.size();
                break;
            }
        }
        AiSkillGroupMember chosen = available.get(startIdx);
        redisCache.setCacheObject(key, chosen.getAgentId(), 24, TimeUnit.HOURS);
        return chosen;
    }

    /**
     * 执行分配：写流水、置坐席忙碌
     */
    private DispatchResult assignToAgent(AiSkillGroup group, AiSkillGroupMember member, DispatchContext ctx)
    {
        AiCallAgentStatus agent = agentStatusMapper.selectAiCallAgentStatusByAgentId(member.getAgentId());
        String agentName = agent != null ? agent.getAgentName() : ("坐席" + member.getAgentId());

        // 若该会话已在队列中，更新其出队信息；否则直接记录一条已分配流水
        AiCallQueue existQueue = StringUtils.isNotEmpty(ctx.getSessionId())
                ? queueMapper.selectQueuingBySessionId(ctx.getSessionId()) : null;
        Date now = new Date();
        if (existQueue != null)
        {
            existQueue.setAgentId(member.getAgentId());
            existQueue.setQueueStatus("1");
            existQueue.setStrategyUsed(group.getStrategy());
            existQueue.setDequeueTime(now);
            existQueue.setWaitDuration((int) ((now.getTime() - existQueue.getEnqueueTime().getTime()) / 1000));
            queueMapper.updateAiCallQueue(existQueue);
        }
        else
        {
            AiCallQueue q = new AiCallQueue();
            q.setSessionId(ctx.getSessionId());
            q.setRecordId(ctx.getRecordId());
            q.setCallerNumber(ctx.getCallerNumber());
            q.setGroupId(group.getGroupId());
            q.setAgentId(member.getAgentId());
            q.setEnqueueTime(now);
            q.setDequeueTime(now);
            q.setWaitDuration(0);
            q.setQueueStatus("1");
            q.setStrategyUsed(group.getStrategy());
            q.setPriority(ctx.getPriority() == null ? 0 : ctx.getPriority());
            queueMapper.insertAiCallQueue(q);
        }

        // 置坐席为忙碌（status 在线，call_status 通话中）。真实 PBX 桥接由网关层完成。
        if (agent != null)
        {
            agent.setCallStatus("1");
            agent.setCurrentCallPhone(ctx.getCallerNumber());
            agent.setCallStartTime(now);
            agentStatusMapper.updateAiCallAgentStatus(agent);
        }

        DispatchResult result = DispatchResult.assigned(member.getAgentId(), agentName,
                group.getGroupId(), group.getGroupName(), group.getStrategy());
        result.setQueueId(existQueue != null ? existQueue.getQueueId() : null);
        log.info("分配成功：会话[{}] 主叫[{}] -> 坐席[{}] 技能组[{}] 策略[{}]",
                ctx.getSessionId(), ctx.getCallerNumber(), agentName, group.getGroupName(), group.getStrategy());
        return result;
    }

    /**
     * 入队
     */
    private DispatchResult enqueue(AiSkillGroup group, DispatchContext ctx)
    {
        // 若同会话已在排队，直接返回排队位置，不重复入队
        AiCallQueue exist = StringUtils.isNotEmpty(ctx.getSessionId())
                ? queueMapper.selectQueuingBySessionId(ctx.getSessionId()) : null;
        Date now = new Date();
        AiCallQueue queue;
        if (exist != null)
        {
            queue = exist;
        }
        else
        {
            queue = new AiCallQueue();
            queue.setSessionId(ctx.getSessionId());
            queue.setRecordId(ctx.getRecordId());
            queue.setCallerNumber(ctx.getCallerNumber());
            queue.setGroupId(group.getGroupId());
            queue.setEnqueueTime(now);
            queue.setQueueStatus("0");
            queue.setPriority(ctx.getPriority() == null ? 0 : ctx.getPriority());
            queueMapper.insertAiCallQueue(queue);
        }
        int before = queueMapper.countBefore(group.getGroupId(), queue.getEnqueueTime(), queue.getPriority());
        int queueSize = queueMapper.countQueuingByGroupId(group.getGroupId());
        log.info("入队：会话[{}] 主叫[{}] 技能组[{}] 位置[{}]",
                ctx.getSessionId(), ctx.getCallerNumber(), group.getGroupName(), before + 1);
        return DispatchResult.queued(queue.getQueueId(), before + 1, queueSize,
                group.getGroupId(), group.getGroupName());
    }

    @Override
    @Transactional
    public DispatchResult assignManually(Long queueId, Long agentId)
    {
        AiCallQueue queue = queueMapper.selectAiCallQueueByQueueId(queueId);
        if (queue == null || !"0".equals(queue.getQueueStatus()))
        {
            return DispatchResult.failed("排队记录不存在或已处理");
        }
        AiSkillGroup group = skillGroupMapper.selectAiSkillGroupByGroupId(queue.getGroupId());
        AiCallAgentStatus agent = agentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"1".equals(agent.getStatus()) || !"0".equals(agent.getCallStatus()))
        {
            return DispatchResult.failed("目标坐席不存在或当前不空闲");
        }
        Date now = new Date();
        queue.setAgentId(agentId);
        queue.setQueueStatus("1");
        queue.setStrategyUsed("manual");
        queue.setDequeueTime(now);
        queue.setWaitDuration((int) ((now.getTime() - queue.getEnqueueTime().getTime()) / 1000));
        queueMapper.updateAiCallQueue(queue);

        agent.setCallStatus("1");
        agent.setCurrentCallPhone(queue.getCallerNumber());
        agent.setCallStartTime(now);
        agentStatusMapper.updateAiCallAgentStatus(agent);

        DispatchResult result = DispatchResult.assigned(agentId, agent.getAgentName(),
                group.getGroupId(), group.getGroupName(), "manual");
        result.setQueueId(queueId);
        return result;
    }

    @Override
    public List<AiCallQueue> listQueuing(Long groupId)
    {
        if (groupId != null)
        {
            return queueMapper.selectQueuingByGroupId(groupId);
        }
        AiCallQueue q = new AiCallQueue();
        q.setQueueStatus("0");
        return queueMapper.selectAiCallQueueList(q);
    }

    @Override
    public int kickQueue(Long queueId, String reason)
    {
        AiCallQueue queue = queueMapper.selectAiCallQueueByQueueId(queueId);
        if (queue == null)
        {
            return 0;
        }
        queue.setQueueStatus("3");
        queue.setDequeueTime(new Date());
        queue.setWaitDuration((int) ((System.currentTimeMillis() - queue.getEnqueueTime().getTime()) / 1000));
        log.info("踢除排队：queueId={} sessionId={} reason={}", queueId, queue.getSessionId(), reason);
        return queueMapper.updateAiCallQueue(queue);
    }

    @Override
    @Transactional
    public DispatchResult tryDispatchNextForAgent(Long agentId)
    {
        // 坐席通话结束，找出该坐席所属技能组中最早等待的排队者，分配给它
        AiSkillGroupMember query = new AiSkillGroupMember();
        query.setAgentId(agentId);
        query.setStatus("1");
        List<AiSkillGroupMember> memberships = memberMapper.selectMemberList(query);
        if (memberships == null || memberships.isEmpty())
        {
            return null;
        }
        // 取该坐席所属组中等待最久的一条
        AiCallQueue oldest = null;
        for (AiSkillGroupMember m : memberships)
        {
            List<AiCallQueue> queuing = queueMapper.selectQueuingByGroupId(m.getGroupId());
            if (queuing != null && !queuing.isEmpty())
            {
                AiCallQueue candidate = queuing.get(0); // 已按 priority desc, enqueue_time asc 排序
                if (oldest == null || candidate.getEnqueueTime().before(oldest.getEnqueueTime()))
                {
                    oldest = candidate;
                }
            }
        }
        if (oldest == null)
        {
            return null;
        }
        // 校验该坐席当前确实空闲
        AiCallAgentStatus agent = agentStatusMapper.selectAiCallAgentStatusByAgentId(agentId);
        if (agent == null || !"1".equals(agent.getStatus()) || !"0".equals(agent.getCallStatus()))
        {
            return null;
        }
        AiSkillGroup group = skillGroupMapper.selectAiSkillGroupByGroupId(oldest.getGroupId());
        AiSkillGroupMember self = new AiSkillGroupMember();
        self.setAgentId(agentId);
        self.setSkillLevel(memberships.get(0).getSkillLevel());
        DispatchContext ctx = DispatchContext.of(oldest.getSessionId(), oldest.getRecordId(), oldest.getCallerNumber());
        ctx.setPriority(oldest.getPriority());
        return assignToAgent(group, self, ctx);
    }
}
