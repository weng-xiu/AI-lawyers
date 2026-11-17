package ai.lawyers.framework.websocket.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.lawyers.system.service.lawyers.voice.emotion.VoiceRiskActionService;

/**
 * 情绪/意图实时联动测试：来电者通道门禁、urgent/negative 去重与覆盖、
 * 空文本忽略、emotion 帧下发。
 *
 * @author ai-lawyers
 */
class VoiceEmotionWatcherTest
{
    private final VoiceRiskActionService action = mock(VoiceRiskActionService.class);

    @Test
    void 空文本不触发识别()
    {
        RecordingHost host = new RecordingHost(false);
        VoiceEmotionWatcher watcher = new VoiceEmotionWatcher(action, host, "s-1", "caller");

        watcher.detect(null, true);
        watcher.detect("   ", true);

        verifyNoInteractions(action);
        assertThat(host.frames).isEmpty();
    }

    @Test
    void 坐席通道不挂钩风险识别()
    {
        RecordingHost host = new RecordingHost(false);
        VoiceEmotionWatcher watcher = new VoiceEmotionWatcher(action, host, "s-1", "agent");

        watcher.detect("救命", true);
        watcher.detect("我要投诉", true);

        verifyNoInteractions(action);
        assertThat(host.frames).isEmpty();
    }

    @Test
    void robot模式下来电者语音挂钩识别()
    {
        when(action.handleEmotion(anyString(), any(), anyString(), anyList(), anyString())).thenReturn(5L);
        RecordingHost host = new RecordingHost(true);
        VoiceEmotionWatcher watcher = new VoiceEmotionWatcher(action, host, "s-2", "agent");

        watcher.detect("救命", true);

        verify(action).handleEmotion("urgent", "URGENT", "救命", List.of("救命"), "s-2");
        assertThat(host.frames).hasSize(1);
        assertThat(host.frames.get(0))
                .contains("\"type\":\"emotion\"")
                .contains("\"level\":\"urgent\"")
                .contains("\"warningId\":5");
    }

    @Test
    void 来电者紧急词partial即触发且每通话只动作一次()
    {
        when(action.handleEmotion(anyString(), any(), anyString(), anyList(), anyString())).thenReturn(9L);
        RecordingHost host = new RecordingHost(false);
        VoiceEmotionWatcher watcher = new VoiceEmotionWatcher(action, host, "s-3", "caller");

        watcher.detect("我不想活了", false);
        watcher.detect("我真的不想活了", true);

        verify(action, times(1)).handleEmotion("urgent", "URGENT", "我不想活了",
                List.of("不想活"), "s-3");
        assertThat(host.frames).hasSize(1);
    }

    @Test
    void 负面情绪仅final生效且每通话只动作一次()
    {
        when(action.handleEmotion(anyString(), any(), anyString(), anyList(), anyString())).thenReturn(7L);
        RecordingHost host = new RecordingHost(false);
        VoiceEmotionWatcher watcher = new VoiceEmotionWatcher(action, host, "s-4", "caller");

        // partial 不动作
        watcher.detect("我要投诉你们", false);
        verify(action, never()).handleEmotion(anyString(), any(), anyString(), anyList(), anyString());
        assertThat(host.frames).isEmpty();

        watcher.detect("我要投诉你们", true);
        watcher.detect("我要投诉你们乱收费", true);

        verify(action, times(1)).handleEmotion("negative", "COMPLAINT", "我要投诉你们",
                List.of("投诉"), "s-4");
        assertThat(host.frames).hasSize(1);
        assertThat(host.frames.get(0)).contains("\"level\":\"negative\"");
    }

    @Test
    void urgent命中后negative不再动作()
    {
        when(action.handleEmotion(anyString(), any(), anyString(), anyList(), anyString())).thenReturn(3L);
        RecordingHost host = new RecordingHost(false);
        VoiceEmotionWatcher watcher = new VoiceEmotionWatcher(action, host, "s-5", "caller");

        watcher.detect("救命", true);
        watcher.detect("我要投诉", true);

        verify(action, times(1)).handleEmotion(anyString(), any(), anyString(), anyList(), anyString());
        verify(action).handleEmotion("urgent", "URGENT", "救命", List.of("救命"), "s-5");
        assertThat(host.frames).hasSize(1);
    }

    @Test
    void caller角色大小写不敏感()
    {
        when(action.handleEmotion(anyString(), any(), anyString(), anyList(), anyString())).thenReturn(1L);
        RecordingHost host = new RecordingHost(false);
        VoiceEmotionWatcher watcher = new VoiceEmotionWatcher(action, host, "s-6", "CALLER");

        watcher.detect("快来救我", true);

        verify(action).handleEmotion("urgent", "URGENT", "快来救我", List.of("快来救我"), "s-6");
        assertThat(host.frames).hasSize(1);
    }

    /** 记录下发帧并控制 robot 模式的宿主 */
    private static final class RecordingHost implements VoiceEmotionWatcher.Host
    {
        private final List<String> frames = new ArrayList<>();

        private final boolean robotMode;

        private RecordingHost(boolean robotMode)
        {
            this.robotMode = robotMode;
        }

        @Override
        public void send(String frame)
        {
            frames.add(frame);
        }

        @Override
        public boolean robotMode()
        {
            return robotMode;
        }
    }
}
