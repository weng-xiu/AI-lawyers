package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 知识检索查询日志实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiKnowledgeQueryLogTest
{
    @Test
    void 属性读写()
    {
        Date createTime = new Date();
        AiKnowledgeQueryLog log = new AiKnowledgeQueryLog();
        log.setLogId(1L);
        log.setQueryText("如何申请劳动仲裁");
        log.setQueryHash("a1b2c3d4");
        log.setScene("agent");
        log.setHitCount(5);
        log.setTopKnowledgeId(2L);
        log.setRoute("fused");
        log.setLatencyMs(120);
        log.setConverted(1);
        log.setCreateTime(createTime);

        assertEquals(1L, log.getLogId());
        assertEquals("如何申请劳动仲裁", log.getQueryText());
        assertEquals("a1b2c3d4", log.getQueryHash());
        assertEquals("agent", log.getScene());
        assertEquals(5, log.getHitCount());
        assertEquals(2L, log.getTopKnowledgeId());
        assertEquals("fused", log.getRoute());
        assertEquals(120, log.getLatencyMs());
        assertEquals(1, log.getConverted());
        assertEquals(createTime, log.getCreateTime());
    }

    @Test
    void 默认值为空()
    {
        AiKnowledgeQueryLog log = new AiKnowledgeQueryLog();

        assertNull(log.getLogId());
        assertNull(log.getQueryText());
        assertNull(log.getQueryHash());
        assertNull(log.getScene());
        assertNull(log.getHitCount());
        assertNull(log.getTopKnowledgeId());
        assertNull(log.getRoute());
        assertNull(log.getLatencyMs());
        assertNull(log.getConverted());
        assertNull(log.getCreateTime());
    }
}
