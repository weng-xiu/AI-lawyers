package ai.lawyers.system.service.lawyers.cluster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-C6：实例排空状态单测。
 */
public class InstanceDrainStateTest
{
    @Test
    public void defaultNotDraining()
    {
        InstanceDrainState state = new InstanceDrainState();
        assertFalse(state.isDraining());
        assertEquals(0L, state.getDrainingSinceMs());
    }

    @Test
    public void beginDrainSetsFlagAndTime()
    {
        InstanceDrainState state = new InstanceDrainState();
        long before = System.currentTimeMillis();
        state.beginDrain();

        assertTrue(state.isDraining());
        assertTrue(state.getDrainingSinceMs() >= before);
    }

    @Test
    public void beginDrainIdempotentKeepsFirstTime() throws InterruptedException
    {
        InstanceDrainState state = new InstanceDrainState();
        state.beginDrain();
        long first = state.getDrainingSinceMs();
        Thread.sleep(15);
        state.beginDrain();

        assertEquals(first, state.getDrainingSinceMs());
    }

    @Test
    public void resetClearsDraining()
    {
        InstanceDrainState state = new InstanceDrainState();
        state.beginDrain();
        state.reset();

        assertFalse(state.isDraining());
        assertEquals(0L, state.getDrainingSinceMs());
    }
}
