package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 知识检索总览统计测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class KnowledgeQueryOverviewTest
{
    @Test
    void 属性读写()
    {
        KnowledgeQueryOverview overview = new KnowledgeQueryOverview();
        overview.setTotalCount(100L);
        overview.setHitCount(80L);
        overview.setMissCount(20L);
        overview.setPendingCount(5L);
        overview.setAvgLatencyMs(12.5D);

        assertEquals(100L, overview.getTotalCount());
        assertEquals(80L, overview.getHitCount());
        assertEquals(20L, overview.getMissCount());
        assertEquals(5L, overview.getPendingCount());
        assertEquals(12.5D, overview.getAvgLatencyMs());
    }

    @Test
    void 默认值为空()
    {
        KnowledgeQueryOverview overview = new KnowledgeQueryOverview();

        assertNull(overview.getTotalCount());
        assertNull(overview.getHitCount());
        assertNull(overview.getMissCount());
        assertNull(overview.getPendingCount());
        assertNull(overview.getAvgLatencyMs());
    }
}
