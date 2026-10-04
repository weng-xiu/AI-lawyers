package ai.lawyers.system.service.lawyers.voice.copilot;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;

/**
 * Copilot 仿真回归任务管理器（V2.79）：批量评测异步任务化。
 *
 * <p>提交后立即返回 taskId，后台单线程串行跑 {@link CopilotSimulationService#run}，
 * 进度与最终报告写 Redis（带 TTL），按 taskId 轮询；跨实例用
 * {@link RedisLeaderLock#acquireOnce} 互斥，集群同时只允许一个 Copilot 评测。</p>
 *
 * <p>与 StatBackfillManager 结构一致但锁/key/线程池完全独立，互不影响。</p>
 */
@Component
public class CopilotSimulationManager
{
    private static final Logger log = LoggerFactory.getLogger(CopilotSimulationManager.class);

    /** 任务对象 key 前缀 */
    private static final String TASK_KEY_PREFIX = "copilot:simulation:task:";

    /** 互斥锁名（跨实例全局唯一） */
    private static final String LOCK_NAME = "lock:copilot-simulation";

    /** 互斥锁 TTL：崩溃兜底自动释放（正常结束会显式释放） */
    private static final Duration LOCK_TTL = Duration.ofHours(1);

    /** 任务对象 TTL：终态保留一天供查询 */
    private static final long TASK_TTL_HOURS = 24;

    /** 评测串行执行：单线程，避免并发评测压垮模型/库 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
    {
        Thread t = new Thread(r, "copilot-simulation");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RedisLeaderLock leaderLock;

    @Autowired
    private CopilotSimulationService copilotSimulationService;

    @PreDestroy
    public void shutdown()
    {
        executor.shutdown();
    }

    /**
     * 提交评测任务。
     *
     * @param casesJson 用例集 JSON；空则跑默认 classpath 用例
     * @return 任务 ID
     */
    public String submit(String casesJson)
    {
        String token = leaderLock.acquireOnce(LOCK_NAME, LOCK_TTL);
        if (token == null)
        {
            throw new ServiceException("已有 Copilot 评测任务正在执行，请等待其完成后再提交");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        Task t = new Task();
        t.taskId = taskId;
        t.status = Task.RUNNING;
        t.submitTime = System.currentTimeMillis();
        save(t);

        executor.submit(() -> runTask(taskId, casesJson, token));
        return taskId;
    }

    /** 查询任务（含进度/报告）；不存在（过期或非法 ID）抛业务异常 */
    public Task getTask(String taskId)
    {
        if (taskId == null || taskId.isEmpty())
        {
            throw new ServiceException("任务ID不能为空");
        }
        Task t = redisCache.getCacheObject(TASK_KEY_PREFIX + taskId);
        if (t == null)
        {
            throw new ServiceException("Copilot 评测任务不存在或已过期");
        }
        return t;
    }

    private void runTask(String taskId, String casesJson, String lockToken)
    {
        Task t = null;
        try
        {
            t = getTaskSilently(taskId);
            t.startTime = System.currentTimeMillis();
            save(t);

            ObjectNode report = copilotSimulationService.run(casesJson);
            t.report = report;
            t.totalCases = report.path("totalCases").asInt(0);
            t.passedCases = report.path("passedCases").asInt(0);
            t.status = Task.SUCCESS;
            t.finishTime = System.currentTimeMillis();
            save(t);
            log.info("Copilot 评测任务完成 taskId={}, {}/{} 用例通过",
                    taskId, t.passedCases, t.totalCases);
        }
        catch (Exception e)
        {
            log.error("Copilot 评测任务失败 taskId={}", taskId, e);
            if (t != null)
            {
                t.status = Task.FAILED;
                t.errorMsg = e.getMessage();
                t.finishTime = System.currentTimeMillis();
                save(t);
            }
        }
        finally
        {
            leaderLock.releaseOnce(LOCK_NAME, lockToken);
        }
    }

    private Task getTaskSilently(String taskId)
    {
        Task t = redisCache.getCacheObject(TASK_KEY_PREFIX + taskId);
        return t != null ? t : new Task();
    }

    private void save(Task t)
    {
        redisCache.setCacheObject(TASK_KEY_PREFIX + t.taskId, t,
                (int) TASK_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 评测任务状态（Redis JSON 存储，字段直出供轮询）。
     */
    public static class Task
    {
        public static final String RUNNING = "RUNNING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";

        /** 任务ID */
        public String taskId;
        /** 状态：RUNNING / SUCCESS / FAILED */
        public String status;
        /** 总用例数（完成后填充） */
        public int totalCases;
        /** 通过用例数（完成后填充） */
        public int passedCases;
        /** 提交时间戳（ms） */
        public Long submitTime;
        /** 实际开始时间戳（ms） */
        public Long startTime;
        /** 结束时间戳（ms） */
        public Long finishTime;
        /** 失败原因 */
        public String errorMsg;
        /** 完成后的完整回归报告 */
        public ObjectNode report;

        /** 完成百分比：评测为整批一次执行，仅 0/100 两态 */
        public int getPercent()
        {
            return Task.SUCCESS.equals(status) ? 100 : 0;
        }
    }
}
