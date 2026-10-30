package ai.lawyers.system.service.lawyers.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F8 知识检索问句文本处理：脱敏、截断、归一化与哈希。
 *
 * <p>查询日志只用于运营分析，不应把完整敏感信息落库：</p>
 * <ol>
 *   <li>{@link #maskForStore(String)}：先脱敏手机号/身份证/银行卡/邮箱，再截断
 *       {@link #MAX_STORE_LENGTH} 字符，供 {@code query_text} 展示；</li>
 *   <li>{@link #normalize(String)}：去空白与标点后转小写，使“劳动合同？怎么办”与
 *       “劳动合同怎么办”归入同一问句组；</li>
 *   <li>{@link #hash(String)}：对归一化文本取 SHA-256 前 32 位十六进制，
 *       仅以哈希分组与去重，不按原文做模糊检索。</li>
 * </ol>
 *
 * @author ai-lawyers
 */
public final class KnowledgeQueryTextUtils
{
    /** 落库问句最大长度（与 ai_knowledge_query_log.query_text 列宽匹配） */
    public static final int MAX_STORE_LENGTH = 500;

    /** 邮箱：保留首字符与域名，其余打码 */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 身份证：18 位（末位可为 X）或 15 位，避免与其它数字串误匹配 */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{17}[\\dXx]|\\d{15})(?!\\d)");

    /** 手机号：1 开头的 11 位数字 */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** 银行卡：16~19 位连续数字 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");

    /** 固话：区号 3~4 位 + 7~8 位号码（可带横线） */
    private static final Pattern LANDLINE = Pattern.compile("(?<!\\d)0\\d{2,3}-?\\d{7,8}(?!\\d)");

    private KnowledgeQueryTextUtils()
    {
    }

    /**
     * 落库文本：脱敏 + 截断。空文本返回空串。
     */
    public static String maskForStore(String raw)
    {
        if (isBlank(raw))
        {
            return "";
        }
        String masked = raw.trim();
        masked = EMAIL.matcher(masked).replaceAll(m -> Matcher.quoteReplacement(maskEmail(m.group())));
        masked = ID_CARD.matcher(masked).replaceAll(m -> Matcher.quoteReplacement(maskNumber(m.group())));
        masked = PHONE.matcher(masked).replaceAll(m -> Matcher.quoteReplacement(maskNumber(m.group())));
        masked = BANK_CARD.matcher(masked).replaceAll(m -> Matcher.quoteReplacement(maskNumber(m.group())));
        masked = LANDLINE.matcher(masked).replaceAll(m -> Matcher.quoteReplacement(maskNumber(m.group())));
        return masked.length() > MAX_STORE_LENGTH ? masked.substring(0, MAX_STORE_LENGTH) : masked;
    }

    /**
     * 归一化：去首尾空白、转小写、去掉所有空白/标点/符号，用于稳定分组。
     */
    public static String normalize(String raw)
    {
        if (isBlank(raw))
        {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}\\p{S}]", "");
    }

    /**
     * 问句哈希：归一化文本 SHA-256 前 32 位十六进制；归一化后为空返回空串。
     */
    public static String hash(String raw)
    {
        String normalized = normalize(raw);
        if (normalized.isEmpty())
        {
            return "";
        }
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++)
            {
                int value = bytes[i] & 0xFF;
                sb.append(Character.forDigit(value >> 4, 16)).append(Character.forDigit(value & 0xF, 16));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            // JDK 必带 SHA-256，此分支仅为编译兜底
            return Integer.toHexString(normalized.hashCode());
        }
    }

    public static boolean isBlank(String raw)
    {
        return raw == null || raw.trim().isEmpty();
    }

    /** 长数字脱敏：保留前 3 位与后 4 位 */
    private static String maskNumber(String digits)
    {
        int len = digits.length();
        if (len < 7)
        {
            return digits;
        }
        StringBuilder sb = new StringBuilder(len);
        sb.append(digits, 0, 3);
        for (int i = 0; i < len - 7; i++)
        {
            sb.append('*');
        }
        sb.append(digits, len - 4, len);
        return sb.toString();
    }

    /** 邮箱脱敏：l***@domain.com */
    private static String maskEmail(String email)
    {
        int at = email.indexOf('@');
        if (at <= 0)
        {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        return local.charAt(0) + "***" + domain;
    }
}
