package ai.lawyers.system.service.impl.lawyers.quality;

/**
 * P1-7：轻量 WAV（RIFF/WAVE）→ S16LE 单声道 PCM 解析器。
 *
 * <p>仅解析标准 PCM WAV（format tag=1，16bit）；立体声按左右声道平均混成单声道。
 * 质检声学规则只需 8k/16k 采样率的单声道 PCM，故不引入 javax.sound 等重型依赖。
 * 纯算法、无 Spring 依赖，逐 chunk 遍历以兼容 LIST/fact 等附加块。</p>
 *
 * @author ai-lawyers
 */
public final class WavPcmDecoder
{
    /** 解码结果：采样率 + S16LE 单声道 PCM */
    public static class Pcm
    {
        private final int sampleRate;
        private final byte[] data;

        Pcm(int sampleRate, byte[] data)
        {
            this.sampleRate = sampleRate;
            this.data = data;
        }

        public int getSampleRate() { return sampleRate; }
        public byte[] getData() { return data; }
    }

    private WavPcmDecoder()
    {
    }

    /**
     * 解析 WAV 字节。
     *
     * @throws IllegalArgumentException 非 RIFF/WAVE、非 PCM、非 16bit 或数据缺失
     */
    public static Pcm decode(byte[] wav)
    {
        if (wav == null || wav.length < 44)
        {
            throw new IllegalArgumentException("WAV 数据过短或为空");
        }
        if (!asciiAt(wav, 0, "RIFF") || !asciiAt(wav, 8, "WAVE"))
        {
            throw new IllegalArgumentException("非 RIFF/WAVE 格式");
        }

        int audioFormat = -1;
        int channels = -1;
        int sampleRate = -1;
        int bitsPerSample = -1;
        byte[] raw = null;

        int pos = 12;
        while (pos + 8 <= wav.length)
        {
            String id = ascii(wav, pos, 4);
            long size = u32(wav, pos + 4);
            int body = pos + 8;
            if (size < 0 || body > wav.length)
            {
                break;
            }
            if ("fmt ".equals(id))
            {
                audioFormat = u16(wav, body);
                channels = u16(wav, body + 2);
                sampleRate = (int) u32(wav, body + 4);
                bitsPerSample = u16(wav, body + 14);
            }
            else if ("data".equals(id))
            {
                raw = new byte[(int) size];
                System.arraycopy(wav, body, raw, 0, (int) size);
            }
            // chunk 体按字对齐（偶数），跳过未知块
            pos = body + (int) size + ((size & 1L) == 1L ? 1 : 0);
        }

        if (audioFormat != 1)
        {
            throw new IllegalArgumentException("仅支持 PCM(format=1) WAV，当前 format=" + audioFormat);
        }
        if (bitsPerSample != 16)
        {
            throw new IllegalArgumentException("仅支持 16bit WAV，当前 bits=" + bitsPerSample);
        }
        if (channels != 1 && channels != 2)
        {
            throw new IllegalArgumentException("仅支持单/双声道 WAV，当前 channels=" + channels);
        }
        if (sampleRate <= 0 || raw == null || raw.length == 0)
        {
            throw new IllegalArgumentException("WAV 缺少有效 data 块");
        }

        byte[] mono = channels == 2 ? downmix(raw) : raw;
        return new Pcm(sampleRate, mono);
    }

    /** 立体声 S16LE 左右声道平均混成单声道 */
    private static byte[] downmix(byte[] stereo)
    {
        int frames = stereo.length / 4;
        byte[] out = new byte[frames * 2];
        for (int i = 0; i < frames; i++)
        {
            int left = (short) ((stereo[i * 4] & 0xFF) | (stereo[i * 4 + 1] << 8));
            int right = (short) ((stereo[i * 4 + 2] & 0xFF) | (stereo[i * 4 + 3] << 8));
            short mix = (short) ((left + right) / 2);
            out[i * 2] = (byte) (mix & 0xFF);
            out[i * 2 + 1] = (byte) ((mix >> 8) & 0xFF);
        }
        return out;
    }

    private static int u16(byte[] b, int off)
    {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int off)
    {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    private static String ascii(byte[] b, int off, int len)
    {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
        {
            sb.append((char) (b[off + i] & 0xFF));
        }
        return sb.toString();
    }

    private static boolean asciiAt(byte[] b, int off, String expect)
    {
        return ascii(b, off, expect.length()).equals(expect);
    }
}
