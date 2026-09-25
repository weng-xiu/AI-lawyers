package ai.lawyers.system.task;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.queue.StreamQueueService;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;

/**
 * Stream 队列积压与死信监控任务（P3-H1）。
 *
 * <p>每 30 秒扫描全部已注册队列：
 * <ul>
 *   <li><b>积压告警</b>：pending（已投递未 ACK）连续超过阈值达
 *       {@code queue.stream.lag-alarm-duration-ms}（默认 5 分钟，对应"lag>1000 持续 5min 告警"），
 *       产生一次 STREAM_LAG 告警；恢复低于阈值后告警状态重置，允许下一次积压再次告警
 *       （"按积压事件告警一次"，不随扫描周期刷屏）；</li>
 *   <li><b>死信告警</b>：死信数超过阈值（默认 1，即出现死信即提醒人工排查重投），
 *       产生 STREAM_DLQ 告警，DB 侧 10 分钟抑制窗口兜底防刷。</li>
 * </ul>
 * </p>
 *
 * <p>多实例经 {@link RedisLeaderLock} 保证单实例执行；队列注册表是 JVM 内存态
 * （各 Dispatcher 在本实例 registerHandler），非 leader 实例不扫描以避免重复告警，
 * 队列覆盖完整性依赖 leader 实例注册全量队列（5 个内置 Dispatcher 各实例均注册，满足）。</p>
 *
 * @author ai-lawyers
 */
@Component
public class StreamLagMonitorTask
{
    private static final Logger log = LoggerFactory.getLogger(StreamLagMonitorTask.class);

    /** 集群单主锁名（TTL 2 分钟，大于单轮扫描最坏耗时） */
    private static final String LOCK_NAME = "job:stream-lag-monitor";
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private StreamQueueService streamQueueService;

    @Autowired
    private ITrunkMonitorService trunkMonitorService;

    /** 总开关，默认开启 */
    @Value("${queue.stream.lag-alarm.enabled:true}")
    private boolean enabled;

    /** 积压告警阈值（pending 消息数） */
    @Value("${queue.stream.lag-alarm-threshold:1000}")
    private long lagThreshold;

    /** 积压持续时长（毫秒），超过才告警，避免瞬时抖动误报 */
    @Value("${queue.stream.lag-alarm-duration-ms:300000}")
    private long lagDurationMs;

    /** 死信告警阈值（默认 1：出现死信即提醒） */
    @Value("${queue.stream.dlq-alarm-threshold:1}")
    private long dlqThreshold;

    /** 各队列积压超阈值的起始时刻（queue -> firstOverMs）；低于阈值时移除 */
    private final Map<String, Long> overSinceMap = new ConcurrentHashMap<>();

    /** 各队列当前是否已就本次积压事件告警过（恢复后重置） */
    private final Set<String> lagAlarmed = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = 30000L, initialDelay = 60000L)
    public void scan()
    {
        if (!enabled)
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, this::doScan);
    }

    private void doScan()
    {
        long now = System.currentTimeMillis();
        for (String queue : streamQueueService.registeredQueues())
        {
            try
            {
                checkLag(queue, now);
                checkDlq(queue);
            }
            catch (Exception e)
            {
                log.warn("Stream[{}] 积压监控扫描异常: {}", queue, e.getMessage());
            }
        }
    }

    /** 积压判定：连续超阈值达 lagDurationMs 才告警一次，恢复后重置 */
    private void checkLag(String queue, long now)
    {
        long pending = streamQueueService.pendingCount(queue);
        if (pending < 0)
        {
            // Redis 查询异常：不更新状态，避免故障期间误告警/误重置
            return;
        }
        if (pending < lagThreshold)
        {
            overSinceMap.remove(queue);
            lagAlarmed.remove(queue);
            return;
        }
        overSinceMap.putIfAbsent(queue, now);
        long overFor = now - overSinceMap.get(queue);
        if (overFor >= lagDurationMs && lagAlarmed.add(queue))
        {
            log.warn("Stream[{}] 积压告警 pending={} 阈值={} 已持续{}ms", queue, pending, lagThreshold, overFor);
            trunkMonitorService.raiseStreamLagAlarm(queue, pending, lagThreshold);
        }
    }

    /** 死信判定：超过阈值即告警（DB 抑制窗口防刷） */
    private void checkDlq(String queue)
    {
        long dead = streamQueueService.deadLetterCount(queue);
        if (dead >= dlqThreshold)
        {
            trunkMonitorService.raiseStreamDlqAlarm(queue, dead, dlqThreshold);
        }
    }
}
