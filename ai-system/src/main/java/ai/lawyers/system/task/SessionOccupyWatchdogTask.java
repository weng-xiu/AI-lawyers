package ai.lawyers.system.task;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 跨渠道会话占用残留看门狗（方案B P1）。
 *
 * <p>周期性回收 expire_time 已过但仍处于活跃态（active_flag='1'）的统一会话记录，
 * 兜底进程崩溃、节点宕机、Redis 键丢失等异常残留；多实例经 {@link RedisLeaderLock}
 * 单主执行。总开关关闭（session.occupy.enabled=false）时跳过扫描。</p>
 *
 * @author ai-lawyers
 */
@Component
public class SessionOccupyWatchdogTask
{
    private static final Logger log = LoggerFactory.getLogger(SessionOccupyWatchdogTask.class);

    private static final String LOCK_NAME = "job:session-occupy-watchdog";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    @Value("${session.occupy.enabled:true}")
    private boolean occupyEnabled;

    @Autowired
    private IAiUnifiedSessionService unifiedSessionService;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Scheduled(fixedDelayString = "${session.occupy.watchdog-interval-ms:300000}", initialDelay = 60000)
    public void scanExpired()
    {
        if (!occupyEnabled)
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doScan);
    }

    private void doScan()
    {
        int reclaimed = unifiedSessionService.reclaimExpired();
        if (reclaimed > 0)
        {
            log.warn("[SessionOccupy] 看门狗回收过期活跃会话 {} 条", reclaimed);
        }
    }
}
