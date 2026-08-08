package ai.lawyers.system.enums;

/**
 * 拨号状态枚举（对应 ai_call_dial_log.dial_status）
 */
public enum DialStatusEnum
{
    /** 排队等待线路资源 */
    QUEUING("0", "排队中"),

    /** 已下发网关，拨号中 */
    DIALING("1", "拨号中"),

    /** 对端振铃 */
    RINGING("2", "振铃"),

    /** 已接通 */
    ANSWERED("3", "已接通"),

    /** 正常挂断 */
    HANGUP("4", "已挂断"),

    /** 呼叫失败 */
    FAILED("5", "失败"),

    /** 超时无应答 */
    TIMEOUT("6", "超时"),

    /** 被拒接 */
    REJECTED("7", "被拒"),

    /** 占线 */
    BUSY("8", "占线"),

    /** 空号/无效号码 */
    INVALID_NUMBER("9", "空号");

    private final String code;

    private final String info;

    DialStatusEnum(String code, String info)
    {
        this.code = code;
        this.info = info;
    }

    public String getCode()
    {
        return code;
    }

    public String getInfo()
    {
        return info;
    }

    /** 是否为终态 */
    public static boolean isFinal(String code)
    {
        return HANGUP.code.equals(code) || FAILED.code.equals(code) || TIMEOUT.code.equals(code)
                || REJECTED.code.equals(code) || BUSY.code.equals(code) || INVALID_NUMBER.code.equals(code);
    }

    /** 该状态是否应判定为"线路侧故障"（用于故障切换决策） */
    public static boolean isTrunkFault(String code)
    {
        return FAILED.code.equals(code) || TIMEOUT.code.equals(code);
    }

    public static String infoOf(String code)
    {
        for (DialStatusEnum e : values())
        {
            if (e.code.equals(code))
            {
                return e.info;
            }
        }
        return "未知";
    }
}
