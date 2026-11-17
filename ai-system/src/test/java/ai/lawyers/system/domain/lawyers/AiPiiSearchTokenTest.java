package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * PII 模糊检索盲 token 实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiPiiSearchTokenTest
{
    @Test
    void 属性读写()
    {
        AiPiiSearchToken token = new AiPiiSearchToken();
        token.setTokenId(1L);
        token.setOwnerType("CALL_RECORD");
        token.setOwnerId(2L);
        token.setTokenValue("a1b2c3d4e5f60718293a4b5c6d7e8f90");

        assertEquals(1L, token.getTokenId());
        assertEquals("CALL_RECORD", token.getOwnerType());
        assertEquals(2L, token.getOwnerId());
        assertEquals("a1b2c3d4e5f60718293a4b5c6d7e8f90", token.getTokenValue());
    }

    @Test
    void 默认值为空()
    {
        AiPiiSearchToken token = new AiPiiSearchToken();

        assertNull(token.getTokenId());
        assertNull(token.getOwnerType());
        assertNull(token.getOwnerId());
        assertNull(token.getTokenValue());
    }
}
