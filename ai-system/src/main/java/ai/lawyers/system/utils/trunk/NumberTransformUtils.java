package ai.lawyers.system.utils.trunk;

import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;

/**
 * 号码变换工具
 *
 * 不同运营商中继对被叫号码格式要求不同（是否加 0、是否要出局字冠、
 * 是否需要去掉 +86 等），此处按线路配置统一处理。
 */
public class NumberTransformUtils
{
    private NumberTransformUtils() {}

    /**
     * 清洗号码：去掉空格、横线、括号、国际区号前缀。
     */
    public static String normalize(String number)
    {
        if (number == null)
        {
            return "";
        }
        String n = number.replaceAll("[\\s\\-()（）]", "");
        if (n.startsWith("+86"))
        {
            n = n.substring(3);
        }
        else if (n.startsWith("0086"))
        {
            n = n.substring(4);
        }
        else if (n.startsWith("86") && n.length() == 13)
        {
            // 8613800138000 形式
            n = n.substring(2);
        }
        return n;
    }

    /**
     * 按线路配置对被叫号码做出局变换：先清洗，再去前 N 位，最后加前缀。
     */
    public static String transform(String calleeNumber, AiCallTrunk trunk)
    {
        String n = normalize(calleeNumber);
        if (trunk == null)
        {
            return n;
        }
        Integer strip = trunk.getStripDigits();
        if (strip != null && strip > 0 && n.length() > strip)
        {
            n = n.substring(strip);
        }
        String prefix = trunk.getAddPrefix();
        if (prefix != null && !prefix.isEmpty())
        {
            n = prefix + n;
        }
        String callerPrefix = trunk.getCallerPrefix();
        if (callerPrefix != null && !callerPrefix.isEmpty())
        {
            n = callerPrefix + n;
        }
        return n;
    }

    /**
     * 解析主叫显号：优先使用业务指定，其次线路配置。
     */
    public static String resolveCaller(String callerNumber, AiCallTrunk trunk)
    {
        if (callerNumber != null && !callerNumber.trim().isEmpty())
        {
            return normalize(callerNumber);
        }
        if (trunk != null && trunk.getCallerDisplay() != null && !trunk.getCallerDisplay().isEmpty())
        {
            return normalize(trunk.getCallerDisplay());
        }
        return "";
    }

    /**
     * 是否为合法的可外呼号码（手机号 11 位 / 固话 7~12 位）。
     */
    public static boolean isValid(String number)
    {
        String n = normalize(number);
        if (n.isEmpty() || !n.matches("\\d+"))
        {
            return false;
        }
        return n.length() >= 5 && n.length() <= 15;
    }

    /**
     * 号码脱敏，用于日志与前端展示：138****8000
     */
    public static String mask(String number)
    {
        String n = normalize(number);
        if (n.length() < 7)
        {
            return n;
        }
        int keepHead = 3;
        int keepTail = 4;
        StringBuilder sb = new StringBuilder(n.substring(0, keepHead));
        for (int i = 0; i < n.length() - keepHead - keepTail; i++)
        {
            sb.append('*');
        }
        sb.append(n.substring(n.length() - keepTail));
        return sb.toString();
    }
}
