package ai.lawyers.common.utils.sign;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ai.lawyers.common.utils.StringUtils;

/**
 * 网关/内部回调接口 HMAC-SHA256 签名校验工具（S7 安全收口）。
 *
 * <p>签名规则：{@code sign = Base64(HmacSHA256(secret, content + "." + timestamp))}，
 * 其中 content 为调用方与服务端约定的业务指纹（如 callUuid+event、agentId+userId），
 * timestamp 为毫秒时间戳；服务端校验时间戳窗口（默认 ±5 分钟）防重放。</p>
 *
 * <p>生产环境必须通过配置开启签名校验并注入强密钥；开发环境可关闭以方便联调。</p>
 *
 * @author ai-lawyers
 */
public final class CallbackSignUtils
{
    /** 默认时间戳窗口：5 分钟 */
    private static final long WINDOW_MILLIS = 5 * 60 * 1000L;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private CallbackSignUtils()
    {
    }

    /**
     * 生成签名（供网关侧/联调对照使用）。
     */
    public static String sign(String secret, String content, String timestamp)
    {
        try
        {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal((content + "." + timestamp).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("生成回调签名失败", e);
        }
    }

    /**
     * 校验签名与时间戳窗口。
     *
     * @param secret    签名密钥
     * @param content   业务指纹（参与签名的内容）
     * @param timestamp 毫秒时间戳字符串
     * @param signature 调用方传入的签名
     * @return true 校验通过
     */
    public static boolean verify(String secret, String content, String timestamp, String signature)
    {
        if (StringUtils.isEmpty(secret) || StringUtils.isEmpty(timestamp) || StringUtils.isEmpty(signature))
        {
            return false;
        }
        try
        {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > WINDOW_MILLIS)
            {
                return false;
            }
            String expected = sign(secret, content, timestamp);
            return constantTimeEquals(expected, signature);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 常量时间比较，防止计时攻击。
     */
    private static boolean constantTimeEquals(String a, String b)
    {
        if (a == null || b == null || a.length() != b.length())
        {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++)
        {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
