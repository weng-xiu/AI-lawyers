package ai.lawyers.system.service.lawyers.voice;

/**
 * 语音引擎枚举（对齐 SmartCall 的 VoiceModelEnum 设计）。
 *
 * <p>ASR/TTS 均基于接口抽象，可按节点选择引擎；ali/dianxin 预留，
 * dashscope 已提供 HTTP 实现，mock 用于无外网/无密钥的本地联调兜底。</p>
 */
public enum VoiceModelEnum
{
    /** 阿里云智能语音（NLS） */
    ALI("ali", "阿里云"),

    /** 电信 AI 语音 */
    DIANXIN("dianxin", "电信"),

    /** 通义千问 DashScope */
    DASHSCOPE("dashscope", "通义千问"),

    /** 本地模拟引擎（默认，仅记录日志） */
    MOCK("mock", "本地模拟");

    private final String code;

    private final String desc;

    VoiceModelEnum(String code, String desc)
    {
        this.code = code;
        this.desc = desc;
    }

    public String getCode()
    {
        return code;
    }

    public String getDesc()
    {
        return desc;
    }

    public static VoiceModelEnum of(String code)
    {
        if (code == null)
        {
            return MOCK;
        }
        for (VoiceModelEnum value : values())
        {
            if (value.code.equalsIgnoreCase(code.trim()))
            {
                return value;
            }
        }
        return MOCK;
    }
}
