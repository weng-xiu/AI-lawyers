package ai.lawyers.system.enums;

/**
 * AI律师辅助会话状态枚举（独立状态机）
 *
 * 与人工通话状态（AiCallAgentStatus.call_status）完全独立，互不影响。
 * 人工通话状态：0=空闲 1=通话中 2=保持 3=咨询中 4=三方 5=话后整理
 * AI辅助状态：见下方各枚举值。
 */
public enum AiAssistSessionStatusEnum
{
    INIT("0", "初始化"),
    ANALYZING("1", "分析中"),
    RECOMMENDING("2", "推荐中"),
    SUMMARIZED("3", "已小结"),
    ENDED("4", "已结束"),
    ERROR("9", "异常");

    private final String code;
    private final String desc;

    AiAssistSessionStatusEnum(String code, String desc)
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

    public static AiAssistSessionStatusEnum getByCode(String code)
    {
        for (AiAssistSessionStatusEnum e : values())
        {
            if (e.code.equals(code))
            {
                return e;
            }
        }
        return null;
    }
}
