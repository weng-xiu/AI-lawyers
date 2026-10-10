package ai.lawyers.system.service.lawyers.voice.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P2-15：通话内说话人分离单测（纯内存，不跨通话建档）。
 *
 * <p>测试环境无 ONNX Runtime/模型，沿用 {@link SpeakerClusteringServiceTest} 的伪提取器模式
 * （构造走降级、覆写可用性与提取方法），按"先声道0后声道1"的提嵌入调用顺序回送预设向量，
 * 验证：双声道合成 WAV → 分通道 VAD 切段 → 段向量 → 通话内聚类 → 簇角色多数表决 →
 * 段角色/置信度映射的完整编排；并校验单声道、模型缺席降级，第三人（OTHER）与双腿分轨入口。</p>
 *
 * @author ai-lawyers
 */
class SpeakerDiarizationServiceTest
{
    private static final int RATE = 16000;
    private static final int FRAME_BYTES = RATE * 20 / 1000 * 2;

    /** 按提嵌入调用顺序（先声道0全部段、后声道1全部段）回送预设向量的伪提取器 */
    private static final class StubExtractor extends SpeakerEmbeddingExtractor
    {
        private final List<float[]> byCall;
        private int call;

        StubExtractor(List<float[]> byCall)
        {
            super(RATE, "models/__no_such__.onnx");
            this.byCall = byCall;
        }

        @Override
        public boolean isModelAvailable()
        {
            return true;
        }

        @Override
        public float[] extract(byte[] pcm)
        {
            return call < byCall.size() ? byCall.get(call++) : null;
        }
    }

    private static float[] unit(float x, float y)
    {
        double n = Math.sqrt(x * x + y * y);
        return new float[] { (float) (x / n), (float) (y / n) };
    }

    private SpeakerDiarizationService service(List<float[]> embeddings)
    {
        return new SpeakerDiarizationService(new StubExtractor(embeddings), new SpeakerClusterer());
    }

    /**
     * 合成双声道 WAV：plan[ch] 为该声道的有声/静默布局（与 SpeakerClusteringServiceTest.wav 同构），
     * 两声道帧数取齐（不足补静默帧）。
     */
    private static byte[] stereoWav(List<List<Integer>> plan)
    {
        int totalFrames = 0;
        for (List<Integer> p : plan)
        {
            totalFrames = Math.max(totalFrames, frameCount(p));
        }
        short[] left = new short[totalFrames * (FRAME_BYTES / 2)];
        short[] right = new short[totalFrames * (FRAME_BYTES / 2)];
        if (!plan.isEmpty())
        {
            fillChannel(left, plan.get(0));
        }
        if (plan.size() > 1)
        {
            fillChannel(right, plan.get(1));
        }
        return writeStereoWav(left, right);
    }

    /** voiced[i] 个有声帧、silence[i] 个静默帧交替 */
    private static int frameCount(List<Integer> pattern)
    {
        // pattern = [v0,s0,v1,s1,...]，偶数下标有声、奇数静默
        int total = 0;
        for (int v : pattern)
        {
            total += v;
        }
        return total;
    }

    private static void fillChannel(short[] channel, List<Integer> pattern)
    {
        int samplesPerFrame = FRAME_BYTES / 2;
        int frame = 0;
        for (int i = 0; i < pattern.size(); i++)
        {
            int frames = pattern.get(i);
            boolean voiced = i % 2 == 0;
            for (int f = 0; f < frames; f++)
            {
                if (voiced)
                {
                    for (int s = 0; s < samplesPerFrame; s++)
                    {
                        channel[frame * samplesPerFrame + s] =
                                (short) (Math.sin(2 * Math.PI * 200 * s / RATE) * 7000);
                    }
                }
                frame++;
            }
        }
    }

    private static byte[] writeStereoWav(short[] left, short[] right)
    {
        int n = left.length;
        byte[] data = new byte[n * 4];
        for (int i = 0; i < n; i++)
        {
            data[i * 4] = (byte) (left[i] & 0xFF);
            data[i * 4 + 1] = (byte) ((left[i] >> 8) & 0xFF);
            data[i * 4 + 2] = (byte) (right[i] & 0xFF);
            data[i * 4 + 3] = (byte) ((right[i] >> 8) & 0xFF);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "RIFF");
        writeInt(out, 36 + data.length);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 2);
        writeInt(out, RATE);
        writeInt(out, RATE * 4);
        writeShort(out, 4);
        writeShort(out, 16);
        writeAscii(out, "data");
        writeInt(out, data.length);
        out.writeBytes(data);
        return out.toByteArray();
    }

    /** 单声道 WAV（双腿分轨用）：pattern 同 fillChannel 约定 */
    private static byte[] monoWav(List<Integer> pattern)
    {
        int totalFrames = frameCount(pattern);
        short[] mono = new short[totalFrames * (FRAME_BYTES / 2)];
        fillChannel(mono, pattern);
        byte[] data = new byte[mono.length * 2];
        for (int i = 0; i < mono.length; i++)
        {
            data[i * 2] = (byte) (mono[i] & 0xFF);
            data[i * 2 + 1] = (byte) ((mono[i] >> 8) & 0xFF);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "RIFF");
        writeInt(out, 36 + data.length);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, RATE);
        writeInt(out, RATE * 2);
        writeShort(out, 2);
        writeShort(out, 16);
        writeAscii(out, "data");
        writeInt(out, data.length);
        out.writeBytes(data);
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s)
    {
        for (int i = 0; i < s.length(); i++)
        {
            out.write(s.charAt(i));
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int v)
    {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, int v)
    {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    @Test
    void diarizeStereo_twoRoles_mappedByDominantChannel()
    {
        // 每声道两段语音（10 有声 + 40 静默 + 10 有声）：声道0=坐席，声道1=客户
        byte[] wav = stereoWav(List.of(
                List.of(10, 40, 10),
                List.of(10, 40, 10)));
        // 提嵌入顺序：声道0段0、段1 → 声道1段0、段1
        List<float[]> emb = List.of(unit(1, 0), unit(1, 0.01f), unit(0, 1), unit(0, 1.01f));

        SpeakerDiarizationService.Result result = service(emb).diarizeStereo(wav);

        assertEquals(RATE, result.getSampleRate());
        assertEquals(2, result.getSpeakerCount());
        assertEquals(4, result.getSegments().size());
        assertEquals(SpeakerDiarizationService.DEFAULT_ROLE_A, result.getRoleAName());
        for (SpeakerDiarizationService.Segment seg : result.getSegments())
        {
            if (seg.getChannel() == 0)
            {
                assertEquals(SpeakerDiarizationService.SpeakerRole.ROLE_A, seg.getRole());
                assertEquals("坐席", seg.getRoleName());
            }
            else
            {
                assertEquals(SpeakerDiarizationService.SpeakerRole.ROLE_B, seg.getRole());
                assertEquals("客户", seg.getRoleName());
            }
            // 同向量聚类，段对中心余弦 ≈ 1
            assertTrue(seg.getConfidence() > 0.99f, "confidence=" + seg.getConfidence());
            assertTrue(seg.getEndMs() > seg.getStartMs());
        }
        // 说话人汇总：坐席/客户各 2 段
        SpeakerDiarizationService.Speaker a = result.getSpeakers().get(0);
        SpeakerDiarizationService.Speaker b = result.getSpeakers().get(1);
        assertEquals(2, a.getSegmentCount());
        assertEquals(2, b.getSegmentCount());
        assertEquals(SpeakerDiarizationService.SpeakerRole.ROLE_A, a.getRole());
        assertEquals(SpeakerDiarizationService.SpeakerRole.ROLE_B, b.getRole());
        assertTrue(a.getDurationMs() > 0 && b.getDurationMs() > 0);
    }

    @Test
    void diarizeStereo_segmentsSortedByStartTime()
    {
        byte[] wav = stereoWav(List.of(
                List.of(10, 40, 10),
                List.of(10, 40, 10)));
        List<float[]> emb = List.of(unit(1, 0), unit(1, 0), unit(0, 1), unit(0, 1));

        SpeakerDiarizationService.Result result = service(emb).diarizeStereo(wav);

        List<SpeakerDiarizationService.Segment> segs = result.getSegments();
        for (int i = 1; i < segs.size(); i++)
        {
            assertTrue(segs.get(i - 1).getStartMs() <= segs.get(i).getStartMs());
        }
    }

    @Test
    void diarizeStereo_crossChannelCluster_mapsOther()
    {
        // 声道0两段、声道1两段；同一声纹向量出现在两个声道（时长相当）→ 无主导声道 → OTHER
        byte[] wav = stereoWav(List.of(
                List.of(10, 40, 10),
                List.of(10, 40, 10)));
        List<float[]> emb = List.of(unit(1, 0), unit(0, 1), unit(1, 0), unit(0, 1));

        SpeakerDiarizationService.Result result = service(emb).diarizeStereo(wav);

        assertEquals(2, result.getSpeakerCount());
        boolean hasOther = result.getSpeakers().stream()
                .anyMatch(s -> s.getRole() == SpeakerDiarizationService.SpeakerRole.OTHER);
        assertTrue(hasOther, "跨声道 1:1 的簇应判 OTHER");
        for (SpeakerDiarizationService.Speaker sp : result.getSpeakers())
        {
            assertEquals(SpeakerDiarizationService.SpeakerRole.OTHER, sp.getRole());
            assertEquals(SpeakerDiarizationService.ROLE_OTHER, sp.getRoleName());
        }
    }

    @Test
    void diarizeStereo_customRoles_propagated()
    {
        byte[] wav = stereoWav(List.of(
                List.of(10, 40, 10),
                List.of(10, 40, 10)));
        List<float[]> emb = List.of(unit(1, 0), unit(1, 0), unit(0, 1), unit(0, 1));

        WavPcmDecoderChannelsHolder channels = new WavPcmDecoderChannelsHolder(wav);
        SpeakerDiarizationService.Result result = service(emb).diarizeChannels(
                channels.channels, "值班律师", "来电人", SpeakerClusterer.DEFAULT_THRESHOLD);

        assertEquals("值班律师", result.getRoleAName());
        assertEquals("来电人", result.getRoleBName());
        for (SpeakerDiarizationService.Segment seg : result.getSegments())
        {
            String expected = seg.getChannel() == 0 ? "值班律师" : "来电人";
            assertEquals(expected, seg.getRoleName());
        }
    }

    @Test
    void diarizeStereo_monoInput_returnsEmpty()
    {
        byte[] mono = monoWav(List.of(10, 40, 10));
        // 伪提取器即便"可用"，单声道也应短路为空
        SpeakerDiarizationService.Result result =
                service(List.of(unit(1, 0))).diarizeStereo(mono);

        assertEquals(0, result.getSpeakerCount());
        assertTrue(result.getSegments().isEmpty());
    }

    @Test
    void diarizeStereo_modelUnavailable_returnsEmpty()
    {
        byte[] wav = stereoWav(List.of(
                List.of(10, 40, 10),
                List.of(10, 40, 10)));
        SpeakerEmbeddingExtractor degraded =
                new SpeakerEmbeddingExtractor(RATE, "models/__no_such__.onnx");
        SpeakerDiarizationService svc =
                new SpeakerDiarizationService(degraded, new SpeakerClusterer());

        SpeakerDiarizationService.Result result = svc.diarizeStereo(wav);

        assertEquals(0, result.getSpeakerCount());
        assertTrue(result.getSegments().isEmpty());
    }

    @Test
    void diarizeTwoLegs_endToEnd()
    {
        // 双腿分轨：legA 10有声+40静默+10有声；legB 较短（仅前段），解码器补零对齐
        byte[] legA = monoWav(List.of(10, 40, 10));
        byte[] legB = monoWav(List.of(10));
        List<float[]> emb = List.of(unit(1, 0), unit(1, 0), unit(0, 1));

        SpeakerDiarizationService.Result result = service(emb).diarizeTwoLegs(legA, legB);

        assertEquals(2, result.getSpeakerCount());
        assertEquals(3, result.getSegments().size());
        assertEquals(1L, result.getSpeakers().stream()
                .filter(s -> s.getRole() == SpeakerDiarizationService.SpeakerRole.ROLE_B).count());
    }

    /** 仅为复用 decodeChannels 解析结果的小包装（避免测试里直接 import 造成歧义） */
    private static final class WavPcmDecoderChannelsHolder
    {
        final ai.lawyers.system.service.impl.lawyers.quality.WavPcmDecoder.ChannelPcm channels;

        WavPcmDecoderChannelsHolder(byte[] wav)
        {
            this.channels = ai.lawyers.system.service.impl.lawyers.quality.WavPcmDecoder.decodeChannels(wav);
        }
    }
}
