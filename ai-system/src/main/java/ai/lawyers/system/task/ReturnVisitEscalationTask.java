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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.system.domain.lawyers.AiReturnVisitTask;
import ai.lawyers.system.mapper.lawyers.AiReturnVisitTaskMapper;
import ai.lawyers.system.service.lawyers.CallEventPublisher;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * F5 智能回访 —— 逾期回访任务逐级升级定时任务
 *
 * <p>每 10 分钟扫描 status=2（已逾期，由 {@code AiReturnVisitTaskServiceImpl.scanOverdueTasks} 打标）
 * 且仍未回访的任务，按超期时长做三级升级，每级仅广播一次（Redis 标记 7 天去重）：</p>
 * <ul>
 *   <li>L1：刚逾期（&gt;0）→ 提醒受理人本人；</li>
 *   <li>L2：逾期 ≥ 1 天 → 升级班组长角色；</li>
 *   <li>L3：逾期 ≥ 3 天 → 升级分管领导角色。</li>
 * </ul>
 * <p>角色链可通过配置 {@code call.returnVisit.escalate-roles} 覆盖（逗号分隔，缺省
 * agent/teamleader/leader）。事件类型 {@code RETURN_VISIT_ESCALATE} 经 {@link CallEventPublisher}
 * 广播，供坐席工作台/后续站内信消费。N7：多实例经 {@link RedisLeaderLock} 单主执行。</p>
 *
 * @author ai-lawyers
 */
@Component
public class ReturnVisitEscalationTask
{
    private static final Logger log = LoggerFactory.getLogger(ReturnVisitEscalationTask.class);

    private static final String LOCK_NAME = "job:return-visit-escalation";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /** 升级去重标记保留 7 天 */
    private static final long MARKER_TTL_DAYS = 7L;
    private static final String MARKER_KEY_PREFIX = "rv:escalated:";

    private static final long L2_MINUTES = 24 * 60L;
    private static final long L3_MINUTES = 72 * 60L;

    /** 单轮扫描上限，避免积压时单任务处理过重 */
    private static final int BATCH_LIMIT = 200;

    /** 升级目标角色链（L1/L2/L3），可配置覆盖 */
    @Value("${call.returnVisit.escalate-roles:agent,teamleader,leader}")
    private String escalateRoles;

    @Autowired
    private AiReturnVisitTaskMapper taskMapper;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired(required = false)
    private CallEventPublisher callEventPublisher;

    @Autowired(required = false)
    private RedisCache redisCache;

    @Scheduled(fixedDelayString = "${call.returnVisit.escalationIntervalMs:600000}", initialDelay = 150000)
    public void scanEscalations()
    {
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doScan);
    }

    private void doScan()
    {
        List<AiReturnVisitTask> overdueTasks = taskMapper.selectOverdueTasksForEscalate(BATCH_LIMIT);
        if (overdueTasks == null || overdueTasks.isEmpty())
        {
            return;
        }
        String[] roles = parseRoles(escalateRoles);
        Date now = new Date();
        for (AiReturnVisitTask task : overdueTasks)
        {
            try
            {
                escalateIfNeeded(task, roles, now);
            }
            catch (Exception ex)
            {
                log.error("[ReturnVisit] 逾期升级处理失败 taskId={}", task.getTaskId(), ex);
            }
        }
    }

    private void escalateIfNeeded(AiReturnVisitTask task, String[] roles, Date now)
    {
        if (task.getPlanTime() == null)
        {
            return;
        }
        long overdueMinutes = (now.getTime() - task.getPlanTime().getTime()) / 60000L;
        int level;
        if (overdueMinutes >= L3_MINUTES) { level = 3; }
        else if (overdueMinutes >= L2_MINUTES) { level = 2; }
        else { level = 1; }
        if (level - 1 >= roles.length || roles[level - 1].isEmpty())
        {
            return;
        }
        String markerKey = MARKER_KEY_PREFIX + task.getTaskId() + ":" + level;
        if (redisCache != null && Boolean.TRUE.equals(redisCache.hasKey(markerKey)))
        {
            return;
        }
        String targetRole = roles[level - 1];
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("taskNo", task.getTaskNo());
        data.put("callerName", task.getCallerName());
        data.put("callerNumber", task.getCallerNumber());
        data.put("priority", task.getPriority());
        data.put("assignee", task.getAssignee());
        data.put("assigneeId", task.getAssigneeId());
        data.put("planTime", task.getPlanTime());
        data.put("overdueMinutes", overdueMinutes);
        data.put("level", level);
        data.put("escalateRole", targetRole);
        data.put("ts", now.getTime());
        if (callEventPublisher != null)
        {
            callEventPublisher.broadcast("RETURN_VISIT_ESCALATE", data);
        }
        log.warn("[ReturnVisit] 逾期回访逐级升级 taskNo={} level={} role={} overdueMin={}",
                task.getTaskNo(), level, targetRole, overdueMinutes);
        if (redisCache != null)
        {
            redisCache.setCacheObject(markerKey, targetRole, (int) MARKER_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    private static String[] parseRoles(String config)
    {
        if (config == null || config.trim().isEmpty())
        {
            return new String[]{"agent", "teamleader", "leader"};
        }
        String[] parts = config.split(",");
        String[] roles = new String[parts.length];
        for (int i = 0; i < parts.length; i++)
        {
            roles[i] = parts[i].trim();
        }
        return roles;
    }
}
