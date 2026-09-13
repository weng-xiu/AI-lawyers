package ai.lawyers.system.task;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiSlaPolicy;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.IAiSlaPolicyService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * F9 SLA 逐级升级定时任务
 *
 * <p>每 10 分钟扫描到点未办结工单，按匹配策略的 escalateRoles 角色链逐级升级：
 * 超期 1 个办结时限周期升级到第 1 级角色、2 个周期升级到第 2 级……每级仅广播一次
 * （Redis 标记 7 天去重），事件类型 {@code TICKET_ESCALATE} 经 {@link CallEventPublisher} 推送。</p>
 *
 * <p>与 {@link TicketSlaScheduleTask} 的分工：后者负责首次超时打标+TICKET_OVERTIME 广播，
 * 本任务负责后续逐级升级，两者互不影响。</p>
 *
 * @author ai-lawyers
 */
@Component
public class SlaEscalationScheduleTask
{
    private static final Logger log = LoggerFactory.getLogger(SlaEscalationScheduleTask.class);

    private static final String LOCK_NAME = "job:sla-escalation";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /** 升级去重标记保留 7 天（覆盖工单最长超期观察期） */
    private static final long MARKER_TTL_DAYS = 7L;

    private static final String MARKER_KEY_PREFIX = "sla:escalated:";

    @Autowired
    private AiCallTicketMapper ticketMapper;

    @Autowired
    private IAiSlaPolicyService slaPolicyService;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired(required = false)
    private CallEventPublisher callEventPublisher;

    @Autowired(required = false)
    private RedisCache redisCache;

    @Scheduled(fixedDelayString = "${call.ticket.slaEscalationIntervalMs:600000}", initialDelay = 120000)
    public void scanEscalations()
    {
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doScan);
    }

    private void doScan()
    {
        List<AiCallTicket> tickets;
        try
        {
            tickets = ticketMapper.selectEscalatableTickets();
        }
        catch (Exception e)
        {
            log.error("[SLA] 升级扫描查询失败", e);
            return;
        }
        if (tickets == null || tickets.isEmpty())
        {
            return;
        }
        Date now = new Date();
        for (AiCallTicket ticket : tickets)
        {
            try
            {
                escalateIfNeeded(ticket, now);
            }
            catch (Exception ex)
            {
                log.error("[SLA] 工单升级处理失败 ticketId={}", ticket.getTicketId(), ex);
            }
        }
    }

    private void escalateIfNeeded(AiCallTicket ticket, Date now)
    {
        String bizType = StringUtils.isNotEmpty(ticket.getExternalType())
                ? ticket.getExternalType() : "TICKET";
        AiSlaPolicy policy = slaPolicyService.matchPolicy(bizType, ticket.getPriority());
        if (policy == null || StringUtils.isEmpty(policy.getEscalateRoles())
                || policy.getResolveMinutes() == null || policy.getResolveMinutes() <= 0)
        {
            return;
        }
        String[] roles = policy.getEscalateRoles().split(",");
        long overdueMillis = now.getTime() - ticket.getDueTime().getTime();
        long periodMillis = policy.getResolveMinutes() * 60_000L;
        // 超期周期数：1 个周期 → 第 1 级
        int level = (int) Math.min(roles.length, overdueMillis / periodMillis + 1);
        if (level <= 0)
        {
            return;
        }
        String markerKey = MARKER_KEY_PREFIX + ticket.getTicketId() + ":" + level;
        if (redisCache != null && Boolean.TRUE.equals(redisCache.hasKey(markerKey)))
        {
            return;
        }
        String targetRole = roles[level - 1].trim();
        if (StringUtils.isEmpty(targetRole))
        {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("ticketId", ticket.getTicketId());
        data.put("ticketNo", ticket.getTicketNo());
        data.put("title", ticket.getTitle());
        data.put("priority", ticket.getPriority());
        data.put("dueTime", ticket.getDueTime());
        data.put("level", level);
        data.put("escalateRole", targetRole);
        data.put("callerNumber", ticket.getCallerNumber());
        data.put("ts", now.getTime());
        if (callEventPublisher != null)
        {
            callEventPublisher.broadcast("TICKET_ESCALATE", data);
        }
        log.warn("[SLA] 工单逐级升级 ticketNo={} level={} role={} dueTime={}",
                ticket.getTicketNo(), level, targetRole, ticket.getDueTime());
        if (redisCache != null)
        {
            redisCache.setCacheObject(markerKey, targetRole, (int) MARKER_TTL_DAYS, TimeUnit.DAYS);
        }
    }
}
