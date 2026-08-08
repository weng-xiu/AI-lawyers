package ai.lawyers.system.service.impl.lawyers.trunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;

/**
 * 线路监控定时任务
 *
 * 通过 call.monitor.enabled=false 可整体关闭（例如本地开发环境）。
 * 多实例部署时建议只在一个实例开启，或改造为分布式锁调度。
 */
@Component
@ConditionalOnProperty(prefix = "call.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TrunkScheduleTask
{
    private static final Logger log = LoggerFactory.getLogger(TrunkScheduleTask.class);

    @Autowired
    private ITrunkMonitorService trunkMonitorService;

    /**
     * 线路健康探测：每 30 秒一次。
     */
    @Scheduled(fixedDelayString = "${call.monitor.healthCheckIntervalMs:30000}", initialDelay = 20000)
    public void healthCheck()
    {
        try
        {
            trunkMonitorService.healthCheckAll();
        }
        catch (Exception e)
        {
            log.error("线路健康探测任务异常", e);
        }
    }

    /**
     * 熔断恢复探测：每 30 秒一次。
     */
    @Scheduled(fixedDelayString = "${call.monitor.circuitRecoverIntervalMs:30000}", initialDelay = 35000)
    public void recoverCircuit()
    {
        try
        {
            trunkMonitorService.recoverCircuitBreakers();
        }
        catch (Exception e)
        {
            log.error("熔断恢复任务异常", e);
        }
    }

    /**
     * 质量指标聚合：每分钟第 5 秒执行，统计上一分钟数据。
     */
    @Scheduled(cron = "${call.monitor.metricCron:5 * * * * ?}")
    public void collectMetrics()
    {
        try
        {
            trunkMonitorService.collectMetrics();
        }
        catch (Exception e)
        {
            log.error("线路质量统计任务异常", e);
        }
    }

    /**
     * 质量告警评估：每 2 分钟一次。
     */
    @Scheduled(fixedDelayString = "${call.monitor.alarmIntervalMs:120000}", initialDelay = 60000)
    public void evaluateAlarm()
    {
        try
        {
            trunkMonitorService.evaluateQualityAlarm();
        }
        catch (Exception e)
        {
            log.error("线路质量告警评估任务异常", e);
        }
    }
}
