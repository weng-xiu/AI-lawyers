package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 知识库审核发布流水实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiKnowledgeAuditTest
{
    @Test
    void 属性读写()
    {
        Date auditTime = new Date();
        AiKnowledgeAudit audit = new AiKnowledgeAudit();
        audit.setAuditId(1L);
        audit.setKnowledgeId(2L);
        audit.setTitle("劳动合同解除情形");
        audit.setPublishVersion(1);
        audit.setAction("APPROVE");
        audit.setFromStatus("1");
        audit.setToStatus("2");
        audit.setAuditOpinion("内容准确，同意发布");
        audit.setAuditor("审核员甲");
        audit.setAuditTime(auditTime);

        assertEquals(1L, audit.getAuditId());
        assertEquals(2L, audit.getKnowledgeId());
        assertEquals("劳动合同解除情形", audit.getTitle());
        assertEquals(1, audit.getPublishVersion());
        assertEquals("APPROVE", audit.getAction());
        assertEquals("1", audit.getFromStatus());
        assertEquals("2", audit.getToStatus());
        assertEquals("内容准确，同意发布", audit.getAuditOpinion());
        assertEquals("审核员甲", audit.getAuditor());
        assertEquals(auditTime, audit.getAuditTime());
    }

    @Test
    void 默认值为空()
    {
        AiKnowledgeAudit audit = new AiKnowledgeAudit();

        assertNull(audit.getAuditId());
        assertNull(audit.getKnowledgeId());
        assertNull(audit.getTitle());
        assertNull(audit.getAction());
        assertNull(audit.getAuditor());
        assertNull(audit.getAuditTime());
    }

    @Test
    void 字符串输出包含标题与动作()
    {
        AiKnowledgeAudit audit = new AiKnowledgeAudit();
        audit.setTitle("遗产继承顺序");
        audit.setAction("PUBLISH");

        String text = audit.toString();
        assertTrue(text.contains("遗产继承顺序"));
        assertTrue(text.contains("PUBLISH"));
    }
}
