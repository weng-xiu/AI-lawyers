package ai.lawyers.web.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.service.lawyers.cluster.InstanceDrainState;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P3-C6：排空健康指示器单测——正常 UP，排空 OUT_OF_SERVICE（readiness 摘流量判据）。
 */
public class DrainHealthIndicatorTest
{
    @Test
    public void upWhenNotDraining()
    {
        InstanceDrainState state = new InstanceDrainState();
        DrainHealthIndicator indicator = new DrainHealthIndicator();
        ReflectionTestUtils.setField(indicator, "drainState", state);

        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals(false, health.getDetails().get("draining"));
    }

    @Test
    public void outOfServiceWhenDraining()
    {
        InstanceDrainState state = new InstanceDrainState();
        state.beginDrain();
        DrainHealthIndicator indicator = new DrainHealthIndicator();
        ReflectionTestUtils.setField(indicator, "drainState", state);

        Health health = indicator.health();
        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
        assertEquals(true, health.getDetails().get("draining"));
    }
}
