package ai.lawyers.system.service.impl.lawyers.trunk;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;

/**
 * 线路监控定时任务
 *
 * 通过 call.monitor.enabled=false 可整体关闭（例如本地开发环境）。
 * N7：多实例部署时通过 {@link RedisLeaderLock} 单主锁保证每个任务全组仅一个实例执行
 * （cluster.lock.enabled=false 可回退为各实例各自执行）。
 */
@Component
@ConditionalOnProperty(prefix = "call.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TrunkScheduleTask
{
    private static final Logger log = LoggerFactory.getLogger(TrunkScheduleTask.class);

    private static final String LOCK_HEALTH = "job:trunk-health";
    private static final String LOCK_CIRCUIT = "job:trunk-circuit-recover";
    private static final String LOCK_METRICS = "job:trunk-metrics";
    private static final String LOCK_ALARM = "job:trunk-alarm";

    /** TTL 均取大于单轮最坏耗时：探测/恢复 60s，指标 2min，告警 5min */
    private static final Duration TTL_SHORT = Duration.ofSeconds(60);
    private static final Duration TTL_METRICS = Duration.ofMinutes(2);
    private static final Duration TTL_ALARM = Duration.ofMinutes(5);

    @Autowired
    private ITrunkMonitorService trunkMonitorService;

    @Autowired
    private RedisLeaderLock leaderLock;

    /**
     * 线路健康探测：每 30 秒一次。
     */
    @Scheduled(fixedDelayString = "${call.monitor.healthCheckIntervalMs:30000}", initialDelay = 20000)
    public void healthCheck()
    {
        leaderLock.tryRun(LOCK_HEALTH, TTL_SHORT, () ->
        {
            try
            {
                trunkMonitorService.healthCheckAll();
            }
            catch (Exception e)
            {
                log.error("线路健康探测任务异常", e);
            }
        });
    }

    /**
     * 熔断恢复探测：每 30 秒一次。
     */
    @Scheduled(fixedDelayString = "${call.monitor.circuitRecoverIntervalMs:30000}", initialDelay = 35000)
    public void recoverCircuit()
    {
        leaderLock.tryRun(LOCK_CIRCUIT, TTL_SHORT, () ->
        {
            try
            {
                trunkMonitorService.recoverCircuitBreakers();
            }
            catch (Exception e)
            {
                log.error("熔断恢复任务异常", e);
            }
        });
    }

    /**
     * 质量指标聚合：每分钟第 5 秒执行，统计上一分钟数据。
     */
    @Scheduled(cron = "${call.monitor.metricCron:5 * * * * ?}")
    public void collectMetrics()
    {
        leaderLock.tryRun(LOCK_METRICS, TTL_METRICS, () ->
        {
            try
            {
                trunkMonitorService.collectMetrics();
            }
            catch (Exception e)
            {
                log.error("线路质量统计任务异常", e);
            }
        });
    }

    /**
     * 质量告警评估：每 2 分钟一次。
     */
    @Scheduled(fixedDelayString = "${call.monitor.alarmIntervalMs:120000}", initialDelay = 60000)
    public void evaluateAlarm()
    {
        leaderLock.tryRun(LOCK_ALARM, TTL_ALARM, () ->
        {
            try
            {
                trunkMonitorService.evaluateQualityAlarm();
            }
            catch (Exception e)
            {
                log.error("线路质量告警评估任务异常", e);
            }
        });
    }
}
