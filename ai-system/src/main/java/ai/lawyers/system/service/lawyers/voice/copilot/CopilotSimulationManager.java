package ai.lawyers.system.service.lawyers.voice.copilot;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.system.service.lawyers.voice.AbstractSimulationTaskManager;

/**
 * Copilot 仿真回归任务管理器（V2.79 / P1-8 纳入统一平台）：批量评测异步任务化。
 *
 * <p>提交后立即返回 taskId，后台单线程串行跑 {@link CopilotSimulationService#run}，
 * 进度与最终报告写 Redis（带 TTL），按 taskId 轮询；跨实例用
 * {@link ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock#acquireOnce} 互斥，
 * 集群同时只允许一个 Copilot 评测。公共逻辑见 {@link AbstractSimulationTaskManager}。</p>
 *
 * @author ai-lawyers
 */
@Component
public class CopilotSimulationManager extends AbstractSimulationTaskManager
{
    /** 任务对象 key 前缀 */
    private static final String TASK_KEY_PREFIX = "copilot:simulation:task:";

    /** 互斥锁名（跨实例全局唯一） */
    private static final String LOCK_NAME = "lock:copilot-simulation";

    /** 互斥锁 TTL：崩溃兜底自动释放（正常结束会显式释放） */
    private static final Duration LOCK_TTL = Duration.ofHours(1);

    @Autowired
    private CopilotSimulationService copilotSimulationService;

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
        return "已有 Copilot 评测任务正在执行，请等待其完成后再提交";
    }

    @Override
    protected String threadName()
    {
        return "copilot-simulation";
    }

    @Override
    protected ObjectNode doRun(String casesJson)
    {
        return copilotSimulationService.run(casesJson);
    }
}
