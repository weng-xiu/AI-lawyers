package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ai.lawyers.system.service.lawyers.voice.VoiceProperties;

/**
 * DashScope 流式引擎边界行为单测（P3-A2/A3，无凭证不连外网）。
 *
 * @author ai-lawyers
 */
class DashScopeStreamEngineTest
{
    @Test
    void asrOpenWithoutApiKey_shouldCallbackEngineUnavailable()
    {
        VoiceProperties props = new VoiceProperties(); // apiKey 为空
        DashScopeStreamAsrEngine engine = new DashScopeStreamAsrEngine(props);
        List<String> errors = new ArrayList<>();
        engine.open(new VoiceAsrContext("s1", "agent", "pcm", 16000), new AsrStreamCallback()
        {
            @Override
            public void onPartial(String text)
            {
            }

            @Override
            public void onFinal(String text)
            {
            }

            @Override
            public void onError(String code, String message)
            {
                errors.add(code);
            }
        });
        assertThat(errors).containsExactly("ENGINE_UNAVAILABLE");
    }

    @Test
    void asrClose_shouldBeIdempotent()
    {
        VoiceProperties props = new VoiceProperties();
        DashScopeStreamAsrEngine engine = new DashScopeStreamAsrEngine(props);
        engine.close();
        engine.close(); // 不抛异常即幂等
    }

    @Test
    void asrFeedAfterClose_shouldBeSilentlyIgnored()
    {
        VoiceProperties props = new VoiceProperties();
        DashScopeStreamAsrEngine engine = new DashScopeStreamAsrEngine(props);
        engine.close();
        engine.feed(new byte[320]); // close 后静默忽略，不抛异常
    }

    @Test
    void ttsWithoutApiKey_shouldErrorAndReturnCancelledHandle()
    {
        VoiceProperties props = new VoiceProperties();
        DashScopeStreamTtsEngine engine = new DashScopeStreamTtsEngine(props);
        List<String> errors = new ArrayList<>();
        StreamSynthesis handle = engine.synthesizeStream("你好", null, 16000, new TtsStreamCallback()
        {
            @Override
            public void onAudio(int seq, int chunkCount, byte[] pcm)
            {
            }

            @Override
            public void onEnd()
            {
            }

            @Override
            public void onError(String code, String message)
            {
                errors.add(code);
            }
        });
        assertThat(errors).containsExactly("ENGINE_UNAVAILABLE");
        assertThat(handle.isCancelled()).isTrue();
        handle.cancel(); // 幂等不抛
    }
}
