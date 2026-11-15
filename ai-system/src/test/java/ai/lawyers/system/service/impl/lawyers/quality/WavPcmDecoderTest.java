package ai.lawyers.system.service.impl.lawyers.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * WavPcmDecoder 单测：标准 PCM WAV 解析、立体声下混/分道、双腿对齐补齐，
 * 以及非 RIFF、非 PCM、非 16bit、声道数非法、缺 data 等错误分支。
 *
 * @author ai-lawyers
 */
class WavPcmDecoderTest
{
    @Test
    void decodesMonoPcm()
    {
        byte[] data = samples((short) 1, (short) 2, (short) 3);

        WavPcmDecoder.Pcm pcm = WavPcmDecoder.decode(wav(1, 16000, 16, 1, data));

        assertThat(pcm.getSampleRate()).isEqualTo(16000);
        assertThat(pcm.getData()).isEqualTo(data);
    }

    @Test
    void decodesStereoByAveragingChannels()
    {
        byte[] data = samples((short) 1000, (short) 2000, (short) 3000, (short) 4000);

        WavPcmDecoder.Pcm pcm = WavPcmDecoder.decode(wav(2, 8000, 16, 1, data));

        assertThat(readSamples(pcm.getData())).containsExactly(1500, 3500);
    }

    @Test
    void decodeChannelsKeepsMonoAsSingleChannel()
    {
        byte[] data = samples((short) 7, (short) 8);

        WavPcmDecoder.ChannelPcm pcm = WavPcmDecoder.decodeChannels(wav(1, 8000, 16, 1, data));

        assertThat(pcm.getChannelCount()).isEqualTo(1);
        assertThat(readSamples(pcm.getChannel(0))).containsExactly(7, 8);
    }

    @Test
    void decodeChannelsDeinterleavesStereo()
    {
        byte[] data = samples((short) 1000, (short) 2000, (short) 3000, (short) 4000);

        WavPcmDecoder.ChannelPcm pcm = WavPcmDecoder.decodeChannels(wav(2, 8000, 16, 1, data));

        assertThat(pcm.getChannelCount()).isEqualTo(2);
        assertThat(readSamples(pcm.getChannel(0))).containsExactly(1000, 3000);
        assertThat(readSamples(pcm.getChannel(1))).containsExactly(2000, 4000);
    }

    @Test
    void decodeTwoLegsPadsShorterLegWithSilence()
    {
        byte[] legA = wav(1, 8000, 16, 1, samples((short) 1, (short) 2));
        byte[] legB = wav(1, 8000, 16, 1, samples((short) 3, (short) 4, (short) 5, (short) 6));

        WavPcmDecoder.ChannelPcm pcm = WavPcmDecoder.decodeTwoLegs(legA, legB);

        assertThat(pcm.getSampleRate()).isEqualTo(8000);
        assertThat(pcm.getChannelCount()).isEqualTo(2);
        assertThat(readSamples(pcm.getChannel(0))).containsExactly(1, 2, 0, 0);
        assertThat(readSamples(pcm.getChannel(1))).containsExactly(3, 4, 5, 6);
    }

    @Test
    void decodeTwoLegsRejectsStereoInput()
    {
        byte[] stereo = wav(2, 8000, 16, 1, samples((short) 1, (short) 2));
        byte[] mono = wav(1, 8000, 16, 1, samples((short) 1));

        assertThatThrownBy(() -> WavPcmDecoder.decodeTwoLegs(stereo, mono))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("两个单声道");
    }

    @Test
    void decodeTwoLegsRejectsSampleRateMismatch()
    {
        byte[] a = wav(1, 8000, 16, 1, samples((short) 1));
        byte[] b = wav(1, 16000, 16, 1, samples((short) 1));

        assertThatThrownBy(() -> WavPcmDecoder.decodeTwoLegs(a, b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("采样率不一致");
    }

    @Test
    void trimsUnalignedTailSamples()
    {
        byte[] data = new byte[] { 1, 2, 3 };

        WavPcmDecoder.Pcm pcm = WavPcmDecoder.decode(wav(1, 8000, 16, 1, data));

        assertThat(pcm.getData()).hasSize(2);
    }

    @Test
    void parsesExtraChunks()
    {
        byte[] data = samples((short) 9);
        byte[] wav = rawWav(1, 8000, 16, 1, data, true);

        assertThat(readSamples(WavPcmDecoder.decode(wav).getData())).containsExactly(9);
    }

    @Test
    void rejectsTooShortData()
    {
        assertThatThrownBy(() -> WavPcmDecoder.decode(new byte[10]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过短");
    }

    @Test
    void rejectsNonRiff()
    {
        byte[] wav = wav(1, 8000, 16, 1, samples((short) 1));
        wav[0] = 'X';

        assertThatThrownBy(() -> WavPcmDecoder.decode(wav))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非 RIFF/WAVE");
    }

    @Test
    void rejectsNonPcmFormat()
    {
        assertThatThrownBy(() -> WavPcmDecoder.decode(wav(1, 8000, 16, 3, new byte[4])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 PCM");
    }

    @Test
    void rejectsNon16Bit()
    {
        assertThatThrownBy(() -> WavPcmDecoder.decode(wav(1, 8000, 8, 1, new byte[4])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 16bit");
    }

    @Test
    void rejectsUnsupportedChannelCount()
    {
        assertThatThrownBy(() -> WavPcmDecoder.decode(wav(3, 8000, 16, 1, new byte[6])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持单/双声道");
    }

    @Test
    void rejectsMissingDataChunk()
    {
        byte[] wav = rawWav(1, 8000, 16, 1, null, false);

        assertThatThrownBy(() -> WavPcmDecoder.decode(wav))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少有效 data 块");
    }

    private byte[] wav(int channels, int sampleRate, int bits, int format, byte[] data)
    {
        return rawWav(channels, sampleRate, bits, format, data, false);
    }

    private byte[] rawWav(int channels, int sampleRate, int bits, int format, byte[] data,
                          boolean withListChunk)
    {
        int dataLen = data == null ? 0 : data.length;
        int extra = withListChunk ? 12 : 0;
        ByteBuffer buffer = ByteBuffer.allocate(44 + extra + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + extra + dataLen);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) format);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * channels * bits / 8);
        buffer.putShort((short) (channels * bits / 8));
        buffer.putShort((short) bits);
        if (withListChunk)
        {
            buffer.put("LIST".getBytes(StandardCharsets.US_ASCII));
            buffer.putInt(4);
            buffer.put("INFO".getBytes(StandardCharsets.US_ASCII));
        }
        if (data != null)
        {
            buffer.put("data".getBytes(StandardCharsets.US_ASCII));
            buffer.putInt(dataLen);
            buffer.put(data);
        }
        return buffer.array();
    }

    private byte[] samples(short... values)
    {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short value : values)
        {
            buffer.putShort(value);
        }
        return buffer.array();
    }

    private short[] readSamples(byte[] pcm)
    {
        short[] values = new short[pcm.length / 2];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
        }
        return values;
    }
}
