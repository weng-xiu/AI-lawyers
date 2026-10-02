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

    /** A2：DashScope 流式 ASR 模型（全双工 WebSocket，paraformer-realtime 系列） */
    private String dashscopeAsrModel = "paraformer-realtime-v2";

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

    /** A4：/ws/voice 服务端 VAD（能量+过零率）开关，关闭后不做自动 barge-in 打断 */
    private boolean vadEnabled = true;

    /** P2-12：Silero VAD 影子模式开关（默认关闭）。开启后旧 VAD 仍为主决策，Silero 判定仅记录日志打标 */
    private boolean vadShadowEnabled = false;

    /** P2-12：Silero VAD 模型路径（classpath: 前缀或文件路径），默认 models/silero_vad_int8.onnx */
    private String vadShadowModelPath = "models/silero_vad_int8.onnx";

    /** P2-12：Silero VAD 语音概率阈值（0~1），默认 0.5 */
    private float vadShadowThreshold = 0.5f;

    /** P2-12：会话明细抽样率（0~1，0=仅日统计不落会话明细，1=全量抽样） */
    private double vadShadowSessionSampleRate = 0d;

    public boolean isVadEnabled()
    {
        return vadEnabled;
    }

    public void setVadEnabled(boolean vadEnabled)
    {
        this.vadEnabled = vadEnabled;
    }

    public boolean isVadShadowEnabled()
    {
        return vadShadowEnabled;
    }

    public void setVadShadowEnabled(boolean vadShadowEnabled)
    {
        this.vadShadowEnabled = vadShadowEnabled;
    }

    public String getVadShadowModelPath()
    {
        return vadShadowModelPath;
    }

    public void setVadShadowModelPath(String vadShadowModelPath)
    {
        this.vadShadowModelPath = vadShadowModelPath;
    }

    public float getVadShadowThreshold()
    {
        return vadShadowThreshold;
    }

    public void setVadShadowThreshold(float vadShadowThreshold)
    {
        this.vadShadowThreshold = vadShadowThreshold;
    }

    public double getVadShadowSessionSampleRate()
    {
        return vadShadowSessionSampleRate;
    }

    public void setVadShadowSessionSampleRate(double vadShadowSessionSampleRate)
    {
        this.vadShadowSessionSampleRate = Math.max(0d, Math.min(1d, vadShadowSessionSampleRate));
    }

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

    public String getDashscopeAsrModel()
    {
        return dashscopeAsrModel;
    }

    public void setDashscopeAsrModel(String dashscopeAsrModel)
    {
        this.dashscopeAsrModel = dashscopeAsrModel;
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
