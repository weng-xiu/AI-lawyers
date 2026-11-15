package ai.lawyers.system.service.lawyers.voice.speaker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * KaldiFbank 单测：非法/过短输入、分帧数量、特征维度、
 * 静音/去直流、确定性与能量单调性。
 *
 * @author ai-lawyers
 */
class KaldiFbankTest
{
    @Test
    void invalidInputReturnsEmpty()
    {
        assertThat(KaldiFbank.compute(null, 16000)).isEmpty();
        assertThat(KaldiFbank.compute(new float[0], 16000)).isEmpty();
        assertThat(KaldiFbank.compute(new float[800], 0)).isEmpty();
    }

    @Test
    void shorterThanOneFrameReturnsEmpty()
    {
        assertThat(KaldiFbank.compute(new float[399], 16000)).isEmpty();
    }

    @Test
    void exactlyOneFrame()
    {
        float[][] fbank = KaldiFbank.compute(new float[400], 16000);

        assertThat(fbank).hasDimensions(1, KaldiFbank.FBANK_DIM);
        assertThat(fbank[0]).hasSize(KaldiFbank.FBANK_DIM);
    }

    @Test
    void frameCountFollowsSnipEdges()
    {
        // (560 - 400) / 160 + 1 = 2
        assertThat(KaldiFbank.compute(new float[560], 16000)).hasDimensions(2, KaldiFbank.FBANK_DIM);
        // (720 - 400) / 160 + 1 = 3
        assertThat(KaldiFbank.compute(new float[720], 16000)).hasDimensions(3, KaldiFbank.FBANK_DIM);
    }

    @Test
    void silenceProducesLogEpsilon()
    {
        float[][] fbank = KaldiFbank.compute(new float[400], 16000);

        assertSilent(fbank[0]);
    }

    @Test
    void dcOffsetRemoved()
    {
        float[] constant = new float[400];
        java.util.Arrays.fill(constant, 0.5f);

        float[][] fbank = KaldiFbank.compute(constant, 16000);

        assertSilent(fbank[0]);
    }

    @Test
    void deterministicForSameInput()
    {
        float[] samples = sine(16000, 440d, 0.5d, 800);

        float[][] first = KaldiFbank.compute(samples, 16000);
        float[][] second = KaldiFbank.compute(samples, 16000);

        assertThat(first).hasDimensions(second.length, KaldiFbank.FBANK_DIM);
        for (int f = 0; f < first.length; f++)
        {
            assertThat(first[f]).containsExactly(second[f]);
        }
    }

    @Test
    void allValuesFinite()
    {
        float[][] fbank = KaldiFbank.compute(sine(16000, 440d, 0.5d, 800), 16000);

        for (float[] frame : fbank)
        {
            for (float value : frame)
            {
                assertThat(Float.isFinite(value)).isTrue();
            }
        }
    }

    @Test
    void louderSignalHasHigherMeanEnergy()
    {
        double loud = mean(KaldiFbank.compute(sine(16000, 440d, 0.5d, 800), 16000));
        double quiet = mean(KaldiFbank.compute(sine(16000, 440d, 0.005d, 800), 16000));

        assertThat(loud).isGreaterThan(quiet);
    }

    @Test
    void supports8kSampleRate()
    {
        float[][] fbank = KaldiFbank.compute(sine(8000, 300d, 0.3d, 400), 8000);

        // (400 - 200) / 80 + 1 = 3
        assertThat(fbank).hasDimensions(3, KaldiFbank.FBANK_DIM);
        assertThat(fbank[0]).hasSize(KaldiFbank.FBANK_DIM);
    }

    private void assertSilent(float[] frame)
    {
        float expected = (float) Math.log(1.1920929e-7f);
        for (float value : frame)
        {
            assertThat(Math.abs(value - expected)).isLessThan(0.001f);
        }
    }

    private double mean(float[][] fbank)
    {
        double sum = 0;
        int count = 0;
        for (float[] frame : fbank)
        {
            for (float value : frame)
            {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private float[] sine(int sampleRate, double hz, double amplitude, int length)
    {
        float[] samples = new float[length];
        for (int i = 0; i < length; i++)
        {
            samples[i] = (float) (amplitude * Math.sin(2 * Math.PI * hz * i / sampleRate));
        }
        return samples;
    }
}
