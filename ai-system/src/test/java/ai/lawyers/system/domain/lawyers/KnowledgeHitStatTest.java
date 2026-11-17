package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 高频命中知识排行行测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class KnowledgeHitStatTest
{
    @Test
    void 属性读写()
    {
        Date lastTime = new Date();
        KnowledgeHitStat stat = new KnowledgeHitStat();
        stat.setKnowledgeId(10L);
        stat.setTitle("劳动合同解除条件");
        stat.setHitCount(35L);
        stat.setLastTime(lastTime);

        assertEquals(10L, stat.getKnowledgeId());
        assertEquals("劳动合同解除条件", stat.getTitle());
        assertEquals(35L, stat.getHitCount());
        assertEquals(lastTime, stat.getLastTime());
    }

    @Test
    void 默认值为空()
    {
        KnowledgeHitStat stat = new KnowledgeHitStat();

        assertNull(stat.getKnowledgeId());
        assertNull(stat.getTitle());
        assertNull(stat.getHitCount());
        assertNull(stat.getLastTime());
    }
}
