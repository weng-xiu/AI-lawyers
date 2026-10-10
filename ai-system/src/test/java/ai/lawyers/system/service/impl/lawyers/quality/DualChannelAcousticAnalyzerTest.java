package ai.lawyers.system.service.impl.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.service.impl.lawyers.quality.AcousticFeatureAnalyzer.DualChannelResult;
import ai.lawyers.system.service.impl.lawyers.quality.AcousticFeatureAnalyzer.Overlap;
import ai.lawyers.system.service.impl.lawyers.quality.AcousticFeatureAnalyzer.RoleFeatures;
import ai.lawyers.system.service.impl.lawyers.quality.QualityRuleEngine.RuleViolation;

/**
 * P2-7：双声道分角色声学分析单测。合成两路单声道 PCM（各段 200Hz 正弦有声 + 静默），
 * 校验抢话/重叠区间判定（≥300ms）、分角色语速、无重叠不误判与端到端分轨 WAV 链路。
 *
 * @author ai-lawyers
 */
class DualChannelAcousticAnalyzerTest
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
        setField("overlapMinMs", 300L);
        setField("overlapPenalty", 5);
        setField("overlapMaxPenalty", 20);
    }

    @Test
    void analyzeTwoChannel_overlapDetected_asInterruption()
    {
        // A：帧 0-99 有声（约 2s），其后静默；B：帧 50-149 有声。
        // VAD 校正后 A≈[0,99) 帧、B≈[49,149) 帧 → 重叠 [49,99) ≈ 1000ms（≥300ms 判 1 处抢话）
        int frames = 200;
        byte[] a = pcm(frames);
        byte[] b = pcm(frames);
        fillTone(a, 0, 100);
        fillTone(b, 50, 100);

        DualChannelResult r = analyzer.analyzeTwoChannel(RATE, a, repeatHan(6), b, repeatHan(6));

        assertEquals(1, r.getOverlaps().size());
        Overlap ov = r.getOverlaps().get(0);
        assertTrue(ov.getDurationMs() >= 900 && ov.getDurationMs() <= 1080,
                "overlapMs=" + ov.getDurationMs());
        assertTrue(hasKeyword(r.getViolations(), "抢话"));
        // 抢话 5 分；两角色各 6 字/约 2s≈180 字/分，无静默/语速违规 → 合计 5
        assertEquals(5, r.getServiceNormPenalty());
        // 分角色语速各自算出且正常
        assertTrue(r.getRoleA().getResult().getSpeechRate() > 150);
        assertTrue(r.getRoleB().getResult().getSpeechRate() > 150);
        assertEquals("坐席", r.getRoleA().getRole());
        assertEquals("客户", r.getRoleB().getRole());
    }

    @Test
    void analyzeTwoChannel_noOverlap_noInterruption()
    {
        // A：帧 0-99 有声；B：帧 120-209 有声（间隔 ≥20 帧闭合，校正区间不相交）
        int frames = 240;
        byte[] a = pcm(frames);
        byte[] b = pcm(frames);
        fillTone(a, 0, 100);
        fillTone(b, 120, 90);

        DualChannelResult r = analyzer.analyzeTwoChannel(RATE, a, null, b, null);

        assertTrue(r.getOverlaps().isEmpty());
        assertFalse(hasKeyword(r.getViolations(), "抢话"));
        assertEquals(0, r.getServiceNormPenalty());
        // 两路各一个语音段
        assertEquals(1, r.getRoleA().getSpeechIntervalsMs().size());
        assertEquals(1, r.getRoleB().getSpeechIntervalsMs().size());
    }

    @Test
    void analyzeTwoChannel_overlapShorterThanThreshold_ignored()
    {
        // A：帧 0-99 有声；B：帧 90-149 有声 → 边缘重叠约 200ms（<300ms 阈值），不判抢话
        int frames = 180;
        byte[] a = pcm(frames);
        byte[] b = pcm(frames);
        fillTone(a, 0, 100);
        fillTone(b, 90, 60);

        DualChannelResult r = analyzer.analyzeTwoChannel(RATE, a, null, b, null);

        assertTrue(r.getOverlaps().isEmpty(), "overlaps=" + r.getOverlaps().size());
    }

    @Test
    void analyzeTwoChannel_customRoles_propagated()
    {
        int frames = 150;
        byte[] a = pcm(frames);
        byte[] b = pcm(frames);
        fillTone(a, 0, 100);
        fillTone(b, 20, 100);

        DualChannelResult r = analyzer.analyzeTwoChannel(RATE, a, null, b, null, "来电人", "值班律师");

        assertEquals("来电人", r.getRoleA().getRole());
        assertEquals("值班律师", r.getRoleB().getRole());
        RuleViolation v = r.getViolations().stream()
                .filter(x -> x.getKeyword().equals("抢话")).findFirst().orElse(null);
        assertTrue(v != null && v.getReason().contains("来电人/值班律师"));
    }

    @Test
    void analyzeTwoChannel_twoLegWav_endToEnd()
    {
        // uuid_record 双腿分轨：两条单声道 WAV → decodeTwoLegs 对齐 → 抢话检测
        byte[] trackA = pcm(160);
        byte[] trackB = pcm(160);
        fillTone(trackA, 0, 100);
        fillTone(trackB, 40, 100);
        // legB 物理上更短（120 帧），解码时补零对齐
        byte[] trackBShort = new byte[120 * FRAME_BYTES];
        System.arraycopy(trackB, 0, trackBShort, 0, trackBShort.length);
        byte[] wavA = WavPcmDecoderTestSupport.monoWav(RATE, trackA);
        byte[] wavB = WavPcmDecoderTestSupport.monoWav(RATE, trackBShort);

        WavPcmDecoder.ChannelPcm ch = WavPcmDecoder.decodeTwoLegs(wavA, wavB);
        DualChannelResult r = analyzer.analyzeTwoChannel(ch.getSampleRate(),
                ch.getChannel(0), null, ch.getChannel(1), null);

        // B 有声区间从帧 40 起、约 2s，与 A[0,~99) 重叠约 59 帧≈1180ms → 判 1 处
        assertEquals(1, r.getOverlaps().size());
        assertTrue(hasKeyword(r.getViolations(), "抢话"));
    }

    private static boolean hasKeyword(List<RuleViolation> vs, String keyword)
    {
        return vs.stream().anyMatch(v -> v.getKeyword().equals(keyword));
    }

    private static byte[] pcm(int frames)
    {
        return new byte[frames * FRAME_BYTES];
    }

    /** 从第 fromFrame 帧起填充 frameCount 帧 200Hz 正弦（满幅 0.2），其余为零（静默） */
    private static void fillTone(byte[] pcm, int fromFrame, int frameCount)
    {
        int startSample = fromFrame * (FRAME_BYTES / 2);
        int samples = frameCount * (FRAME_BYTES / 2);
        for (int i = 0; i < samples; i++)
        {
            double t = 2d * Math.PI * 200d * i / RATE;
            short s = (short) (Math.sin(t) * 7000);
            int off = (startSample + i) * 2;
            pcm[off] = (byte) (s & 0xFF);
            pcm[off + 1] = (byte) ((s >> 8) & 0xFF);
        }
    }

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

    /** 测试内最小 WAV 封装（PCM/16bit/单声道） */
    static final class WavPcmDecoderTestSupport
    {
        static byte[] monoWav(int rate, byte[] pcm)
        {
            int dataBytes = pcm.length;
            byte[] wav = new byte[44 + dataBytes];
            putAscii(wav, 0, "RIFF");
            putU32(wav, 4, 36 + dataBytes);
            putAscii(wav, 8, "WAVE");
            putAscii(wav, 12, "fmt ");
            putU32(wav, 16, 16);
            putU16(wav, 20, 1);
            putU16(wav, 22, 1);
            putU32(wav, 24, rate);
            putU32(wav, 28, rate * 2);
            putU16(wav, 32, 2);
            putU16(wav, 34, 16);
            putAscii(wav, 36, "data");
            putU32(wav, 40, dataBytes);
            System.arraycopy(pcm, 0, wav, 44, dataBytes);
            return wav;
        }

        private static void putAscii(byte[] b, int off, String s)
        {
            for (int i = 0; i < s.length(); i++)
            {
                b[off + i] = (byte) s.charAt(i);
            }
        }

        private static void putU16(byte[] b, int off, int v)
        {
            b[off] = (byte) (v & 0xFF);
            b[off + 1] = (byte) ((v >> 8) & 0xFF);
        }

        private static void putU32(byte[] b, int off, int v)
        {
            b[off] = (byte) (v & 0xFF);
            b[off + 1] = (byte) ((v >> 8) & 0xFF);
            b[off + 2] = (byte) ((v >> 16) & 0xFF);
            b[off + 3] = (byte) ((v >> 24) & 0xFF);
        }
    }
}
