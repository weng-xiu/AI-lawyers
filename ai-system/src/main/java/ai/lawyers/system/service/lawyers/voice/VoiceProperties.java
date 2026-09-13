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

    /**
     * F1 粤语（yue-CN）TTS 发音人，未配置时粤语路由回退默认发音人。
     * 真实粤语 Provider 接入前为预留配置（POC 桩）。
     */
    private String cantoneseVoice = "";

    /** F1 粤语 TTS 模型（部分 Provider 粤语需独立模型） */
    private String cantoneseTtsModel = "";

    /** F1 粤语 ASR 语种码（Whisper 协议用 yue，供应商私有协议按其文档调整） */
    private String cantoneseAsrLanguage = "yue";

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

    public String getCantoneseVoice()
    {
        return cantoneseVoice;
    }

    public void setCantoneseVoice(String cantoneseVoice)
    {
        this.cantoneseVoice = cantoneseVoice;
    }

    public String getCantoneseTtsModel()
    {
        return cantoneseTtsModel;
    }

    public void setCantoneseTtsModel(String cantoneseTtsModel)
    {
        this.cantoneseTtsModel = cantoneseTtsModel;
    }

    public String getCantoneseAsrLanguage()
    {
        return cantoneseAsrLanguage;
    }

    public void setCantoneseAsrLanguage(String cantoneseAsrLanguage)
    {
        this.cantoneseAsrLanguage = cantoneseAsrLanguage;
    }
}
