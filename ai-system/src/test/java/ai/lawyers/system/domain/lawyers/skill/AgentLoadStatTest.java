package ai.lawyers.system.domain.lawyers.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 坐席负载统计测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AgentLoadStatTest
{
    @Test
    void 属性读写()
    {
        Date lastCallStartTime = new Date();
        AgentLoadStat stat = new AgentLoadStat();
        stat.setAgentId(7L);
        stat.setLastCallStartTime(lastCallStartTime);
        stat.setTodayCompleted(18);

        assertEquals(7L, stat.getAgentId());
        assertEquals(lastCallStartTime, stat.getLastCallStartTime());
        assertEquals(18, stat.getTodayCompleted());
    }

    @Test
    void 默认值为空()
    {
        AgentLoadStat stat = new AgentLoadStat();

        assertNull(stat.getAgentId());
        assertNull(stat.getLastCallStartTime());
        assertNull(stat.getTodayCompleted());
    }
}
