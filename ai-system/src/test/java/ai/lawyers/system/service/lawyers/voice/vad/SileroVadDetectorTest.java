package ai.lawyers.system.service.lawyers.voice.vad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * P2-12 方案A：Silero VAD 影子检测器单测。
 *
 * <p>测试环境无 ONNX Runtime / 模型文件，构造时必然降级 Mock
 * （与旧 VAD 同能量+过零率判据），覆盖 Mock 链路的状态机语义；
 * 真实 ONNX 推理路径属部署侧验证。</p>
 *
 * @author ai-lawyers
 */
class SileroVadDetectorTest
{
    private static final int RATE = 16000;

    /** 构造一帧 20ms S16LE PCM：voiced=false 全零静默；voiced=true 低频方波（ZCR≈0.2、RMS=5000） */
    private static byte[] frame(boolean voiced)
    {
        int samples = RATE * 20 / 1000;
        byte[] pcm = new byte[samples * 2];
        if (voiced)
        {
            for (int i = 0; i < samples; i++)
            {
                short s = (short) (((i / 5) & 1) == 0 ? 5000 : -5000);
                pcm[i * 2] = (byte) (s & 0xFF);
                pcm[i * 2 + 1] = (byte) (s >> 8);
            }
        }
        return pcm;
    }

    private static SileroVadDetector newDetector(AtomicInteger starts, AtomicInteger ends)
    {
        return new SileroVadDetector(RATE, new SileroVadDetector.ShadowListener()
        {
            @Override
            public void onShadowSpeechStart(long detectMs)
            {
                starts.incrementAndGet();
            }

            @Override
            public void onShadowSpeechEnd()
            {
                ends.incrementAndGet();
            }
        });
    }

    @Test
    void constructor_invalidSampleRate_throws()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new SileroVadDetector(12345, null));
    }

    @Test
    void constructor_withoutOnnxModel_fallsBackToMock()
    {
        SileroVadDetector detector = newDetector(new AtomicInteger(), new AtomicInteger());
        // classpath 无 onnxruntime 且 models/silero_vad_int8.onnx 不存在 → Mock 模式
        assertTrue(detector.isMockMode());
        assertFalse(detector.isOnnxAvailable());
    }

    @Test
    void feed_silence_neverTriggersStart()
    {
        AtomicInteger starts = new AtomicInteger();
        SileroVadDetector detector = newDetector(starts, new AtomicInteger());
        byte[] silence = frame(false);
        for (int i = 0; i < 30; i++)
        {
            detector.feed(silence);
        }
        assertEquals(0, starts.get());
        assertFalse(detector.isShadowSpeaking());
    }

    @Test
    void feed_threeVoicedFrames_triggersStartOnce()
    {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();
        SileroVadDetector detector = newDetector(starts, ends);
        byte[] voice = frame(true);
        detector.feed(voice);
        detector.feed(voice);
        assertFalse(detector.isShadowSpeaking(), "前两帧不应判起始");
        detector.feed(voice);
        assertTrue(detector.isShadowSpeaking());
        assertEquals(1, starts.get());
        assertEquals(0, ends.get());
    }

    @Test
    void feed_twentySilentFramesAfterVoice_triggersEnd()
    {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();
        SileroVadDetector detector = newDetector(starts, ends);
        byte[] voice = frame(true);
        byte[] silence = frame(false);
        for (int i = 0; i < 3; i++)
        {
            detector.feed(voice);
        }
        for (int i = 0; i < 20; i++)
        {
            detector.feed(silence);
        }
        assertFalse(detector.isShadowSpeaking());
        assertEquals(1, starts.get());
        assertEquals(1, ends.get());
    }

    @Test
    void reset_clearsSpeakingState()
    {
        AtomicInteger starts = new AtomicInteger();
        SileroVadDetector detector = newDetector(starts, new AtomicInteger());
        byte[] voice = frame(true);
        for (int i = 0; i < 3; i++)
        {
            detector.feed(voice);
        }
        assertTrue(detector.isShadowSpeaking());
        detector.reset();
        assertFalse(detector.isShadowSpeaking());
    }

    @Test
    void feed_nullOrEmpty_isNoOp()
    {
        SileroVadDetector detector = newDetector(new AtomicInteger(), new AtomicInteger());
        detector.feed(null);
        detector.feed(new byte[0]);
        assertFalse(detector.isShadowSpeaking());
    }
}
