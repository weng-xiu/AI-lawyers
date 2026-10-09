package ai.lawyers.system.service.impl.lawyers.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * P1-7：WAV→PCM 解析器单测（构造标准 PCM WAV，校验采样率/声道与非法格式拒绝）。
 *
 * @author ai-lawyers
 */
class WavPcmDecoderTest
{
    private static final int RATE = 16000;

    @Test
    void decode_mono16k_preservesSamples()
    {
        byte[] wav = buildWav(RATE, 1, 320, null);
        WavPcmDecoder.Pcm pcm = WavPcmDecoder.decode(wav);
        assertEquals(RATE, pcm.getSampleRate());
        assertEquals(640, pcm.getData().length);
    }

    @Test
    void decode_stereo_isDownmixedToMono()
    {
        byte[] wav = buildWav(RATE, 2, 160, null);
        WavPcmDecoder.Pcm pcm = WavPcmDecoder.decode(wav);
        assertEquals(RATE, pcm.getSampleRate());
        // 立体声 N 帧 → 单声道 N 样本（字节数减半）
        assertEquals(160 * 2, pcm.getData().length);
    }

    @Test
    void decode_skipsUnknownChunks()
    {
        byte[] extra = {'L', 'I', 'S', 'T', 8, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8};
        byte[] wav = buildWav(RATE, 1, 320, extra);
        WavPcmDecoder.Pcm pcm = WavPcmDecoder.decode(wav);
        assertEquals(640, pcm.getData().length);
    }

    @Test
    void decode_notWav_throws()
    {
        byte[] bad = new byte[64];
        assertThrows(IllegalArgumentException.class, () -> WavPcmDecoder.decode(bad));
    }

    /**
     * 构造 PCM WAV。
     *
     * @param framesPerCh 每声道样本数
     * @param insertAfterFmt 插入到 fmt 块之后、data 块之前的额外字节（可为 null）
     */
    static byte[] buildWav(int rate, int channels, int framesPerCh, byte[] insertAfterFmt)
    {
        int dataBytes = framesPerCh * channels * 2;
        int extra = insertAfterFmt == null ? 0 : insertAfterFmt.length;
        byte[] wav = new byte[44 + extra + dataBytes];
        putAscii(wav, 0, "RIFF");
        putU32(wav, 4, 36 + extra + dataBytes);
        putAscii(wav, 8, "WAVE");
        putAscii(wav, 12, "fmt ");
        putU32(wav, 16, 16);
        putU16(wav, 20, 1);
        putU16(wav, 22, channels);
        putU32(wav, 24, rate);
        putU32(wav, 28, rate * channels * 2);
        putU16(wav, 32, channels * 2);
        putU16(wav, 34, 16);
        int pos = 36;
        if (insertAfterFmt != null)
        {
            System.arraycopy(insertAfterFmt, 0, wav, pos, insertAfterFmt.length);
            pos += insertAfterFmt.length;
        }
        putAscii(wav, pos, "data");
        putU32(wav, pos + 4, dataBytes);
        // 数据体用固定非零 S16LE 样本填充
        for (int i = 0; i < dataBytes; i += 2)
        {
            int off = pos + 8 + i;
            wav[off] = (byte) 0x00;
            wav[off + 1] = (byte) 0x20;
        }
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
