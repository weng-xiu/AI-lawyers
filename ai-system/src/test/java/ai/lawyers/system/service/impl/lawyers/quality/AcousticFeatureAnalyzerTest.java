package ai.lawyers.system.service.impl.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.service.impl.lawyers.quality.AcousticFeatureAnalyzer.AcousticResult;

/**
 * P1-7：声学特征分析器单测。合成“有声段 + 长静默 + 有声段”PCM，
 * 校验静默检测、有声时长、整体语速与违规扣分。
 *
 * @author ai-lawyers
 */
class AcousticFeatureAnalyzerTest
{
    private static final int RATE = 16000;
    private static final int FRAME_BYTES = RATE * 20 / 1000 * 2;

    private AcousticFeatureAnalyzer analyzer;

    @BeforeEach
    void setUp() throws Exception
    {
        analyzer = new AcousticFeatureAnalyzer();
        setField("longSilenceMs", 3000L);
        setField("longSilencePenalty", 10);
        setField("longSilenceMaxPenalty", 30);
        setField("rateMin", 80);
        setField("rateMax", 400);
        setField("ratePenalty", 10);
    }

    @Test
    void analyze_longGap_detectedAndPenalized()
    {
        // 三段各 100 帧（2s）有声 + 结尾 25 帧静默（使末段正常闭合）：
        // 前两段间隔 40 帧（800ms，校正后 460ms 不触发），后两段间隔 200 帧（校正后≈3980ms 触发）
        int frames = 100 + 40 + 100 + 200 + 100 + 25;
        byte[] pcm = new byte[frames * FRAME_BYTES];
        fillTone(pcm, 0, 100 * FRAME_BYTES);
        fillTone(pcm, 140 * FRAME_BYTES, 100 * FRAME_BYTES);
        fillTone(pcm, 440 * FRAME_BYTES, 100 * FRAME_BYTES);

        AcousticResult r = analyzer.analyze(RATE, pcm, repeatHan(200));

        assertTrue(hasKeyword(r, "长静默"));
        // 长静默 -10；200 字/6s=2000 字/分（>400）语速过快再 -10 → 合计 20
        assertEquals(20, r.getServiceNormPenalty());
        // 校正后最长静默 ≈ 3980ms（4000 - 340）
        assertTrue(r.getMaxSilenceMs() >= 3900 && r.getMaxSilenceMs() <= 4000,
                "maxSilenceMs=" + r.getMaxSilenceMs());
        // 有声时长：三段闭合，各 span=117、校正后 100 帧 → 300 帧 ×20ms = 6000ms
        assertTrue(r.getVoicedMs() >= 5900 && r.getVoicedMs() <= 6100,
                "voicedMs=" + r.getVoicedMs());
        assertTrue(r.getSpeechRate() > 0);
    }

    @Test
    void analyze_fastRate_flagged()
    {
        // 100 帧（2s）有声 + 25 帧尾静默：闭合段 span=117、去17帧校正 → 100帧=2000ms；
        // 1200 汉字/2s = 36000 字/分（>400）
        byte[] pcm = new byte[(100 + 25) * FRAME_BYTES];
        fillTone(pcm, 0, 100 * FRAME_BYTES);

        AcousticResult r = analyzer.analyze(RATE, pcm, repeatHan(1200));

        assertTrue(hasKeyword(r, "语速过快"), "violations=" + keywords(r));
        assertEquals(10, r.getServiceNormPenalty());
        assertTrue(r.getSpeechRate() > 400, "speechRate=" + r.getSpeechRate());
    }

    @Test
    void analyze_normalCall_noAcousticViolation()
    {
        // 100 帧（2s）有声 + 25 帧尾静默（正常闭合）：span=117、去17帧校正 → 100帧=2000ms；
        // 6 汉字/2s = 180 字/分（落在 80~400，无声学违规）
        byte[] pcm = new byte[(100 + 25) * FRAME_BYTES];
        fillTone(pcm, 0, 100 * FRAME_BYTES);
        AcousticResult r = analyzer.analyze(RATE, pcm, repeatHan(6));

        for (var v : r.getViolations())
        {
            assertFalse("长静默".equals(v.getKeyword()));
        }
        assertTrue(r.getSpeechRate() > 150 && r.getSpeechRate() < 300,
                "speechRate=" + r.getSpeechRate());
    }

    @Test
    void analyze_emptyPcm_emptyResult()
    {
        AcousticResult r = analyzer.analyze(RATE, new byte[0], "你好");
        assertTrue(r.getViolations().isEmpty());
        assertEquals(0, r.getServiceNormPenalty());
    }

    private boolean hasKeyword(AcousticResult r, String keyword)
    {
        return r.getViolations().stream().anyMatch(v -> v.getKeyword().equals(keyword));
    }

    private static String keywords(AcousticResult r)
    {
        List<String> ks = new java.util.ArrayList<>();
        r.getViolations().forEach(v -> ks.add(v.getKeyword()));
        return ks.toString();
    }

    /** 用 200Hz 正弦（满幅 0.2）填充，确保 RMS 过阈、ZCR≈0.025 落在区间 */
    private static void fillTone(byte[] pcm, int offset, int len)
    {
        for (int i = 0; i < len / 2; i++)
        {
            double t = 2d * Math.PI * 200d * i / RATE;
            short s = (short) (Math.sin(t) * 7000);
            int off = offset + i * 2;
            pcm[off] = (byte) (s & 0xFF);
            pcm[off + 1] = (byte) ((s >> 8) & 0xFF);
        }
    }

    /** 重复汉字“法”count 次（避免标点干扰字数） */
    private static String repeatHan(int count)
    {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++)
        {
            sb.append('法');
        }
        return sb.toString();
    }

    private void setField(String name, Object value) throws Exception
    {
        Field f = AcousticFeatureAnalyzer.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(analyzer, value);
    }
}
