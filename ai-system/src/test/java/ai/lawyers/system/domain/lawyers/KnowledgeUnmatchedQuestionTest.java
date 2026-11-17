package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 未命中问句排行行测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class KnowledgeUnmatchedQuestionTest
{
    @Test
    void 属性读写()
    {
        Date first = new Date(1000L);
        Date last = new Date(2000L);
        KnowledgeUnmatchedQuestion question = new KnowledgeUnmatchedQuestion();
        question.setQueryHash("hash-001");
        question.setQueryText("企业拖欠工资怎么办");
        question.setAskCount(12L);
        question.setFirstTime(first);
        question.setLastTime(last);
        question.setConverted(1);

        assertEquals("hash-001", question.getQueryHash());
        assertEquals("企业拖欠工资怎么办", question.getQueryText());
        assertEquals(12L, question.getAskCount());
        assertEquals(first, question.getFirstTime());
        assertEquals(last, question.getLastTime());
        assertEquals(1, question.getConverted());
    }

    @Test
    void 默认值为空()
    {
        KnowledgeUnmatchedQuestion question = new KnowledgeUnmatchedQuestion();

        assertNull(question.getQueryHash());
        assertNull(question.getQueryText());
        assertNull(question.getAskCount());
        assertNull(question.getConverted());
    }
}
