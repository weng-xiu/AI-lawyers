package ai.lawyers.framework.websocket.voice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.system.service.lawyers.voice.VoiceProperties;

/**
 * 流式语音引擎注册表（P3-A2/A3）。
 *
 * <p>按引擎编码创建流式 ASR/TTS 引擎实例：mock 始终可用（联调兜底）；
 * dashscope 需配置 {@code voice.dashscope-api-key}，否则返回 null 由会话层
 * 显式回 ENGINE_UNAVAILABLE（禁止静默降级为 Mock 造成"假成功"）。</p>
 *
 * <p>ASR 引擎有状态（open/feed/close 绑定单会话），每次创建新实例；
 * TTS 引擎无状态（每次合成独立连接），可复用实例。</p>
 *
 * @author ai-lawyers
 */
@Component
public class VoiceEngineRegistry
{
    private final VoiceProperties properties;

    @Autowired
    public VoiceEngineRegistry(VoiceProperties properties)
    {
        this.properties = properties;
    }

    /** 测试/兜底用：无配置时仅 mock 可用 */
    public VoiceEngineRegistry()
    {
        this.properties = null;
    }

    /**
     * 创建流式 ASR 引擎实例。
     *
     * @param code 引擎编码（mock/dashscope）
     * @return 引擎实例；未知编码或凭证缺失返回 null
     */
    public StreamAsrEngine createAsr(String code)
    {
        if (StringUtils.isEmpty(code) || MockStreamAsrEngine.ENGINE_CODE.equalsIgnoreCase(code))
        {
            return new MockStreamAsrEngine();
        }
        if (DashScopeStreamAsrEngine.ENGINE_CODE.equalsIgnoreCase(code))
        {
            return hasDashscopeKey() ? new DashScopeStreamAsrEngine(properties) : null;
        }
        return null;
    }

    /**
     * 获取流式 TTS 引擎实例。
     *
     * @param code 引擎编码（mock/dashscope）
     * @return 引擎实例；未知编码或凭证缺失返回 null
     */
    public StreamTtsEngine createTts(String code)
    {
        if (StringUtils.isEmpty(code) || MockStreamTtsEngine.ENGINE_CODE.equalsIgnoreCase(code))
        {
            return MockStreamTtsEngine.INSTANCE;
        }
        if (DashScopeStreamTtsEngine.ENGINE_CODE.equalsIgnoreCase(code))
        {
            return hasDashscopeKey() ? new DashScopeStreamTtsEngine(properties) : null;
        }
        return null;
    }

    private boolean hasDashscopeKey()
    {
        return properties != null && StringUtils.isNotEmpty(properties.getDashscopeApiKey());
    }
}
