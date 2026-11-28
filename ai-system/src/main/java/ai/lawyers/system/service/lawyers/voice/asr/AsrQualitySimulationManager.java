package ai.lawyers.system.service.lawyers.voice.asr;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ai.lawyers.system.service.lawyers.voice.AbstractSimulationTaskManager;

/**
 * N2（语雀 7.4 S1）：ASR 转写质量仿真评测任务管理器（纳入统一异步任务平台）。
 *
 * <p>提交即返回 taskId，后台单线程串行跑 {@link AsrQualitySimulationService#run}，进度与
 * 报告写 Redis 按 taskId 轮询；跨实例用
 * {@link ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock#acquireOnce} 互斥。
 * 与 Copilot / 机器人评测的锁、key、线程池完全独立，可并行互不影响。</p>
 *
 * <p>ASR 报告原生计数字段为 totalSamples/passedSamples，而公共骨架
 * {@link AbstractSimulationTaskManager} 的 Task 顶层便利计数固定读取
 * totalCases/passedCases，故 doRun 在返回报告上补这两个别名字段；报告内嵌的
 * totalSamples/passedSamples 等原字段保持不变，别名仅用于骨架进度计数。</p>
 *
 * @author ai-lawyers
 */
@Component
public class AsrQualitySimulationManager extends AbstractSimulationTaskManager
{
    /** 任务对象 key 前缀 */
    private static final String TASK_KEY_PREFIX = "asr:simulation:task:";

    /** 互斥锁名（跨实例全局唯一） */
    private static final String LOCK_NAME = "lock:asr-simulation";

    /** 互斥锁 TTL：崩溃兜底自动释放 */
    private static final Duration LOCK_TTL = Duration.ofHours(1);

    @Autowired
    private AsrQualitySimulationService asrQualitySimulationService;

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
        return "已有ASR转写质量评测任务正在执行，请等待其完成后再提交";
    }

    @Override
    protected String threadName()
    {
        return "asr-simulation";
    }

    @Override
    protected ObjectNode doRun(String samplesJson)
    {
        ObjectNode report = asrQualitySimulationService.run(samplesJson);
        // 适配公共骨架 Task 顶层便利计数（骨架固定读取 totalCases/passedCases）
        report.put("totalCases", report.path("totalSamples").asInt(0));
        report.put("passedCases", report.path("passedSamples").asInt(0));
        return report;
    }
}
