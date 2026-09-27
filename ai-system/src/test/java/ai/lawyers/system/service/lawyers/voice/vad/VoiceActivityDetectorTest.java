package ai.lawyers.system.service.lawyers.voice.vad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link VoiceActivityDetector} 轻量 VAD 判据测试（P3-A4）：
 * 能量+过零率双门限、起始 60ms（≤80ms 指标）、尾静默 400ms、残余缓冲拼帧、8k/16k 帧长。
 *
 * @author ai-lawyers
 */
class VoiceActivityDetectorTest
{
    /** 16kHz 20ms 帧样本数 */
    private static final int FRAME_SAMPLES_16K = 320;

    private static final class Rec implements VoiceActivityDetector.Listener
    {
        final List<String> events = new ArrayList<>();

        @Override
        public void onSpeechStart()
        {
            events.add("start");
        }

        @Override
        public void onSpeechEnd()
        {
            events.add("end");
        }
    }

    /** 生成 frames 个 20ms 帧的 440Hz 正弦 PCM（16kHz S16LE，RMS≈7071，ZCR≈0.055） */
    private static byte[] sine16k(int frames)
    {
        byte[] pcm = new byte[frames * FRAME_SAMPLES_16K * 2];
        for (int i = 0; i < frames * FRAME_SAMPLES_16K; i++)
        {
            short v = (short) (10000 * Math.sin(2 * Math.PI * 440 * i / 16000.0));
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) (v >> 8);
        }
        return pcm;
    }

    private static byte[] silence16k(int frames)
    {
        return new byte[frames * FRAME_SAMPLES_16K * 2];
    }

    @Test
    void silenceFrames_neverTrigger()
    {
        Rec rec = new Rec();
        VoiceActivityDetector vad = new VoiceActivityDetector(16000, rec);
        vad.feed(silence16k(50));
        assertThat(rec.events).isEmpty();
        assertThat(vad.isSpeaking()).isFalse();
    }

    @Test
    void speechStart_within80ms()
    {
        Rec rec = new Rec();
        VoiceActivityDetector vad = new VoiceActivityDetector(16000, rec);
        // 2 帧（40ms）不足起始判定
        vad.feed(sine16k(2));
        assertThat(rec.events).isEmpty();
        // 第 3 帧（累计 60ms ≤ 80ms）触发 start
        vad.feed(sine16k(1));
        assertThat(rec.events).containsExactly("start");
        assertThat(vad.isSpeaking()).isTrue();
    }

    @Test
    void speechEnd_after400msSilence()
    {
        Rec rec = new Rec();
        VoiceActivityDetector vad = new VoiceActivityDetector(16000, rec);
        vad.feed(sine16k(5));
        assertThat(rec.events).containsExactly("start");
        // 19 帧（380ms）静默未达结束阈值
        vad.feed(silence16k(19));
        assertThat(rec.events).containsExactly("start");
        // 第 20 帧（累计 400ms）触发 end
        vad.feed(silence16k(1));
        assertThat(rec.events).containsExactly("start", "end");
        assertThat(vad.isSpeaking()).isFalse();
    }

    @Test
    void dcBias_isRejectedByZcrFloor()
    {
        Rec rec = new Rec();
        VoiceActivityDetector vad = new VoiceActivityDetector(16000, rec);
        // 恒定直流偏置 8000（RMS 高但 ZCR=0 < zcrMin）
        byte[] dc = new byte[10 * FRAME_SAMPLES_16K * 2];
        for (int i = 0; i < dc.length / 2; i++)
        {
            dc[i * 2] = (byte) 0x40;
            dc[i * 2 + 1] = (byte) 0x1F; // 0x1F40 = 8000
        }
        vad.feed(dc);
        assertThat(rec.events).isEmpty();
    }

    @Test
    void highFrequencyHiss_isRejectedByZcrCeiling()
    {
        Rec rec = new Rec();
        VoiceActivityDetector vad = new VoiceActivityDetector(16000, rec);
        // 逐样本 ±10000 交替（ZCR≈1.0 > zcrMax，类白噪嘶声）
        byte[] hiss = new byte[10 * FRAME_SAMPLES_16K * 2];
        for (int i = 0; i < hiss.length / 2; i++)
        {
            short v = (short) (i % 2 == 0 ? 10000 : -10000);
            hiss[i * 2] = (byte) (v & 0xFF);
            hiss[i * 2 + 1] = (byte) (v >> 8);
        }
        vad.feed(hiss);
        assertThat(rec.events).isEmpty();
    }

    @Test
    void nonAlignedFeed_isBufferedAndReassembled()
    {
        Rec rec = new Rec();
        VoiceActivityDetector vad = new VoiceActivityDetector(16000, rec);
        byte[] three = sine16k(3);
        // 半帧两次喂入与整帧一次喂入结果一致
        int half = three.length / 2;
        byte[] part1 = new byte[half];
        byte[] part2 = new byte[three.length - half];
        System.arraycopy(three, 0, part1, 0, half);
        System.arraycopy(three, half, part2, 0, three.length - half);
        vad.feed(part1);
        vad.feed(part2);
        assertThat(rec.events).containsExactly("start");
    }

    @Test
    void reset_allowsRetrigger()
    {
        Rec rec = new Rec();
        VoiceActivityDetector vad = new VoiceActivityDetector(16000, rec);
        vad.feed(sine16k(3));
        assertThat(rec.events).containsExactly("start");
        vad.reset();
        assertThat(vad.isSpeaking()).isFalse();
        vad.feed(silence16k(30));
        assertThat(rec.events).containsExactly("start"); // 语音态被 reset 抹除，不补发 end
        vad.feed(sine16k(3));
        assertThat(rec.events).containsExactly("start", "start");
    }

    @Test
    void eightKhz_frameLength()
    {
        Rec rec = new Rec();
        VoiceActivityDetector vad = new VoiceActivityDetector(8000, rec);
        // 8kHz 20ms=160 样本=320 字节；喂 3 帧 440Hz 正弦
        byte[] pcm = new byte[3 * 160 * 2];
        for (int i = 0; i < 3 * 160; i++)
        {
            short v = (short) (10000 * Math.sin(2 * Math.PI * 440 * i / 8000.0));
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) (v >> 8);
        }
        vad.feed(pcm);
        assertThat(rec.events).containsExactly("start");
    }
}
