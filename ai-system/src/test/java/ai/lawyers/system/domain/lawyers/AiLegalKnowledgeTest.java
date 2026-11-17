package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 法律知识库实体测试：属性读写、审核字段与字符串输出。
 *
 * @author ai-lawyers
 */
class AiLegalKnowledgeTest
{
    @Test
    void 属性读写()
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setKnowledgeId(1L);
        knowledge.setTitle("劳动合同解除情形");
        knowledge.setCategoryId(2L);
        knowledge.setContent("用人单位与劳动者协商一致可以解除合同");
        knowledge.setLawArticle("劳动合同法第三十六条");
        knowledge.setSource("国家法律法规数据库");
        knowledge.setKeywords("劳动合同,解除");
        knowledge.setViewCount(100L);
        knowledge.setStatus("0");

        assertEquals(1L, knowledge.getKnowledgeId());
        assertEquals("劳动合同解除情形", knowledge.getTitle());
        assertEquals(2L, knowledge.getCategoryId());
        assertEquals("用人单位与劳动者协商一致可以解除合同", knowledge.getContent());
        assertEquals("劳动合同法第三十六条", knowledge.getLawArticle());
        assertEquals("国家法律法规数据库", knowledge.getSource());
        assertEquals("劳动合同,解除", knowledge.getKeywords());
        assertEquals(100L, knowledge.getViewCount());
        assertEquals("0", knowledge.getStatus());
    }

    @Test
    void 审核与溯源字段读写()
    {
        Date auditTime = new Date();
        Date effectiveDate = new Date();
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setAuditStatus("1");
        knowledge.setAuditBy("审核员");
        knowledge.setAuditTime(auditTime);
        knowledge.setAuditRemark("内容准确");
        knowledge.setLawName("中华人民共和国劳动合同法");
        knowledge.setArticleNo("第三十六条");
        knowledge.setIssuingAuthority("全国人大常委会");
        knowledge.setEffectiveDate(effectiveDate);
        knowledge.setValidStatus("1");
        knowledge.setSourceUrl("https://flk.npc.gov.cn");
        knowledge.setPublishVersion(3);

        assertEquals("1", knowledge.getAuditStatus());
        assertEquals("审核员", knowledge.getAuditBy());
        assertEquals(auditTime, knowledge.getAuditTime());
        assertEquals("内容准确", knowledge.getAuditRemark());
        assertEquals("中华人民共和国劳动合同法", knowledge.getLawName());
        assertEquals("第三十六条", knowledge.getArticleNo());
        assertEquals("全国人大常委会", knowledge.getIssuingAuthority());
        assertEquals(effectiveDate, knowledge.getEffectiveDate());
        assertEquals("1", knowledge.getValidStatus());
        assertEquals("https://flk.npc.gov.cn", knowledge.getSourceUrl());
        assertEquals(3, knowledge.getPublishVersion());
    }

    @Test
    void 默认值为空()
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();

        assertNull(knowledge.getKnowledgeId());
        assertNull(knowledge.getTitle());
        assertNull(knowledge.getContent());
        assertNull(knowledge.getViewCount());
        assertNull(knowledge.getAuditTime());
        assertNull(knowledge.getPublishVersion());
    }

    @Test
    void 字符串输出包含标题与内容()
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setTitle("遗产继承顺序");
        knowledge.setContent("第一顺序为配偶、子女、父母");

        String text = knowledge.toString();
        assertTrue(text.contains("遗产继承顺序"));
        assertTrue(text.contains("第一顺序为配偶、子女、父母"));
    }
}
