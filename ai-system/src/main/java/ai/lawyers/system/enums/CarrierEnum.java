package ai.lawyers.system.enums;

/**
 * 运营商枚举
 *
 * 用于被叫号码归属识别与线路选路。编码与数据库 ai_call_trunk.carrier /
 * ai_number_segment.carrier 字段保持一致。
 */
public enum CarrierEnum
{
    /** 中国移动 */
    CHINA_MOBILE("CM", "中国移动"),

    /** 中国联通 */
    CHINA_UNICOM("CU", "中国联通"),

    /** 中国电信 */
    CHINA_TELECOM("CT", "中国电信"),

    /** 中国广电 */
    CHINA_BROADNET("CB", "中国广电"),

    /** 虚拟运营商 */
    VIRTUAL("VI", "虚拟运营商"),

    /** 未知/任意（兜底线路可承载任意运营商） */
    UNKNOWN("00", "未知");

    private final String code;

    private final String info;

    CarrierEnum(String code, String info)
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

    public static CarrierEnum fromCode(String code)
    {
        if (code == null || code.isEmpty())
        {
            return UNKNOWN;
        }
        for (CarrierEnum c : values())
        {
            if (c.code.equalsIgnoreCase(code))
            {
                return c;
            }
        }
        return UNKNOWN;
    }

    public static String infoOf(String code)
    {
        return fromCode(code).getInfo();
    }
}
