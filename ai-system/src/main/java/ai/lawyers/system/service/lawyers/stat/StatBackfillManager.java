package ai.lawyers.system.service.lawyers.stat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.task.StatMinuteScheduleTask;

/**
 * 分钟物化回填（backfill）任务管理器：把"单请求内按分钟同步循环"改为异步任务化。
 *
 * <p>提交后立即返回 taskId，后台单线程串行聚合，进度写 Redis（带 TTL、每分钟刷新），
 * 前端按 taskId 轮询；跨实例用 {@link RedisLeaderLock#acquireOnce} 互斥，全局同时只允许一个回填。</p>
 *
 * <p>幂等性：分钟聚合依赖 uk_stat 唯一键覆盖写，中断/重复执行安全。</p>
 */
@Component
public class StatBackfillManager
{
    private static final Logger log = LoggerFactory.getLogger(StatBackfillManager.class);

    /** 进度对象 key 前缀 */
    private static final String TASK_KEY_PREFIX = "stat:backfill:task:";

    /** 回填互斥锁名（跨实例全局唯一） */
    private static final String LOCK_NAME = "lock:stat-backfill";

    /** 互斥锁 TTL：崩溃兜底自动释放（正常结束会显式释放） */
    private static final Duration LOCK_TTL = Duration.ofHours(3);

    /** 进度对象 TTL：终态保留一天供查询 */
    private static final long PROGRESS_TTL_HOURS = 24;

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 回填串行执行：单线程，避免并发回填互相抢锁与压垮库 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
    {
        Thread t = new Thread(r, "stat-backfill");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private StatMinuteScheduleTask statMinuteScheduleTask;

    @PreDestroy
    public void shutdown()
    {
        executor.shutdown();
    }

    /**
     * 提交回填任务。入参为已归零到分钟、且 begin &lt; end、跨度 ≤7 天（校验仍在本方法内兜底）。
     *
     * @return 任务 ID
     */
    public String submit(LocalDateTime begin, LocalDateTime end)
    {
        if (begin == null || end == null)
        {
            throw new ServiceException("开始时间与结束时间不能为空");
        }
        if (!begin.isBefore(end))
        {
            throw new ServiceException("开始时间必须早于结束时间");
        }
        long minutes = Duration.between(begin, end).toMinutes();
        if (minutes > 7 * 24 * 60)
        {
            throw new ServiceException("回填区间不能超过 7 天");
        }

        // 集群互斥：抢不到说明已有回填在跑
        String token = leaderLock.acquireOnce(LOCK_NAME, LOCK_TTL);
        if (token == null)
        {
            throw new ServiceException("已有回填任务正在执行，请等待其完成后再提交");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        Progress p = new Progress();
        p.taskId = taskId;
        p.status = Progress.RUNNING;
        p.beginTime = begin.format(MINUTE_FMT);
        p.endTime = end.format(MINUTE_FMT);
        p.totalMinutes = (int) minutes;
        p.processedMinutes = 0;
        p.rowsWritten = 0;
        p.submitTime = System.currentTimeMillis();
        saveProgress(p);

        executor.submit(() -> runTask(taskId, begin, end, token));
        return taskId;
    }

    /**
     * 查询任务进度；不存在（已过期或非法 ID）抛业务异常。
     */
    public Progress getProgress(String taskId)
    {
        if (taskId == null || taskId.isEmpty())
        {
            throw new ServiceException("任务ID不能为空");
        }
        Progress p = redisCache.getCacheObject(TASK_KEY_PREFIX + taskId);
        if (p == null)
        {
            throw new ServiceException("回填任务不存在或已过期");
        }
        return p;
    }

    private void runTask(String taskId, LocalDateTime begin, LocalDateTime end, String lockToken)
    {
        Progress p = null;
        try
        {
            p = getProgressSilently(taskId);
            p.startTime = System.currentTimeMillis();
            int rows = 0;
            int processed = 0;
            for (LocalDateTime t = begin; t.isBefore(end); t = t.plusMinutes(1))
            {
                rows += statMinuteScheduleTask.aggregateMinute(t);
                processed++;
                p.processedMinutes = processed;
                p.rowsWritten = rows;
                saveProgress(p);
            }
            p.status = Progress.SUCCESS;
            p.finishTime = System.currentTimeMillis();
            saveProgress(p);
            log.info("分钟物化回填完成 taskId={}, 分钟数={}, 写入/更新指标={}", taskId, processed, rows);
        }
        catch (Exception e)
        {
            log.error("分钟物化回填失败 taskId={}", taskId, e);
            if (p != null)
            {
                p.status = Progress.FAILED;
                p.errorMsg = e.getMessage();
                p.finishTime = System.currentTimeMillis();
                saveProgress(p);
            }
        }
        finally
        {
            leaderLock.releaseOnce(LOCK_NAME, lockToken);
        }
    }

    private Progress getProgressSilently(String taskId)
    {
        Progress p = redisCache.getCacheObject(TASK_KEY_PREFIX + taskId);
        return p != null ? p : new Progress();
    }

    private void saveProgress(Progress p)
    {
        redisCache.setCacheObject(TASK_KEY_PREFIX + p.taskId, p,
                (int) PROGRESS_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 回填任务进度（Redis JSON 存储，字段直出供前端轮询）。
     */
    public static class Progress
    {
        public static final String RUNNING = "RUNNING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";

        /** 任务ID */
        public String taskId;
        /** 状态：RUNNING / SUCCESS / FAILED */
        public String status;
        /** 回填开始（含，yyyy-MM-dd HH:mm） */
        public String beginTime;
        /** 回填结束（不含，yyyy-MM-dd HH:mm） */
        public String endTime;
        /** 总分钟数 */
        public int totalMinutes;
        /** 已处理分钟数 */
        public int processedMinutes;
        /** 累计写入/更新指标条数 */
        public long rowsWritten;
        /** 提交时间戳（ms） */
        public Long submitTime;
        /** 实际开始时间戳（ms） */
        public Long startTime;
        /** 结束时间戳（ms） */
        public Long finishTime;
        /** 失败原因 */
        public String errorMsg;

        /** 完成百分比 0~100 */
        public int getPercent()
        {
            if (totalMinutes <= 0)
            {
                return 0;
            }
            return (int) Math.min(100, Math.round(processedMinutes * 100.0 / totalMinutes));
        }
    }
}
