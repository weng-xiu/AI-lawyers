package ai.lawyers.system.task;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.impl.lawyers.storage.RecordingUploadService;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * 录音补传定时任务（P0-5）。
 *
 * <p>每 10 分钟扫描上传前态话单（上传队列溢出、入队失败、上传重试耗尽等漏处理场景），
 * 从本地录音目录取文件重新入队，worker 自带就绪等待与退避重试。多实例经
 * {@link RedisLeaderLock} 保证单实例执行；本地模式且未开启加密时无待处理工作，
 * {@link RecordingUploadService#reconcilePendingUploads} 内部直接返回 0。</p>
 *
 * @author ai-lawyers
 */
@Component
public class RecordingReconcileTask
{
    private static final Logger log = LoggerFactory.getLogger(RecordingReconcileTask.class);

    /** 集群单主锁名（TTL 3 分钟，大于单轮扫描最坏耗时） */
    private static final String LOCK_NAME = "job:recording-reconcile";
    private static final Duration LOCK_TTL = Duration.ofMinutes(3);

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private RecordingUploadService recordingUploadService;

    /** 总开关（call.recording.reconcile-enabled，关闭后任务空转） */
    @Value("${call.recording.reconcile-enabled:true}")
    private boolean enabled;

    /** 避开在途任务：只补扫结束超过 N 分钟的话单 */
    @Value("${call.recording.reconcile-min-age-minutes:5}")
    private int minAgeMinutes;

    /** 补扫时间窗：只补扫近 N 小时内结束的话单 */
    @Value("${call.recording.reconcile-max-age-hours:24}")
    private int maxAgeHours;

    /** 单轮补扫上限 */
    @Value("${call.recording.reconcile-batch-limit:200}")
    private int batchLimit;

    /** 每 10 分钟执行一次（分钟位错开统计/风控任务） */
    @Scheduled(cron = "${call.recording.reconcile-cron:0 11 */1 * * ?}")
    public void scan()
    {
        if (!enabled)
        {
            return;
        }
        leaderLock.tryRun(LOCK_NAME, LOCK_TTL, () -> {
            try
            {
                int requeued = recordingUploadService.reconcilePendingUploads(
                        minAgeMinutes, maxAgeHours, batchLimit);
                if (requeued > 0)
                {
                    log.info("[RecordingReconcile] 补扫完成，重新入队 {} 条", requeued);
                }
            }
            catch (Exception ex)
            {
                // 单轮失败不影响下一轮
                log.error("[RecordingReconcile] 补扫异常: {}", ex.getMessage(), ex);
            }
        });
    }
}
