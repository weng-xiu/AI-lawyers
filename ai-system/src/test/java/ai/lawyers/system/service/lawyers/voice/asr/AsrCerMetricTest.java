package ai.lawyers.system.service.lawyers.voice.asr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link AsrCerMetric} 单测：归一化、字符级编辑距离与 CER 边界，纯 JDK 无外部依赖。
 */
class AsrCerMetricTest
{
    private AsrCerMetric metric;

    @BeforeEach
    void setUp()
    {
        metric = new AsrCerMetric();
    }

    @Test
    void score_identicalText_cerZero()
    {
        AsrCerMetric.AsrCerResult r = metric.score("老板拖欠工资", "老板拖欠工资");
        assertEquals(0, r.getEditDistance());
        assertEquals(6, r.getRefLength());
        assertEquals(0d, r.getCer(), 0.0001);
    }

    @Test
    void score_oneSubstitution_distanceOne()
    {
        // 合动→合同，替换 1 字，CER=1/6
        AsrCerMetric.AsrCerResult r = metric.score("劳动合同", "劳动合动");
        assertEquals(1, r.getEditDistance());
        assertEquals(4, r.getRefLength());
        assertEquals(0.25d, r.getCer(), 0.0001);
    }

    @Test
    void score_oneInsertion_distanceOne()
    {
        // 假设多 1 字：插入 1 次
        AsrCerMetric.AsrCerResult r = metric.score("拖欠工资", "拖欠了工资");
        assertEquals(1, r.getEditDistance());
        assertEquals(4, r.getRefLength());
        assertEquals(0.25d, r.getCer(), 0.0001);
    }

    @Test
    void score_oneDeletion_distanceOne()
    {
        // 假设少 1 字：删除 1 次
        AsrCerMetric.AsrCerResult r = metric.score("拖欠工资啊", "拖欠工资");
        assertEquals(1, r.getEditDistance());
        assertEquals(5, r.getRefLength());
        assertEquals(0.2d, r.getCer(), 0.0001);
    }

    @Test
    void normalize_punctuationWhitespaceCaseWidth_ignored()
    {
        // 标点、空白、英文大小写、全角字母差异均被归一化抹平 → CER=0
        String ref = "Hello，工资。";
        String hyp = "hello  工资!　";
        AsrCerMetric.AsrCerResult r = metric.score(ref, hyp);
        assertEquals(0, r.getEditDistance());
        assertEquals(0d, r.getCer(), 0.0001);
        // 直接验证归一化结果：全角Ａ NFKC→半角 a 再小写，去标点空白
        assertEquals("a工资", metric.normalize("Ａ， 工资。"));
    }

    @Test
    void score_bothEmpty_cerZero()
    {
        AsrCerMetric.AsrCerResult r = metric.score("", "");
        assertEquals(0, r.getRefLength());
        assertEquals(0d, r.getCer(), 0.0001);
    }

    @Test
    void score_emptyReferenceButNonEmptyHypothesis_cerOne()
    {
        // 参考为空、转写非空：给有限值 1.0 而非除零
        AsrCerMetric.AsrCerResult r = metric.score("", "多余识别");
        assertEquals(0, r.getRefLength());
        assertEquals(1.0d, r.getCer(), 0.0001);
    }

    @Test
    void score_heavyInsertion_cerGreaterThanOne_notThrowing()
    {
        // 参考 2 字，假设插入大量内容，CER 可大于 1 且不裁剪、不外抛
        AsrCerMetric.AsrCerResult r = metric.score("工资", "工资拖欠了三个月还没给怎么办呢");
        assertTrue(r.getCer() > 1.0d);
    }

    @Test
    void score_nullText_treatedAsEmpty()
    {
        assertEquals(0d, metric.score(null, null).getCer(), 0.0001);
        assertEquals(1.0d, metric.score(null, "识别").getCer(), 0.0001);
    }

    @Test
    void score_roundThreePrecision()
    {
        // 参考 7 字、替换 1 字：1/7=0.142857→0.143
        AsrCerMetric.AsrCerResult r = metric.score("一二三四五六七", "一二三四五六八");
        assertEquals(0.143d, r.getCer(), 0.0001);
    }
}
