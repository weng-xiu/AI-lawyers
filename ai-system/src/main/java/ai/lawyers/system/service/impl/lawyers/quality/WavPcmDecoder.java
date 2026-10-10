package ai.lawyers.system.service.impl.lawyers.quality;

/**
 * P1-7：轻量 WAV（RIFF/WAVE）→ S16LE PCM 解析器。
 *
 * <p>仅解析标准 PCM WAV（format tag=1，16bit）。默认 {@link #decode(byte[])} 把立体声按左右声道
 * 平均混成单声道（既有单声道质检链路不受影响）；双声道质检（P2-7 抢话/分角色语速）使用
 * {@link #decodeChannels(byte[])} 保留各声道不强混，FreeSWITCH uuid_record 双腿分轨文件使用
 * {@link #decodeTwoLegs(byte[], byte[])} 对齐输入。质检声学规则只需 8k/16k 采样率 PCM，
 * 故不引入 javax.sound 等重型依赖。纯算法、无 Spring 依赖，逐 chunk 遍历以兼容 LIST/fact 等附加块。</p>
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

    /**
     * 多声道解码结果：采样率 + 每个声道独立的 S16LE PCM（不做下混）。
     * 各声道等长（帧数一致），声道下标即 WAV 物理声道（0=左/legA，1=右/legB），
     * 与坐席/客户角色的对应关系由采集侧标定后由调用方映射。
     */
    public static class ChannelPcm
    {
        private final int sampleRate;
        private final byte[][] channels;

        ChannelPcm(int sampleRate, byte[][] channels)
        {
            this.sampleRate = sampleRate;
            this.channels = channels;
        }

        public int getSampleRate() { return sampleRate; }
        public int getChannelCount() { return channels.length; }
        public byte[] getChannel(int index) { return channels[index]; }
    }

    /** WAV 解析中间结果 */
    private static class Parsed
    {
        final int sampleRate;
        final int channels;
        final byte[] raw;

        Parsed(int sampleRate, int channels, byte[] raw)
        {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.raw = raw;
        }
    }

    private WavPcmDecoder()
    {
    }

    /**
     * 解析 WAV 字节并下混为单声道（既有单声道质检链路入口）。
     *
     * @throws IllegalArgumentException 非 RIFF/WAVE、非 PCM、非 16bit 或数据缺失
     */
    public static Pcm decode(byte[] wav)
    {
        Parsed parsed = parse(wav);
        byte[] mono = parsed.channels == 2 ? downmix(parsed.raw) : parsed.raw;
        return new Pcm(parsed.sampleRate, mono);
    }

    /**
     * P2-7：解析 WAV 并保留全部声道（不强混），双声道质检入口。
     *
     * <p>单声道 WAV 返回 1 个声道；立体声 WAV 拆出左/右两个独立 S16LE 通道，等长。
     * 声道与坐席/客户角色的映射由采集侧（RECORD_STEREO 实测标定或 uuid_record 分轨命名）决定。</p>
     *
     * @throws IllegalArgumentException 非 RIFF/WAVE、非 PCM、非 16bit 或数据缺失
     */
    public static ChannelPcm decodeChannels(byte[] wav)
    {
        Parsed parsed = parse(wav);
        byte[][] channels;
        if (parsed.channels == 1)
        {
            channels = new byte[][] { parsed.raw };
        }
        else
        {
            channels = deinterleave(parsed.raw);
        }
        return new ChannelPcm(parsed.sampleRate, channels);
    }

    /**
     * P2-7：解析 FreeSWITCH uuid_record 双腿分轨产生的两个单声道 WAV（两条 leg 各录一个文件），
     * 对齐为等长双通道：采样率必须一致，较短的一条以零样本（静默）补齐。
     *
     * @param legAWav 第一条 leg 的单声道 WAV（采集侧约定角色，如坐席）
     * @param legBWav 第二条 leg 的单声道 WAV（采集侧约定角色，如客户）
     * @throws IllegalArgumentException 任一方非单声道或双方采样率不一致
     */
    public static ChannelPcm decodeTwoLegs(byte[] legAWav, byte[] legBWav)
    {
        Parsed a = parse(legAWav);
        Parsed b = parse(legBWav);
        if (a.channels != 1 || b.channels != 1)
        {
            throw new IllegalArgumentException(
                    "双腿分轨要求两个单声道 WAV，当前 channels=" + a.channels + "/" + b.channels);
        }
        if (a.sampleRate != b.sampleRate)
        {
            throw new IllegalArgumentException(
                    "双腿采样率不一致：" + a.sampleRate + " vs " + b.sampleRate);
        }
        int frameCount = Math.max(a.raw.length, b.raw.length) / 2;
        byte[][] channels = new byte[][] { padMono(a.raw, frameCount), padMono(b.raw, frameCount) };
        return new ChannelPcm(a.sampleRate, channels);
    }

    /** 解析 WAV 字节，校验格式并返回原始交织 PCM（立体声 raw 为 LRLR...） */
    private static Parsed parse(byte[] wav)
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
        // data 块理论上应按帧对齐，截掉尾部不足一样本的残余
        int aligned = raw.length - (raw.length % (channels * 2));
        if (aligned != raw.length)
        {
            byte[] trimmed = new byte[aligned];
            System.arraycopy(raw, 0, trimmed, 0, aligned);
            raw = trimmed;
        }
        return new Parsed(sampleRate, channels, raw);
    }

    /** 立体声交织 S16LE（LRLR...）拆成两个等长单声道缓冲 */
    private static byte[][] deinterleave(byte[] stereo)
    {
        int frames = stereo.length / 4;
        byte[] left = new byte[frames * 2];
        byte[] right = new byte[frames * 2];
        for (int i = 0; i < frames; i++)
        {
            left[i * 2] = stereo[i * 4];
            left[i * 2 + 1] = stereo[i * 4 + 1];
            right[i * 2] = stereo[i * 4 + 2];
            right[i * 2 + 1] = stereo[i * 4 + 3];
        }
        return new byte[][] { left, right };
    }

    /** 单声道 PCM 补齐/截断到指定样本数，补零（静默） */
    private static byte[] padMono(byte[] raw, int frameCount)
    {
        byte[] out = new byte[frameCount * 2];
        System.arraycopy(raw, 0, out, 0, Math.min(raw.length, out.length));
        return out;
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
