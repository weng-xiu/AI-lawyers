package ai.lawyers.system.service.lawyers.voice.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Copilot 法条推荐项测试：溯源字段透传与空载荷。
 *
 * @author ai-lawyers
 */
class CopilotLawTest
{
    @Test
    void 构造函数保存法条推荐字段()
    {
        CopilotLaw law = new CopilotLaw(88L, "民法典", "第五百七十七条", "合同编");

        assertEquals(88L, law.getChunkId());
        assertEquals("民法典", law.getTitle());
        assertEquals("第五百七十七条", law.getLawArticle());
        assertEquals("合同编", law.getSource());
    }

    @Test
    void 允许空溯源字段()
    {
        CopilotLaw law = new CopilotLaw(null, null, null, null);

        assertNull(law.getChunkId());
        assertNull(law.getTitle());
        assertNull(law.getLawArticle());
        assertNull(law.getSource());
    }
}
