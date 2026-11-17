package ai.lawyers.common.utils.uuid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * 序列生成测试：长度、机器标识、递增补零与循环重置。
 *
 * @author ai-lawyers
 */
class SeqTest
{
    @Test
    void getId_默认通用序列格式正确()
    {
        String id = Seq.getId();

        // yyMMddHHmmss(14) + 机器标识(1) + 3 位序列
        assertEquals(18, id.length());
        assertTrue(id.matches("\\d{14}A\\d{3}"));
        assertNotEquals(id, Seq.getId());
    }

    @Test
    void getId_上传序列独立递增()
    {
        String upload = Seq.getId(Seq.uploadSeqType);

        assertEquals(18, upload.length());
        assertTrue(upload.matches("\\d{14}A\\d{3}"));
    }

    @Test
    void getId_指定序列器与长度时左补零()
    {
        AtomicInteger seq = new AtomicInteger(7);

        assertEquals(Seq.getId(seq, 3).substring(15), "007");
        assertEquals("008", Seq.getId(seq, 3).substring(15));
    }

    @Test
    void getId_达到上限后重置为一()
    {
        AtomicInteger seq = new AtomicInteger(999);

        assertEquals("999", Seq.getId(seq, 3).substring(15));
        assertEquals("001", Seq.getId(seq, 3).substring(15));
        assertEquals("002", Seq.getId(seq, 3).substring(15));
    }
}
