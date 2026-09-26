package ai.lawyers.system.service.lawyers.cluster;

import org.springframework.stereotype.Component;

/**
 * P3-C6：本实例排空（draining）状态。
 *
 * <p>无损发布流程中，应用收到 SIGTERM 后由 GracefulDrainCoordinator 置为排空态：
 * <ul>
 *   <li>readiness 探针翻 OUT_OF_SERVICE → nginx/负载均衡摘除流量；</li>
 *   <li>外呼扫描跳过新任务领取（在途拨号继续执行至完成）；</li>
 *   <li>等待在途通话归零或超时后才真正销毁 Bean。</li>
 * </ul>
 * 默认非排空，单机正常运行与单机停机行为不受影响。
 */
@Component
public class InstanceDrainState
{
    private volatile boolean draining = false;

    private volatile long drainingSinceMs = 0L;

    public boolean isDraining()
    {
        return draining;
    }

    public long getDrainingSinceMs()
    {
        return drainingSinceMs;
    }

    /** 进入排空态（幂等，保留首次进入时间） */
    public void beginDrain()
    {
        if (!draining)
        {
            drainingSinceMs = System.currentTimeMillis();
        }
        draining = true;
    }

    /** 退出排空态 */
    public void reset()
    {
        draining = false;
        drainingSinceMs = 0L;
    }
}
