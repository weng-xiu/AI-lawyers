package ai.lawyers.system.service.lawyers.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * F8 问句文本处理单测：敏感信息脱敏、截断、归一化分组与哈希稳定性。
 *
 * @author ai-lawyers
 */
class KnowledgeQueryTextUtilsTest
{
    @Test
    void masksPhoneIdCardBankCardAndEmail()
    {
        String phone = KnowledgeQueryTextUtils.maskForStore("我的电话是13800138000，请回电");
        assertTrue(phone.contains("138****8000"));
        assertFalse(phone.contains("13800138000"));

        String idCard = KnowledgeQueryTextUtils.maskForStore("身份证110101199001011234");
        assertFalse(idCard.contains("110101199001011234"));
        assertTrue(idCard.contains("1234"));

        String bank = KnowledgeQueryTextUtils.maskForStore("卡号6222021234567890123");
        assertFalse(bank.contains("6222021234567890123"));

        String email = KnowledgeQueryTextUtils.maskForStore("联系邮箱zhangsan@example.com");
        assertEquals("联系邮箱z***@example.com", email);
    }

    @Test
    void truncatesStoredTextToColumnWidth()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++)
        {
            sb.append('问');
        }
        assertEquals(KnowledgeQueryTextUtils.MAX_STORE_LENGTH,
                KnowledgeQueryTextUtils.maskForStore(sb.toString()).length());
    }

    @Test
    void normalizeRemovesWhitespaceAndPunctuation()
    {
        assertEquals("劳动合同怎么办",
                KnowledgeQueryTextUtils.normalize("  劳动 合同？怎么办！"));
        assertEquals("", KnowledgeQueryTextUtils.normalize("  ，。！  "));
    }

    @Test
    void hashIsStableAcrossPunctuationAndWhitespace()
    {
        String a = KnowledgeQueryTextUtils.hash("劳动 合同？怎么办");
        String b = KnowledgeQueryTextUtils.hash("劳动合同怎么办！");
        assertEquals(a, b);
        assertEquals(32, a.length());

        assertNotEquals(a, KnowledgeQueryTextUtils.hash("工伤认定怎么申请"));
        assertEquals("", KnowledgeQueryTextUtils.hash("  "));
    }

    @Test
    void blankInputIsHandled()
    {
        assertEquals("", KnowledgeQueryTextUtils.maskForStore(null));
        assertTrue(KnowledgeQueryTextUtils.isBlank("   "));
        assertFalse(KnowledgeQueryTextUtils.isBlank("问题"));
    }
}
