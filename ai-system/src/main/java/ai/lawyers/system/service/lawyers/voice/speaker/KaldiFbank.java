package ai.lawyers.system.service.lawyers.voice.speaker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P2-15：Kaldi 兼容 80 维 fbank 前端（声纹模型用，无外部依赖）。
 *
 * <p>sherpa-onnx 预导出的 3D-Speaker CAM++/ERes2Net 声纹 ONNX 输入为
 * {@code [batch, frames, 80]} 的 fbank 特征（前端未烘焙进模型），本类在 Java 侧
 * 复刻 sherpa-onnx（Kaldi 特征口径）的 fbank 计算：</p>
 * <ul>
 *   <li>分帧：25ms 窗 / 10ms 位移（16k → 400/160 采样点），snip_edges；</li>
 *   <li>去直流 + 0.97 预加重 + povey 窗 {@code (0.5-0.5cos) ^ 0.85}；</li>
 *   <li>512 点 FFT 取功率谱，mel 三角滤波 80 bins（20Hz~8kHz，权重按 mel 域插值）；</li>
 *   <li>log(max(x, float-eps))，dither=0（确定性，同输入同输出）。</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public final class KaldiFbank
{
    /** 特征维度（sherpa-onnx 3D-Speaker 声纹模型固定 80） */
    public static final int FBANK_DIM = 80;

    private static final int FRAME_MS = 25;
    private static final int SHIFT_MS = 10;
    private static final float PREEMPH = 0.97f;
    private static final float EPS = 1.1920929e-7f; // float epsilon，Kaldi log 下限

    /** 每采样率一份 FFT/mel/窗表（16k 常驻，一次构建） */
    private static final Map<Integer, Tables> TABLES = new ConcurrentHashMap<>();

    private KaldiFbank()
    {
    }

    /**
     * 计算 fbank 特征。
     *
     * @param samples    波形浮点样本（S16LE 已 /32768 归一到 [-1,1]，与 sherpa-onnx 口径一致）
     * @param sampleRate 采样率
     * @return [frames][80]；样本不足一帧时返回空数组
     */
    public static float[][] compute(float[] samples, int sampleRate)
    {
        if (samples == null || samples.length == 0 || sampleRate <= 0)
        {
            return new float[0][];
        }
        Tables t = TABLES.computeIfAbsent(sampleRate, Tables::new);
        int frameLen = sampleRate * FRAME_MS / 1000;
        int frameShift = sampleRate * SHIFT_MS / 1000;
        if (samples.length < frameLen)
        {
            return new float[0][];
        }
        int frames = (samples.length - frameLen) / frameShift + 1;
        float[][] out = new float[frames][FBANK_DIM];
        double[] buf = new double[t.fftSize];
        double[] re = new double[t.fftSize];
        double[] im = new double[t.fftSize];
        for (int f = 0; f < frames; f++)
        {
            int off = f * frameShift;
            // 去直流
            double mean = 0;
            for (int i = 0; i < frameLen; i++)
            {
                mean += samples[off + i];
            }
            mean /= frameLen;
            // 预加重 + povey 窗（写入 FFT 缓冲）
            double prev = samples[off] - mean;
            for (int i = 0; i < frameLen; i++)
            {
                double cur = samples[off + i] - mean;
                buf[i] = (cur - PREEMPH * prev) * t.window[i];
                prev = cur;
            }
            System.arraycopy(buf, 0, re, 0, frameLen);
            java.util.Arrays.fill(im, 0, t.fftSize, 0d);
            java.util.Arrays.fill(re, frameLen, t.fftSize, 0d);
            fft(re, im);
            // 功率谱 + mel 滤波 + log
            for (int bin = 0; bin < FBANK_DIM; bin++)
            {
                double sum = 0;
                int from = t.binFrom[bin];
                int to = t.binTo[bin];
                for (int k = from; k <= to; k++)
                {
                    double power = re[k] * re[k] + im[k] * im[k];
                    float w = t.weights[bin][k - from];
                    if (w != 0f)
                    {
                        sum += w * power;
                    }
                }
                out[f][bin] = (float) Math.log(Math.max(sum, EPS));
            }
        }
        return out;
    }

    /** 原地迭代 radix-2 FFT（n 为 2 的幂） */
    private static void fft(double[] re, double[] im)
    {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++)
        {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1)
            {
                j ^= bit;
            }
            j ^= bit;
            if (i < j)
            {
                double tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                double ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1)
        {
            double ang = -2 * Math.PI / len;
            double wRe = Math.cos(ang);
            double wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len)
            {
                double curRe = 1;
                double curIm = 0;
                for (int k = 0; k < len / 2; k++)
                {
                    int a = i + k;
                    int b = a + len / 2;
                    double vRe = re[b] * curRe - im[b] * curIm;
                    double vIm = re[b] * curIm + im[b] * curRe;
                    re[b] = re[a] - vRe;
                    im[b] = im[a] - vIm;
                    re[a] += vRe;
                    im[a] += vIm;
                    double nRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = nRe;
                }
            }
        }
    }

    private static double melScale(double hz)
    {
        return 1127.0 * Math.log(1.0 + hz / 700.0);
    }

    private static double inverseMel(double mel)
    {
        return 700.0 * (Math.exp(mel / 1127.0) - 1.0);
    }

    /** 采样率相关表：povey 窗（25ms）与 mel 滤波器组（80 bins × 257 个 FFT bin） */
    private static final class Tables
    {
        final int fftSize;
        final double[] window;
        final float[][] weights;
        final int[] binFrom;
        final int[] binTo;

        Tables(int sampleRate)
        {
            int frameLen = sampleRate * FRAME_MS / 1000;
            fftSize = Integer.highestOneBit(frameLen * 2 - 1) << 1; // round_to_power_of_two
            int numFftBins = fftSize / 2 + 1;
            window = new double[frameLen];
            for (int i = 0; i < frameLen; i++)
            {
                double v = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / frameLen);
                window[i] = Math.pow(v, 0.85);
            }
            double melLow = melScale(20.0);
            double melHigh = melScale(sampleRate / 2.0);
            weights = new float[FBANK_DIM][];
            binFrom = new int[FBANK_DIM];
            binTo = new int[FBANK_DIM];
            double hzPerBin = (double) sampleRate / fftSize;
            for (int bin = 0; bin < FBANK_DIM; bin++)
            {
                double leftMel = melLow + bin * (melHigh - melLow) / (FBANK_DIM + 1);
                double centerMel = melLow + (bin + 1) * (melHigh - melLow) / (FBANK_DIM + 1);
                double rightMel = melLow + (bin + 2) * (melHigh - melLow) / (FBANK_DIM + 1);
                int from = Math.max(0, (int) Math.ceil(inverseMel(leftMel) / hzPerBin));
                int to = Math.min(numFftBins - 1, (int) Math.floor(inverseMel(rightMel) / hzPerBin));
                if (to < from)
                {
                    from = to = Math.min(numFftBins - 1, Math.max(0, bin == 0 ? 1 : to));
                }
                binFrom[bin] = from;
                binTo[bin] = to;
                weights[bin] = new float[to - from + 1];
                for (int k = from; k <= to; k++)
                {
                    double m = melScale(k * hzPerBin);
                    float w = 0f;
                    if (m > leftMel && m < rightMel)
                    {
                        w = (float) (m <= centerMel
                                ? (m - leftMel) / (centerMel - leftMel)
                                : (rightMel - m) / (rightMel - centerMel));
                    }
                    weights[bin][k - from] = w;
                }
            }
        }
    }
}
