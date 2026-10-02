package ai.lawyers.system.task;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.IAiCallBehaviorRiskService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 呼叫行为风控定时任务（P2-15）。
 *
 * <p>每 30 分钟扫描评估一次：按号码盲索引聚合窗口内呼叫行为
 * （来电次数/超短通话/夜间来电/未接率），评分超阈值自动生成高频置底规则
 * 并留痕供班组长复核。多实例经 {@link RedisLeaderLock} 保证单实例执行。</p>
 *
 * @author ai-lawyers
 */
@Component
public class CallBehaviorRiskScheduleTask
{
    private static final Logger log = LoggerFactory.getLogger(CallBehaviorRiskScheduleTask.class);

    /** 集群单主锁名（TTL 5 分钟，大于单轮扫描最坏耗时） */
    private static final String LOCK_NAME = "job:call-risk";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private IAiCallBehaviorRiskService riskService;

    /** 总开关（call.risk.enabled，关闭后任务空转） */
    @Value("${call.risk.enabled:true}")
    private boolean enabled;

    /** 每 30 分钟执行一次（分钟位错开统计任务） */
    @Scheduled(cron = "${call.risk.cron:0 17/30 * * * ?}")
    public void scan()
    {
        if (!enabled)
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, () -> {
            try
            {
                int inserted = riskService.scanRisk();
                if (inserted > 0)
                {
                    log.info("[CallRisk] 行为风控扫描完成，新增风险记录 {} 条", inserted);
                }
            }
            catch (Exception ex)
            {
                // 单轮失败不影响下一轮，告警留观
                log.error("[CallRisk] 行为风控扫描异常: {}", ex.getMessage(), ex);
            }
        });
    }
}
