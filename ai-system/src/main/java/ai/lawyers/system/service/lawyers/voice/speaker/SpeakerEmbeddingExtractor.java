package ai.lawyers.system.service.lawyers.voice.speaker;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P2-15 声纹嵌入提取器（骨架，离线）。
 *
 * <p>把一段 S16LE 单声道 PCM（通常由能量/Silero VAD 切出的语音段）送入预训练声纹
 * ONNX 模型，产出固定维度的说话人向量（speaker embedding），供 {@link SpeakerClusterer}
 * 做跨通话/跨号码的说话人聚类。</p>
 *
 * <p><b>内网无外网适配：</b>与 {@code SileroVadDetector} 同构——ONNX Runtime 编译期不
 * 强制依赖，运行期 classpath 存在 {@code ai.onnxruntime.OrtEnvironment} 且模型文件可读时
 * 才真实推理；否则进入降级态，{@link #isModelAvailable()} 返回 false、{@link #extract(byte[])}
 * 返回 null。降级态<b>不伪造向量</b>：伪造身份特征会造成错误聚类，故由调用方跳过聚类。</p>
 *
 * <p><b>模型契约：</b>sherpa-onnx 预导出的 3D-Speaker 声纹 ONNX（CAM++ 等）：
 * 单输入 float32 {@code [1, T, 80]}（Kaldi 兼容 80 维 fbank，由 {@link KaldiFbank}
 * 在内部计算——sherpa-onnx 导出未烘焙前端，入参仍为本方法的原始 S16LE PCM），
 * 单输出 float32 {@code [1, D]} 或 {@code [D]}。
 * 输入/输出名在加载时动态读取。输出经 L2 归一，便于聚类中心平均。</p>
 *
 * @author ai-lawyers
 */
public class SpeakerEmbeddingExtractor
{
    private static final Logger log = LoggerFactory.getLogger(SpeakerEmbeddingExtractor.class);

    /** 默认模型路径（classpath: 前缀或文件系统相对路径） */
    public static final String DEFAULT_MODEL_PATH = "models/speaker_embedding.onnx";

    private final int sampleRate;
    private final String modelPath;

    private final boolean modelAvailable;
    private final Object ortEnv;
    private final Object ortSession;
    private final String inputName;
    private final String outputName;

    public SpeakerEmbeddingExtractor(int sampleRate)
    {
        this(sampleRate, DEFAULT_MODEL_PATH);
    }

    public SpeakerEmbeddingExtractor(int sampleRate, String modelPath)
    {
        this.sampleRate = sampleRate;
        this.modelPath = modelPath;

        Object env = null;
        Object session = null;
        String inName = null;
        String outName = null;
        boolean available = false;

        try
        {
            Class<?> envClass = Class.forName("ai.onnxruntime.OrtEnvironment");
            Method getEnv = envClass.getMethod("getEnvironment");
            env = getEnv.invoke(null);

            Class<?> optionsClass = Class.forName("ai.onnxruntime.OrtSession$SessionOptions");
            Object options = optionsClass.getDeclaredConstructor().newInstance();
            // 单线程推理：话务场景按 VAD 短段提取（1 秒音频约 40ms），多线程收益可忽略，
            // 且避免 native 线程池常驻，对低配部署机更稳
            optionsClass.getMethod("setIntraOpNumThreads", int.class).invoke(options, 1);

            byte[] modelBytes = loadModelBytes(modelPath);
            if (modelBytes != null && modelBytes.length > 0)
            {
                Method createSession = envClass.getMethod("createSession", byte[].class, optionsClass);
                session = createSession.invoke(env, modelBytes, options);

                Method getInputNames = session.getClass().getMethod("getInputNames");
                Method getOutputNames = session.getClass().getMethod("getOutputNames");
                @SuppressWarnings("unchecked")
                java.util.Set<String> inputs = (java.util.Set<String>) getInputNames.invoke(session);
                @SuppressWarnings("unchecked")
                java.util.Set<String> outputs = (java.util.Set<String>) getOutputNames.invoke(session);
                inName = inputs.iterator().next();
                outName = outputs.iterator().next();

                available = true;
                log.info("声纹嵌入模型加载成功 path={}, input={}, output={}", modelPath, inName, outName);
            }
            else
            {
                log.warn("声纹嵌入模型为空或不存在，聚类降级跳过 path={}", modelPath);
            }
        }
        catch (ClassNotFoundException e)
        {
            log.info("ONNX Runtime 不在 classpath 中，声纹聚类降级跳过: {}", e.getMessage());
        }
        catch (Exception e)
        {
            log.warn("声纹嵌入模型初始化失败，聚类降级跳过 path={}: {}", modelPath, e.getMessage());
        }

        this.modelAvailable = available;
        this.ortEnv = env;
        this.ortSession = session;
        this.inputName = inName;
        this.outputName = outName;
    }

    /**
     * 提取单段语音的说话人向量（L2 归一）。
     *
     * @param pcm S16LE 单声道 PCM
     * @return 归一后的嵌入；模型不可用、输入为空或推理失败时返回 null
     */
    public float[] extract(byte[] pcm)
    {
        if (!modelAvailable || pcm == null || pcm.length < 2)
        {
            return null;
        }
        try
        {
            int n = pcm.length / 2;
            float[] audio = new float[n];
            for (int i = 0; i < n; i++)
            {
                short s = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
                audio[i] = s / 32768.0f;
            }

            // Kaldi 兼容 fbank 前端（模型输入 [1, T, 80]，sherpa-onnx 导出不含前端）
            float[][] feats = KaldiFbank.compute(audio, sampleRate);
            if (feats.length == 0)
            {
                return null;
            }
            float[] flat = new float[feats.length * KaldiFbank.FBANK_DIM];
            for (int i = 0; i < feats.length; i++)
            {
                System.arraycopy(feats[i], 0, flat, i * KaldiFbank.FBANK_DIM, KaldiFbank.FBANK_DIM);
            }

            Class<?> tensorClass = Class.forName("ai.onnxruntime.OnnxTensor");
            Class<?> envClass = Class.forName("ai.onnxruntime.OrtEnvironment");
            Method createTensor = tensorClass.getMethod("createTensor",
                    envClass, FloatBuffer.class, long[].class);
            Method closeTensor = tensorClass.getMethod("close");

            Object audioTensor = createTensor.invoke(null, ortEnv,
                    FloatBuffer.wrap(flat), new long[] { 1, feats.length, KaldiFbank.FBANK_DIM });

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(inputName, audioTensor);
            Method run = ortSession.getClass().getMethod("run", Map.class);
            Object result = run.invoke(ortSession, inputs);

            Method getOutput = result.getClass().getMethod("get", String.class);
            Method getValue = tensorClass.getMethod("getValue");
            Method closeResult = result.getClass().getMethod("close");

            // Result.get(String) 返回 Optional<OnnxValue>，须先 get() 再取值
            Object optionalOut = getOutput.invoke(result, outputName);
            Object outputTensor = optionalOut.getClass().getMethod("get").invoke(optionalOut);
            Object value = getValue.invoke(outputTensor);
            float[] embedding = parseEmbedding(value);

            // 只关自建的输入 tensor 与 Result；outputTensor 由 Result.close() 统一释放，
            // 手动再关一次会双重释放 native 句柄（实测 Windows 偶发 0xC0000374 堆损坏崩溃）
            closeTensor.invoke(audioTensor);
            closeResult.invoke(result);

            return l2Normalize(embedding);
        }
        catch (Exception e)
        {
            log.warn("声纹嵌入提取异常，本段跳过: {}", e.getMessage());
            return null;
        }
    }

    /** 模型/运行时是否就绪（false 时聚类应整体跳过） */
    public boolean isModelAvailable()
    {
        return modelAvailable;
    }

    public int getSampleRate()
    {
        return sampleRate;
    }

    public String getModelPath()
    {
        return modelPath;
    }

    public void close()
    {
        if (ortSession != null)
        {
            try
            {
                ortSession.getClass().getMethod("close").invoke(ortSession);
            }
            catch (Exception e)
            {
                log.debug("声纹 session close 异常: {}", e.getMessage());
            }
        }
    }

    private static float[] parseEmbedding(Object value)
    {
        if (value instanceof float[][])
        {
            float[][] m = (float[][]) value;
            return m.length > 0 ? m[0] : new float[0];
        }
        if (value instanceof float[])
        {
            return (float[]) value;
        }
        return new float[0];
    }

    private static float[] l2Normalize(float[] v)
    {
        if (v == null || v.length == 0)
        {
            return v;
        }
        double sumSq = 0;
        for (float x : v)
        {
            sumSq += (double) x * x;
        }
        double norm = Math.sqrt(sumSq);
        if (norm == 0d)
        {
            return v;
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++)
        {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    private static byte[] loadModelBytes(String path)
    {
        try
        {
            if (path.startsWith("classpath:"))
            {
                String res = path.substring("classpath:".length());
                try (var is = SpeakerEmbeddingExtractor.class.getClassLoader().getResourceAsStream(res))
                {
                    return is == null ? null : is.readAllBytes();
                }
            }
            java.io.File f = new java.io.File(path);
            return f.exists() ? java.nio.file.Files.readAllBytes(f.toPath()) : null;
        }
        catch (Exception e)
        {
            log.warn("加载声纹嵌入模型失败 path={}: {}", path, e.getMessage());
            return null;
        }
    }
}
