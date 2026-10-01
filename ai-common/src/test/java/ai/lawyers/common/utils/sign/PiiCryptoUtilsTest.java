package ai.lawyers.common.utils.sign;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-G1-b（V2.55）：PiiCryptoUtils 测试——SM4-GCM 字段加密往返与幂等、
 * 存量明文三态兼容、防篡改、盲索引确定性与密钥参与性。
 */
class PiiCryptoUtilsTest
{
    private static final String PREFIX = "encp2:";

    @Test
    void encrypt_decryptRoundtrip_randomIv()
    {
        String phone = "13812345678";
        String stored = PiiCryptoUtils.encrypt(phone);
        assertTrue(stored.startsWith(PREFIX), "PII 密文应为 encp2: 前缀，实际: " + stored.substring(0, 6));
        assertNotEquals(phone, stored);
        // encp2:(6) + Base64(version 1 + IV 12 + 密文 11 + tag 16 = 40B → 56 字符) = 62
        assertEquals(62, stored.length(), "手机号密文长度应可预估（DDL 列宽依据）");
        assertEquals(phone, PiiCryptoUtils.decrypt(stored), "SM4-GCM 解密应还原明文");
        // 随机 IV：同明文两次密文不同
        assertNotEquals(stored, PiiCryptoUtils.encrypt(phone), "随机 IV 下两次密文应不同");
    }

    @Test
    void encrypt_idCardRoundtrip()
    {
        String idCard = "110101199003078515";
        String stored = PiiCryptoUtils.encrypt(idCard);
        assertTrue(PiiCryptoUtils.isEncrypted(stored));
        assertFalse(PiiCryptoUtils.isEncrypted(idCard), "明文不应被判定为密文");
        assertEquals(idCard, PiiCryptoUtils.decrypt(stored));
    }

    @Test
    void decrypt_plainStoredValue_returnedAsIs()
    {
        // 存量明文（无前缀）原样返回，迁移窗口平滑兼容
        assertEquals("13800000000", PiiCryptoUtils.decrypt("13800000000"));
        assertEquals("", PiiCryptoUtils.decrypt(""));
        assertNull(PiiCryptoUtils.decrypt(null));
    }

    @Test
    void encrypt_idempotent()
    {
        String stored = PiiCryptoUtils.encrypt("13812345678");
        assertEquals(stored, PiiCryptoUtils.encrypt(stored), "已密文不应被二次加密");
    }

    @Test
    void decrypt_tamperedCipher_throws()
    {
        String stored = PiiCryptoUtils.encrypt("13812345678");
        byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        raw[raw.length - 1] ^= 0x01; // 破坏 GCM 认证标签
        String tampered = PREFIX + Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> PiiCryptoUtils.decrypt(tampered),
                "GCM 认证加密下密文被篡改必须抛异常");
    }

    @Test
    void blindIndex_deterministicAndDistinct()
    {
        String a1 = PiiCryptoUtils.blindIndex("13812345678");
        String a2 = PiiCryptoUtils.blindIndex("13812345678");
        assertEquals(a1, a2, "盲索引必须确定（等值查询与唯一索引防重依赖）");
        assertEquals(32, a1.length(), "HMAC-SM3 截断 128 位应为 hex 32 字符");
        assertTrue(a1.matches("[0-9a-f]{32}"), "盲索引应为小写 hex");
        assertNotEquals(a1, PiiCryptoUtils.blindIndex("13812345679"), "不同明文盲索引应不同");
        assertNotEquals(a1, PiiCryptoUtils.blindIndex("13812345678 "), "输入差异（尾随空格）应生效");
        assertNull(PiiCryptoUtils.blindIndex(null));
        assertNull(PiiCryptoUtils.blindIndex(""));
    }

    @Test
    void blindIndex_keyed_notBareSm3()
    {
        String plain = "13812345678";
        // 密钥必须参与运算：盲索引 != 裸 SM3(明文)，手机号小明文空间防枚举/彩虹表反推
        assertNotEquals(SmCryptoUtils.sm3Hex(plain), PiiCryptoUtils.blindIndex(plain));
    }

    // --------------------------------------------- G1-b2 位置分片盲 token（V2.56）

    @Test
    void searchToken_positionSensitiveAndDeterministic()
    {
        String t0 = PiiCryptoUtils.searchToken(0, "8");
        String t1 = PiiCryptoUtils.searchToken(1, "8");
        assertNotEquals(t0, t1, "位置必须参与运算：同字符不同位置 token 应不同");
        assertEquals(t0, PiiCryptoUtils.searchToken(0, "8"), "位置 token 应确定性输出");
        assertEquals(32, t0.length());
        assertTrue(t0.matches("[0-9a-f]{32}"));
    }

    @Test
    void phoneTokens_onePerPosition()
    {
        List<String> tokens = PiiCryptoUtils.phoneTokens("13812345678");
        assertEquals(11, tokens.size(), "11 位号码应生成 11 个位置 token");
        assertEquals(tokens.size(), tokens.stream().distinct().count(), "各位置 token 应互不相同");
        assertTrue(PiiCryptoUtils.phoneTokens("").isEmpty());
        assertTrue(PiiCryptoUtils.phoneTokens(null).isEmpty());
    }

    @Test
    void tokenGroups_structureByKeywordLength()
    {
        List<List<String>> one = PiiCryptoUtils.tokenGroups("8");
        assertEquals(11, one.size(), "1 位关键词在 11 位号码上有 11 个起始位置");
        assertTrue(one.stream().allMatch(g -> g.size() == 1), "每组 1 个 token");

        List<List<String>> three = PiiCryptoUtils.tokenGroups("123");
        assertEquals(9, three.size(), "3 位关键词应有 9 个分组");
        assertTrue(three.stream().allMatch(g -> g.size() == 3), "每组 3 个 token");

        List<List<String>> full = PiiCryptoUtils.tokenGroups("13812345678");
        assertEquals(1, full.size(), "11 位关键词仅有 1 个起始位置");
        assertEquals(11, full.get(0).size());

        assertTrue(PiiCryptoUtils.tokenGroups("123456789012").isEmpty(), "超长关键词无分组（无匹配）");
        assertTrue(PiiCryptoUtils.tokenGroups("").isEmpty());
        assertTrue(PiiCryptoUtils.tokenGroups(null).isEmpty());
    }

    @Test
    void tokenGroups_matchSemanticsLike()
    {
        String phone = "13812345678";
        List<String> stored = PiiCryptoUtils.phoneTokens(phone);
        // 真实子串（含前/中/后缀）：至少一个分组的全部 token 都在号码 token 中（SQL 组内 AND 命中）
        for (String kw : Arrays.asList("138", "3456", "5678", "13812345678"))
        {
            boolean hit = PiiCryptoUtils.tokenGroups(kw).stream()
                    .anyMatch(stored::containsAll);
            assertTrue(hit, "号码包含关键词 " + kw + "，应存在命中分组");
        }
        // 不相关号码：无任何分组完全命中
        boolean hit = PiiCryptoUtils.tokenGroups("999").stream().anyMatch(stored::containsAll);
        assertFalse(hit, "号码不含 999，不应命中");
    }
}
