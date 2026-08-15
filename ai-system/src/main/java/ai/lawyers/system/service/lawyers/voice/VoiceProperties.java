package ai.lawyers.system.service.lawyers.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语音引擎配置（application.yml 的 voice 前缀）。
 *
 * <pre>
 * voice:
 *   engine: mock
 *   dashscope-api-key:
 *   dashscope-base-url: https://dashscope.aliyuncs.com/api/v1
 *   dashscope-tts-model: cosyvoice-v1
 *   dashscope-voice: longxiaochun
 *   asr-base-url: http://localhost:8000/v1
 *   asr-api-key:
 *   asr-model: whisper-1
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "voice")
public class VoiceProperties
{
    /** 默认语音引擎（ali/dianxin/dashscope/mock） */
    private String engine = "mock";

    private String dashscopeApiKey = "";

    private String dashscopeBaseUrl = "https://dashscope.aliyuncs.com/api/v1";

    private String dashscopeTtsModel = "cosyvoice-v1";

    private String dashscopeVoice = "longxiaochun";

    private String dashscopeFormat = "wav";

    private int dashscopeSampleRate = 16000;

    /** OpenAI/Whisper 兼容 ASR 服务地址（可指向 faster-whisper、vLLM 等） */
    private String asrBaseUrl = "http://localhost:8000/v1";

    private String asrApiKey = "";

    private String asrModel = "whisper-1";

    private String asrLanguage = "zh";

    public String getEngine()
    {
        return engine;
    }

    public void setEngine(String engine)
    {
        this.engine = engine;
    }

    public String getDashscopeApiKey()
    {
        return dashscopeApiKey;
    }

    public void setDashscopeApiKey(String dashscopeApiKey)
    {
        this.dashscopeApiKey = dashscopeApiKey;
    }

    public String getDashscopeBaseUrl()
    {
        return dashscopeBaseUrl;
    }

    public void setDashscopeBaseUrl(String dashscopeBaseUrl)
    {
        this.dashscopeBaseUrl = dashscopeBaseUrl;
    }

    public String getDashscopeTtsModel()
    {
        return dashscopeTtsModel;
    }

    public void setDashscopeTtsModel(String dashscopeTtsModel)
    {
        this.dashscopeTtsModel = dashscopeTtsModel;
    }

    public String getDashscopeVoice()
    {
        return dashscopeVoice;
    }

    public void setDashscopeVoice(String dashscopeVoice)
    {
        this.dashscopeVoice = dashscopeVoice;
    }

    public String getDashscopeFormat()
    {
        return dashscopeFormat;
    }

    public void setDashscopeFormat(String dashscopeFormat)
    {
        this.dashscopeFormat = dashscopeFormat;
    }

    public int getDashscopeSampleRate()
    {
        return dashscopeSampleRate;
    }

    public void setDashscopeSampleRate(int dashscopeSampleRate)
    {
        this.dashscopeSampleRate = dashscopeSampleRate;
    }

    public String getAsrBaseUrl()
    {
        return asrBaseUrl;
    }

    public void setAsrBaseUrl(String asrBaseUrl)
    {
        this.asrBaseUrl = asrBaseUrl;
    }

    public String getAsrApiKey()
    {
        return asrApiKey;
    }

    public void setAsrApiKey(String asrApiKey)
    {
        this.asrApiKey = asrApiKey;
    }

    public String getAsrModel()
    {
        return asrModel;
    }

    public void setAsrModel(String asrModel)
    {
        this.asrModel = asrModel;
    }

    public String getAsrLanguage()
    {
        return asrLanguage;
    }

    public void setAsrLanguage(String asrLanguage)
    {
        this.asrLanguage = asrLanguage;
    }
}
