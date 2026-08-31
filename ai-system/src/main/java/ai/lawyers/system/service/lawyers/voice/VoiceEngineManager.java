package ai.lawyers.system.service.lawyers.voice;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.metrics.HotlineMetrics;

/**
 * 语音引擎统一入口：按 {@link VoiceModelEnum} 选择 ASR/TTS 实现，
 * 未配置或调用失败时降级到本地 Mock 引擎，保证 IVR 流程不中断。
 */
@Service
public class VoiceEngineManager
{
    private static final Logger log = LoggerFactory.getLogger(VoiceEngineManager.class);

    private final VoiceProperties properties;

    private final DashScopeTtsEngine dashScopeTtsEngine;

    private final OpenAiCompatibleAsrEngine openAiCompatibleAsrEngine;

    private final MockVoiceEngine mockVoiceEngine;

    /** T5-1：ASR 调用指标（未引入 micrometer 时内部静默） */
    @Autowired(required = false)
    private HotlineMetrics metrics;

    public VoiceEngineManager(VoiceProperties properties,
                              DashScopeTtsEngine dashScopeTtsEngine,
                              OpenAiCompatibleAsrEngine openAiCompatibleAsrEngine,
                              MockVoiceEngine mockVoiceEngine)
    {
        this.properties = properties;
        this.dashScopeTtsEngine = dashScopeTtsEngine;
        this.openAiCompatibleAsrEngine = openAiCompatibleAsrEngine;
        this.mockVoiceEngine = mockVoiceEngine;
    }

    public VoiceModelEnum defaultEngine()
    {
        return VoiceModelEnum.of(properties.getEngine());
    }

    public byte[] synthesize(VoiceModelEnum engine, String text, String format, int sampleRate,
                             Map<String, Object> options)
    {
        TtsEngine ttsEngine = resolveTts(engine);
        try
        {
            byte[] result = ttsEngine.synthesize(text, format, sampleRate, options);
            return result == null ? new byte[0] : result;
        }
        catch (Exception e)
        {
            log.warn("语音合成失败，降级到Mock引擎 engine={} error={}", engine, e.getMessage());
            return mockVoiceEngine.synthesize(text, format, sampleRate, options);
        }
    }

    public String transcribe(VoiceModelEnum engine, byte[] audio, String format, int sampleRate,
                             Map<String, Object> options)
    {
        AsrEngine asrEngine = resolveAsr(engine);
        // T5-1：走到 Mock（未配置 ASR 或显式 mock 引擎）不计外部调用指标，避免污染成功率
        boolean realEngine = asrEngine != mockVoiceEngine;
        long start = System.currentTimeMillis();
        try
        {
            String result = asrEngine.transcribe(audio, format, sampleRate, options);
            if (metrics != null && realEngine)
            {
                metrics.incrementAi("asr", "success");
                metrics.recordAiFirstResponse(System.currentTimeMillis() - start);
            }
            return result == null ? "" : result;
        }
        catch (Exception e)
        {
            // T5-1：真实 ASR 失败降级 Mock，计 fallback（降级率）
            if (metrics != null && realEngine)
            {
                metrics.incrementAi("asr", "fallback");
            }
            log.warn("语音识别失败，降级到Mock引擎 engine={} error={}", engine, e.getMessage());
            return mockVoiceEngine.transcribe(audio, format, sampleRate, options);
        }
    }

    private TtsEngine resolveTts(VoiceModelEnum engine)
    {
        if (VoiceModelEnum.DASHSCOPE == engine)
        {
            return dashScopeTtsEngine;
        }
        // ali/dianxin 预留：接入对应 SDK/HTTP 客户端后在此返回具体实现
        return mockVoiceEngine;
    }

    private AsrEngine resolveAsr(VoiceModelEnum engine)
    {
        if (engine != null && VoiceModelEnum.MOCK != engine
                && StringUtils.isNotEmpty(properties.getAsrBaseUrl()))
        {
            // 阿里云 NLS / 电信 / DashScope 的 HTTP 网关均可适配为 OpenAI 兼容转写协议
            return openAiCompatibleAsrEngine;
        }
        return mockVoiceEngine;
    }
}
