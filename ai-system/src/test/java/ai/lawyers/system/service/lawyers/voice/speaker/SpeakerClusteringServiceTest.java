package ai.lawyers.system.service.lawyers.voice.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P2-15：说话人聚类编排层单测。
 *
 * <p>测试环境无 ONNX Runtime/模型，用一个“伪提取器”子类（构造走降级、覆写可用性与提取
 * 方法）按段序号回送预设向量，验证 WAV 解码 → VAD 分段 → 提嵌入 → 聚类 → 标签/时间范围
 * 映射的完整编排；同时校验模型缺席与静默录音的降级返回。</p>
 *
 * @author ai-lawyers
 */
class SpeakerClusteringServiceTest
{
    private static final int RATE = 16000;
    private static final int FRAME_BYTES = RATE * 20 / 1000 * 2;

    /** 按段序号回送预设向量的伪提取器（不触碰 ONNX） */
    private static final class StubExtractor extends SpeakerEmbeddingExtractor
    {
        private final List<float[]> bySegment;

        StubExtractor(List<float[]> bySegment)
        {
            super(RATE, "models/__no_such__.onnx");
            this.bySegment = bySegment;
        }

        @Override
        public boolean isModelAvailable()
        {
            return true;
        }

        @Override
        public float[] extract(byte[] pcm)
        {
            // 返回值由 service 依据段序号顺序调用，这里用计数器对齐
            int idx = call++ ;
            return idx < bySegment.size() ? bySegment.get(idx) : null;
        }

        private int call;
    }

    private static float[] unit(float x, float y)
    {
        double n = Math.sqrt(x * x + y * y);
        return new float[] { (float) (x / n), (float) (y / n) };
    }

    /** 生成含 voicedFrames 个有声帧、silenceFrames 个静默帧交替的单声道 WAV */
    private static byte[] wav(List<Integer> voiced, List<Integer> silence)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int totalFrames = 0;
        for (int i = 0; i < voiced.size(); i++)
        {
            totalFrames += voiced.get(i) + (i < silence.size() ? silence.get(i) : 0);
        }
        byte[] data = new byte[totalFrames * FRAME_BYTES];
        int pos = 0;
        for (int i = 0; i < voiced.size(); i++)
        {
            for (int f = 0; f < voiced.get(i); f++)
            {
                fillTone(data, pos * FRAME_BYTES);
                pos++;
            }
            pos += i < silence.size() ? silence.get(i) : 0;
        }
        writeWav(out, data);
        return out.toByteArray();
    }

    /** 200Hz 正弦、振幅 7000 填一帧（RMS 远超能量阈值） */
    private static void fillTone(byte[] buf, int off)
    {
        for (int i = 0; i < FRAME_BYTES / 2; i++)
        {
            short s = (short) (Math.sin(2 * Math.PI * 200 * i / RATE) * 7000);
            buf[off + i * 2] = (byte) (s & 0xFF);
            buf[off + i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
    }

    private static void writeWav(ByteArrayOutputStream out, byte[] data)
    {
        int byteRate = RATE * 2;
        writeAscii(out, "RIFF");
        writeInt(out, 36 + data.length);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, RATE);
        writeInt(out, byteRate);
        writeShort(out, 2);
        writeShort(out, 16);
        writeAscii(out, "data");
        writeInt(out, data.length);
        out.writeBytes(data);
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
    void cluster_twoSpeakers_labelsAndRangesMapped()
    {
        // 两段语音（各 10 有声帧），间隔 40 静默帧（>尾音20帧才能闭合出两段）；A、B 两人
        byte[] wav = wav(List.of(10, 10), List.of(40));
        List<float[]> emb = new ArrayList<>();
        emb.add(unit(1, 0));
        emb.add(unit(0, 1));
        SpeakerClusteringService service =
                new SpeakerClusteringService(new StubExtractor(emb), new SpeakerClusterer());

        SpeakerClusteringService.Result result = service.cluster(wav);

        assertEquals(RATE, result.getSampleRate());
        assertEquals(2, result.getSpeakerCount());
        List<SpeakerClusteringService.SegmentLabel> segs = result.getSegments();
        assertEquals(2, segs.size());
        assertEquals(0, segs.get(0).getSegmentIndex());
        assertEquals(1, segs.get(1).getSegmentIndex());
        assertEquals(0, segs.get(0).getSpeakerLabel());
        assertEquals(1, segs.get(1).getSpeakerLabel());
        // 段范围单调、为 20ms 整数倍
        assertTrue(segs.get(0).getEndMs() <= segs.get(1).getStartMs());
        assertEquals(0L, segs.get(0).getStartMs() % 20L);
    }

    @Test
    void cluster_sameSpeaker_mergedIntoOne()
    {
        byte[] wav = wav(List.of(10, 10), List.of(40));
        List<float[]> emb = new ArrayList<>();
        emb.add(unit(1, 0.01f));
        emb.add(unit(1, 0.02f));
        SpeakerClusteringService service =
                new SpeakerClusteringService(new StubExtractor(emb), new SpeakerClusterer());

        SpeakerClusteringService.Result result = service.cluster(wav);

        assertEquals(1, result.getSpeakerCount());
        assertEquals(0, result.getSegments().get(0).getSpeakerLabel());
        assertEquals(0, result.getSegments().get(1).getSpeakerLabel());
    }

    @Test
    void cluster_modelUnavailable_returnsEmpty()
    {
        SpeakerEmbeddingExtractor degraded =
                new SpeakerEmbeddingExtractor(RATE, "models/__no_such__.onnx");
        SpeakerClusteringService service =
                new SpeakerClusteringService(degraded, new SpeakerClusterer());

        SpeakerClusteringService.Result result = service.cluster(new byte[] { 1, 2, 3, 4 });

        assertEquals(0, result.getSpeakerCount());
        assertTrue(result.getSegments().isEmpty());
    }

    @Test
    void cluster_silenceRecording_noSegments()
    {
        // 30 帧全零振幅静音（无有声帧），伪提取器报告可用：应在无段时短路返回空段
        byte[] wav = wav(List.of(0), List.of(30));
        SpeakerClusteringService service = new SpeakerClusteringService(
                new StubExtractor(new ArrayList<>()), new SpeakerClusterer());

        SpeakerClusteringService.Result result = service.cluster(wav);

        assertEquals(0, result.getSpeakerCount());
        assertTrue(result.getSegments().isEmpty());
    }
}
