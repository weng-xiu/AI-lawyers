package ai.lawyers.system.service.lawyers.voice.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * P2-15：声纹嵌入提取器 ONNX 真实推理冒烟（CAM++，3dspeaker 中文 16k）。
 *
 * <p>模型与 ONNX Runtime 就绪才真实执行；任一缺席整类跳过（Assumption），
 * 不影响无模型环境的常规回归。模型路径：{@code -Dspeaker.onnx.path} 覆盖，
 * 默认依次探测 {@code ../models/speaker_embedding.onnx}（surefire 工作目录=模块目录）
 * 与 {@code models/speaker_embedding.onnx}（应用运行目录布局）。</p>
 *
 * @author ai-lawyers
 */
class SpeakerEmbeddingExtractorOnnxTest
{
    private static final int SAMPLE_RATE = 16000;

    private static Path modelPath;

    @BeforeAll
    static void resolveModelPath()
    {
        String prop = System.getProperty("speaker.onnx.path");
        if (prop != null && !prop.isEmpty() && Files.isRegularFile(Path.of(prop)))
        {
            modelPath = Path.of(prop);
            return;
        }
        for (String cand : new String[] {
                "../models/speaker_embedding.onnx", "models/speaker_embedding.onnx" })
        {
            if (Files.isRegularFile(Path.of(cand)))
            {
                modelPath = Path.of(cand);
                return;
            }
        }
    }

    @Test
    void 模型就绪时提取L2归一嵌入并报告维度()
    {
        assumeTrue(modelPath != null, "未找到 models/speaker_embedding.onnx，跳过真实推理冒烟");
        SpeakerEmbeddingExtractor extractor = newExtractor();
        try
        {
            assumeTrue(extractor.isModelAvailable(), "ONNX Runtime 不在 classpath 或模型加载失败");
            float[] emb = extractor.extract(speechLike(220.0, 1, 7L));
            assertNotNull(emb, "真实模型推理不应返回 null");
            assertTrue(emb.length > 0, "嵌入维度须为正（CAM++ 为 192）");

            double norm = 0;
            for (float v : emb)
            {
                norm += (double) v * v;
            }
            assertEquals(1.0, Math.sqrt(norm), 1e-3, "嵌入须 L2 归一");
        }
        finally
        {
            extractor.close();
        }
    }

    @Test
    void 同输入确定性复现且异输入嵌入相异()
    {
        assumeTrue(modelPath != null, "未找到 models/speaker_embedding.onnx，跳过真实推理冒烟");
        SpeakerEmbeddingExtractor extractor = newExtractor();
        try
        {
            assumeTrue(extractor.isModelAvailable(), "ONNX Runtime 不在 classpath 或模型加载失败");
            float[] a1 = extractor.extract(speechLike(220.0, 1, 7L));
            float[] a2 = extractor.extract(speechLike(220.0, 1, 7L));
            assertNotNull(a1);
            assertNotNull(a2);
            assertEquals(a1.length, a2.length);
            assertEquals(1.0, cosine(a1, a2), 1e-6, "同输入两次推理须确定性一致");

            float[] b = extractor.extract(speechLike(440.0, 2, 99L));
            assertNotNull(b);
            assertTrue(cosine(a1, b) < 0.999, "异输入嵌入不应退化为同一向量");
        }
        finally
        {
            extractor.close();
        }
    }

    private SpeakerEmbeddingExtractor newExtractor()
    {
        return new SpeakerEmbeddingExtractor(SAMPLE_RATE, modelPath.toString());
    }

    /** 1 秒类语音合成 PCM（S16LE）：基频+泛音+3Hz 包络+种子噪声，幅度 tanh 软限幅 */
    private static byte[] speechLike(double baseFreq, int harmonics, long seed)
    {
        int n = SAMPLE_RATE;
        Random rnd = new Random(seed);
        byte[] pcm = new byte[n * 2];
        for (int i = 0; i < n; i++)
        {
            double t = (double) i / SAMPLE_RATE;
            double env = 0.6 + 0.4 * Math.sin(2 * Math.PI * 3.0 * t);
            double v = 0;
            for (int h = 1; h <= harmonics + 1; h++)
            {
                v += Math.sin(2 * Math.PI * baseFreq * h * t) / h;
            }
            v = v * env + 0.05 * rnd.nextGaussian();
            short s = (short) Math.round(0.3 * Math.tanh(v) * 32767);
            pcm[i * 2] = (byte) s;
            pcm[i * 2 + 1] = (byte) (s >> 8);
        }
        return pcm;
    }

    private static double cosine(float[] a, float[] b)
    {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++)
        {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
