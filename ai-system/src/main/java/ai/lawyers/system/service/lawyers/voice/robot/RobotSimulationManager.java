package ai.lawyers.system.service.lawyers.voice.robot;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.system.service.lawyers.voice.AbstractSimulationTaskManager;

/**
 * P1-8：语音机器人仿真回归任务管理器（纳入统一平台，异步任务化）。
 *
 * <p>提交即返回 taskId，后台单线程串行跑 {@link RobotSimulationService#run}，进度与报告
 * 写 Redis 按 taskId 轮询；跨实例用
 * {@link ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock#acquireOnce} 互斥。
 * 与 Copilot 评测锁/key/线程池完全独立，可并行互不影响。</p>
 *
 * @author ai-lawyers
 */
@Component
public class RobotSimulationManager extends AbstractSimulationTaskManager
{
    /** 任务对象 key 前缀 */
    private static final String TASK_KEY_PREFIX = "robot:simulation:task:";

    /** 互斥锁名（跨实例全局唯一） */
    private static final String LOCK_NAME = "lock:robot-simulation";

    /** 互斥锁 TTL：崩溃兜底自动释放 */
    private static final Duration LOCK_TTL = Duration.ofHours(1);

    @Autowired
    private RobotSimulationService robotSimulationService;

    @Override
    protected String taskKeyPrefix()
    {
        return TASK_KEY_PREFIX;
    }

    @Override
    protected String lockName()
    {
        return LOCK_NAME;
    }

    @Override
    protected Duration lockTtl()
    {
        return LOCK_TTL;
    }

    @Override
    protected String busyMessage()
    {
        return "已有机器人评测任务正在执行，请等待其完成后再提交";
    }

    @Override
    protected String threadName()
    {
        return "robot-simulation";
    }

    @Override
    protected ObjectNode doRun(String casesJson)
    {
        return robotSimulationService.run(casesJson);
    }
}
