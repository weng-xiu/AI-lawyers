package ai.lawyers.web.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import ai.lawyers.system.service.lawyers.cluster.InstanceDrainState;

/**
 * P3-C6：排空状态健康指示器，Bean 名 {@code drain}，纳入 readiness 探针组。
 *
 * <p>正常运行返回 UP；进入排空态返回 OUT_OF_SERVICE——nginx/负载均衡健康检查
 * /actuator/health/readiness 随即判失败并摘除本实例流量，停止路由新请求。</p>
 */
@Component("drain")
public class DrainHealthIndicator implements HealthIndicator
{
    @Autowired
    private InstanceDrainState drainState;

    @Override
    public Health health()
    {
        if (drainState.isDraining())
        {
            return Health.outOfService()
                    .withDetail("draining", true)
                    .withDetail("drainingSinceMs", drainState.getDrainingSinceMs())
                    .build();
        }
        return Health.up().withDetail("draining", false).build();
    }
}
