package ai.lawyers.system.enums;

/**
 * 线路健康状态枚举
 *
 * 状态机：
 *   UNKNOWN --探测成功--> NORMAL
 *   NORMAL  --质量下降--> DEGRADED --持续失败--> FAULT --超过阈值--> CIRCUIT_OPEN(熔断)
 *   CIRCUIT_OPEN --冷却期结束且探测成功--> NORMAL
 */
public enum TrunkHealthEnum
{
    /** 未知（尚未探测） */
    UNKNOWN("0", "未知"),

    /** 正常 */
    NORMAL("1", "正常"),

    /** 亚健康（质量指标低于告警阈值，但仍可用） */
    DEGRADED("2", "亚健康"),

    /** 故障（探测失败或连续呼叫失败） */
    FAULT("3", "故障"),

    /** 熔断（自动摘除，冷却期内不参与选路） */
    CIRCUIT_OPEN("4", "熔断");

    private final String code;

    private final String info;

    TrunkHealthEnum(String code, String info)
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

    /** 是否可参与选路 */
    public static boolean isSelectable(String code)
    {
        return NORMAL.code.equals(code) || DEGRADED.code.equals(code) || UNKNOWN.code.equals(code);
    }

    public static String infoOf(String code)
    {
        for (TrunkHealthEnum e : values())
        {
            if (e.code.equals(code))
            {
                return e.info;
            }
        }
        return UNKNOWN.info;
    }
}
