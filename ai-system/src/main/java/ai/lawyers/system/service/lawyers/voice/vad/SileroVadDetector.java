package ai.lawyers.system.service.lawyers.voice.vad;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Silero VAD 影子检测器（P2-12 方案 A）。
 *
 * <p>通过 ONNX Runtime Java 加载 Silero VAD int8 模型，在本地离线运行。
 * 与现有能量+过零率 VAD 并行跑影子模式：主决策仍走旧 VAD，本类判定结果仅记录日志打标。</p>
 *
 * <p><b>内网无外网适配：</b>ONNX Runtime 采用反射式可选加载——编译期不强制依赖，
 * 运行期若 classpath 中存在 {@code ai.onnxruntime.OrtEnvironment} 则自动启用真实推理，
 * 否则降级为 Mock 实现（基于能量+过零率的简化模拟，保证影子链路可运行）。</p>
 *
 * <p>模型要求：Silero VAD v4+ int8 ONNX，输入为 16kHz/8kHz 单声道 float32 PCM，
 * 支持 512 样本窗口（16kHz）或 256 样本窗口（8kHz）。状态张量维度 [2][1][64]。</p>
 *
 * @author ai-lawyers
 */
public class SileroVadDetector
{
    private static final Logger log = LoggerFactory.getLogger(SileroVadDetector.class);

    /** 默认模型路径（classpath 或文件系统相对路径） */
    public static final String DEFAULT_MODEL_PATH = "models/silero_vad_int8.onnx";

    /** 默认语音概率阈值（Silero 输出 0~1，通常 0.5 为平衡点） */
    public static final float DEFAULT_THRESHOLD = 0.5f;

    /** 默认起始连续触发帧数（与能量 VAD 对齐：3×20ms=60ms） */
    public static final int DEFAULT_START_FRAMES = 3;

    /** 默认结束连续静默帧数（与能量 VAD 对齐：20×20ms=400ms） */
    public static final int DEFAULT_END_SILENCE_FRAMES = 20;

    /** Silero VAD 状态维度 D1 */
    private static final int STATE_D1 = 2;
    /** Silero VAD 状态维度 D2 */
    private static final int STATE_D2 = 1;
    /** Silero VAD 状态维度 D3（常见 64 或 128，64 兼容多数 int8 模型） */
    private static final int STATE_D3 = 64;

    /** 影子判定事件回调 */
    public interface ShadowListener
    {
        void onShadowSpeechStart(long detectMs);
        void onShadowSpeechEnd();
    }

    /* ================= 配置 ================= */
    private final int sampleRate;
    private final float threshold;
    private final int startFrames;
    private final int endSilenceFrames;
    private final ShadowListener listener;

    /* ================= 运行时状态 ================= */
    private volatile boolean shadowSpeaking;
    private int consecutiveVoice;
    private int consecutiveSilence;
    private long runBeginNanos;
    private volatile long lastDetectionNanos;

    private final byte[] residue;
    private int residueLen;
    private final int frameBytes;

    /* ================= ONNX Runtime（反射可选） ================= */
    private final boolean onnxAvailable;
    private final Object ortEnv;
    private final Object ortSession;
    private final String inputName;
    private final String outputName;
    private final int windowSamples;
    private Object sampleRateTensor;

    /** LSTM 隐藏状态 h（Java 数组，避免 native 张量生命周期问题） */
    private final float[][][] hState = new float[STATE_D1][STATE_D2][STATE_D3];
    /** LSTM 细胞状态 c（Java 数组） */
    private final float[][][] cState = new float[STATE_D1][STATE_D2][STATE_D3];

    /* ================= Mock 模式 ================= */
    private final boolean mockMode;
    private static final double MOCK_ENERGY_THRESHOLD = 500.0;
    private static final double MOCK_ZCR_MIN = 0.002;
    private static final double MOCK_ZCR_MAX = 0.40;

    public SileroVadDetector(int sampleRate, ShadowListener listener)
    {
        this(sampleRate, DEFAULT_MODEL_PATH, DEFAULT_THRESHOLD,
                DEFAULT_START_FRAMES, DEFAULT_END_SILENCE_FRAMES, listener);
    }

    public SileroVadDetector(int sampleRate, String modelPath, float threshold,
            int startFrames, int endSilenceFrames, ShadowListener listener)
    {
        if (sampleRate != 8000 && sampleRate != 16000)
        {
            throw new IllegalArgumentException("Silero VAD 采样率仅支持 8000/16000: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        this.threshold = threshold;
        this.startFrames = Math.max(1, startFrames);
        this.endSilenceFrames = Math.max(1, endSilenceFrames);
        this.listener = listener;
        this.frameBytes = sampleRate * 20 / 1000 * 2;
        this.residue = new byte[frameBytes];
        this.windowSamples = sampleRate == 16000 ? 512 : 256;

        Object env = null;
        Object session = null;
        String inName = null;
        String outName = null;
        boolean available = false;
        boolean mock = true;

        try
        {
            Class<?> envClass = Class.forName("ai.onnxruntime.OrtEnvironment");
            Method getEnv = envClass.getMethod("getEnvironment");
            env = getEnv.invoke(null);

            Class<?> sessionOptionsClass = Class.forName("ai.onnxruntime.OrtSession$SessionOptions");
            Object options = sessionOptionsClass.getDeclaredConstructor().newInstance();

            byte[] modelBytes = loadModelBytes(modelPath);
            if (modelBytes != null && modelBytes.length > 0)
            {
                Method createSession = envClass.getMethod("createSession", byte[].class, sessionOptionsClass);
                session = createSession.invoke(env, modelBytes, options);

                Method getInputNames = session.getClass().getMethod("getInputNames");
                Method getOutputNames = session.getClass().getMethod("getOutputNames");
                @SuppressWarnings("unchecked")
                java.util.Set<String> inputs = (java.util.Set<String>) getInputNames.invoke(session);
                @SuppressWarnings("unchecked")
                java.util.Set<String> outputs = (java.util.Set<String>) getOutputNames.invoke(session);
                inName = inputs.iterator().next();
                outName = outputs.iterator().next();

                Class<?> tensorClass = Class.forName("ai.onnxruntime.OnnxTensor");
                Method createTensor = tensorClass.getMethod("createTensor",
                        envClass, FloatBuffer.class, long[].class);
                FloatBuffer srBuf = FloatBuffer.wrap(new float[] { sampleRate });
                sampleRateTensor = createTensor.invoke(null, env, srBuf, new long[] { 1 });

                available = true;
                mock = false;
                log.info("Silero VAD ONNX 模型加载成功 path={}, input={}, output={}, window={}",
                        modelPath, inName, outName, windowSamples);
            }
            else
            {
                log.warn("Silero VAD 模型文件为空或不存在，降级 Mock 模式 path={}", modelPath);
            }
        }
        catch (ClassNotFoundException e)
        {
            log.info("ONNX Runtime 不在 classpath 中，Silero VAD 使用 Mock 影子模式: {}", e.getMessage());
        }
        catch (Exception e)
        {
            log.warn("Silero VAD ONNX 初始化失败，降级 Mock 模式 path={}: {}", modelPath, e.getMessage());
        }

        this.onnxAvailable = available;
        this.ortEnv = env;
        this.ortSession = session;
        this.inputName = inName;
        this.outputName = outName;
        this.mockMode = mock;
    }

    public synchronized void feed(byte[] pcm)
    {
        if (pcm == null || pcm.length == 0)
        {
            return;
        }
        int offset = 0;
        if (residueLen > 0)
        {
            int need = frameBytes - residueLen;
            int copy = Math.min(need, pcm.length);
            System.arraycopy(pcm, 0, residue, residueLen, copy);
            residueLen += copy;
            offset += copy;
            if (residueLen == frameBytes)
            {
                processFrame(residue, 0);
                residueLen = 0;
            }
        }
        while (offset + frameBytes <= pcm.length)
        {
            processFrame(pcm, offset);
            offset += frameBytes;
        }
        if (offset < pcm.length)
        {
            System.arraycopy(pcm, offset, residue, 0, pcm.length - offset);
            residueLen = pcm.length - offset;
        }
    }

    public synchronized void reset()
    {
        shadowSpeaking = false;
        consecutiveVoice = 0;
        consecutiveSilence = 0;
        residueLen = 0;
        zeroState(hState);
        zeroState(cState);
    }

    public synchronized boolean isShadowSpeaking()
    {
        return shadowSpeaking;
    }

    public long getLastDetectionMs()
    {
        return lastDetectionNanos / 1_000_000L;
    }

    public boolean isMockMode()
    {
        return mockMode;
    }

    public boolean isOnnxAvailable()
    {
        return onnxAvailable;
    }

    public synchronized void close()
    {
        if (ortSession != null)
        {
            try
            {
                ortSession.getClass().getMethod("close").invoke(ortSession);
            }
            catch (Exception e)
            {
                log.debug("Silero VAD session close 异常: {}", e.getMessage());
            }
        }
        if (sampleRateTensor != null)
        {
            try
            {
                sampleRateTensor.getClass().getMethod("close").invoke(sampleRateTensor);
            }
            catch (Exception e)
            {
                log.debug("Silero VAD tensor close 异常: {}", e.getMessage());
            }
        }
    }

    /* ================= 内部 ================= */

    private void processFrame(byte[] buf, int off)
    {
        boolean voiced = mockMode ? mockFrameVoiced(buf, off) : inferFrameVoiced(buf, off);
        onFrame(voiced);
    }

    private boolean mockFrameVoiced(byte[] buf, int off)
    {
        double sumSq = 0;
        int zeroCross = 0;
        int samples = frameBytes / 2;
        int prev = sampleAt(buf, off);
        for (int i = 1; i < samples; i++)
        {
            int s = sampleAt(buf, off + i * 2);
            sumSq += (double) s * (double) s;
            if ((prev >= 0 && s < 0) || (prev < 0 && s >= 0))
            {
                zeroCross++;
            }
            prev = s;
        }
        double rms = Math.sqrt(sumSq / samples);
        double zcr = (double) zeroCross / samples;
        return rms >= MOCK_ENERGY_THRESHOLD && zcr >= MOCK_ZCR_MIN && zcr <= MOCK_ZCR_MAX;
    }

    private boolean inferFrameVoiced(byte[] buf, int off)
    {
        try
        {
            int samples = frameBytes / 2;
            float[] audio = new float[windowSamples];
            int copyLen = Math.min(samples, windowSamples);
            for (int i = 0; i < copyLen; i++)
            {
                audio[i] = sampleAt(buf, off + i * 2) / 32768.0f;
            }

            Class<?> tensorClass = Class.forName("ai.onnxruntime.OnnxTensor");
            Class<?> envClass = Class.forName("ai.onnxruntime.OrtEnvironment");
            Method createTensor = tensorClass.getMethod("createTensor", envClass, FloatBuffer.class, long[].class);
            Method closeTensor = tensorClass.getMethod("close");

            FloatBuffer audioBuf = FloatBuffer.wrap(audio);
            Object audioTensor = createTensor.invoke(null, ortEnv, audioBuf, new long[] { 1, windowSamples });

            Object hTensor = createTensor.invoke(null, ortEnv, wrapState(hState), new long[] { STATE_D1, STATE_D2, STATE_D3 });
            Object cTensor = createTensor.invoke(null, ortEnv, wrapState(cState), new long[] { STATE_D1, STATE_D2, STATE_D3 });

            Map<String, Object> inputs = new java.util.HashMap<>();
            inputs.put(inputName, audioTensor);
            inputs.put("sr", sampleRateTensor);
            inputs.put("h", hTensor);
            inputs.put("c", cTensor);

            Method run = ortSession.getClass().getMethod("run", Map.class);
            Object result = run.invoke(ortSession, inputs);

            Method getOutput = result.getClass().getMethod("get", String.class);
            Method getValue = tensorClass.getMethod("getValue");
            Method closeResult = result.getClass().getMethod("close");

            // output
            Object outputTensor = getOutput.invoke(result, outputName);
            Object value = getValue.invoke(outputTensor);
            float prob = parseProb(value);
            closeTensor.invoke(outputTensor);

            // 更新状态：读取 hn / cn
            readStateOutput(result, getOutput, getValue, closeTensor, "hn", hState);
            readStateOutput(result, getOutput, getValue, closeTensor, "cn", cState);

            // 关闭输入张量
            closeTensor.invoke(audioTensor);
            closeTensor.invoke(hTensor);
            closeTensor.invoke(cTensor);
            // 关闭 result
            closeResult.invoke(result);

            return prob >= threshold;
        }
        catch (Exception e)
        {
            log.warn("Silero VAD 推理异常，本帧按静默处理: {}", e.getMessage());
            return false;
        }
    }

    private static float parseProb(Object value)
    {
        if (value instanceof float[][])
        {
            return ((float[][]) value)[0][0];
        }
        if (value instanceof float[])
        {
            return ((float[]) value)[0];
        }
        return 0f;
    }

    private static void readStateOutput(Object result, Method getOutput, Method getValue,
            Method closeTensor, String name, float[][][] dst) throws Exception
    {
        try
        {
            Object tensor = getOutput.invoke(result, name);
            Object arr = getValue.invoke(tensor);
            if (arr instanceof float[][][])
            {
                copy3d((float[][][]) arr, dst);
            }
            closeTensor.invoke(tensor);
        }
        catch (Exception e)
        {
            log.trace("Silero VAD 读取状态 {} 失败: {}", name, e.getMessage());
        }
    }

    private static FloatBuffer wrapState(float[][][] state)
    {
        float[] flat = new float[STATE_D1 * STATE_D2 * STATE_D3];
        int idx = 0;
        for (int i = 0; i < STATE_D1; i++)
        {
            for (int j = 0; j < STATE_D2; j++)
            {
                System.arraycopy(state[i][j], 0, flat, idx, STATE_D3);
                idx += STATE_D3;
            }
        }
        return FloatBuffer.wrap(flat);
    }

    private static void copy3d(float[][][] src, float[][][] dst)
    {
        for (int i = 0; i < src.length && i < dst.length; i++)
        {
            for (int j = 0; j < src[i].length && j < dst[i].length; j++)
            {
                System.arraycopy(src[i][j], 0, dst[i][j], 0,
                        Math.min(src[i][j].length, dst[i][j].length));
            }
        }
    }

    private static void zeroState(float[][][] state)
    {
        for (int i = 0; i < state.length; i++)
        {
            for (int j = 0; j < state[i].length; j++)
            {
                java.util.Arrays.fill(state[i][j], 0f);
            }
        }
    }

    private void onFrame(boolean voiced)
    {
        if (!shadowSpeaking)
        {
            if (voiced)
            {
                if (consecutiveVoice == 0)
                {
                    runBeginNanos = System.nanoTime();
                }
                consecutiveVoice++;
            }
            else
            {
                consecutiveVoice = 0;
            }
            if (consecutiveVoice >= startFrames)
            {
                shadowSpeaking = true;
                consecutiveVoice = 0;
                consecutiveSilence = 0;
                lastDetectionNanos = System.nanoTime() - runBeginNanos;
                if (listener != null)
                {
                    listener.onShadowSpeechStart(getLastDetectionMs());
                }
            }
        }
        else
        {
            consecutiveSilence = voiced ? 0 : consecutiveSilence + 1;
            if (consecutiveSilence >= endSilenceFrames)
            {
                shadowSpeaking = false;
                consecutiveSilence = 0;
                consecutiveVoice = 0;
                if (listener != null)
                {
                    listener.onShadowSpeechEnd();
                }
            }
        }
    }

    private static int sampleAt(byte[] buf, int off)
    {
        return (short) ((buf[off] & 0xFF) | (buf[off + 1] << 8));
    }

    private static byte[] loadModelBytes(String modelPath)
    {
        try
        {
            if (modelPath.startsWith("classpath:"))
            {
                String res = modelPath.substring("classpath:".length());
                try (var is = SileroVadDetector.class.getClassLoader().getResourceAsStream(res))
                {
                    if (is == null)
                    {
                        return null;
                    }
                    return is.readAllBytes();
                }
            }
            else
            {
                java.io.File f = new java.io.File(modelPath);
                if (!f.exists())
                {
                    return null;
                }
                return java.nio.file.Files.readAllBytes(f.toPath());
            }
        }
        catch (Exception e)
        {
            log.warn("加载 Silero VAD 模型失败 path={}: {}", modelPath, e.getMessage());
            return null;
        }
    }
}
