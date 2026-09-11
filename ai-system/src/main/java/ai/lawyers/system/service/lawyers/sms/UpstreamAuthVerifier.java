package ai.lawyers.system.service.lawyers.sms;

import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.CallbackSignUtils;

/**
 * 短信上行回调鉴权决策（W4）：签名与 token 双通道组合语义。
 *
 * <p>规则（与《上线部署操作手册》§3.3 一致，避免"双配时 token 失效"的配置坑）：</p>
 * <ul>
 *   <li>仅配签名（CALLBACK_SIGN_ENABLED=true + CALLBACK_SIGN_SECRET）：仅校验 HMAC 签名，失败即拒绝</li>
 *   <li>仅配 token（SMS_UPSTREAM_TOKEN）：仅校验 token（header/query/body 三通道）</li>
 *   <li>两者同配：签名优先，签名缺失/失败时降级校验 token，任一通过即放行</li>
 *   <li>均未配置：返回 true（开发联调放行，由调用方告警）</li>
 * </ul>
 *
 * @author ai-lawyers
 */
public final class UpstreamAuthVerifier
{
    private UpstreamAuthVerifier()
    {
    }

    /**
     * @param signEnabled        签名开关（{@code call.callback.sign-enabled}）
     * @param signSecret         签名密钥（{@code call.callback.sign-secret}）
     * @param upstreamToken      上行 token（{@code call.sms.upstream.token}）
     * @param headerToken        请求头 {@code X-Upstream-Token}
     * @param queryToken         查询参数 {@code token}
     * @param bodyToken          请求体字段 {@code token}
     * @param headerTimestamp    请求头 {@code X-Callback-Timestamp}（毫秒）
     * @param headerSignature    请求头 {@code X-Callback-Sign}
     * @param phone              上行手机号（签名指纹组成部分）
     * @param content            上行内容（签名指纹组成部分）
     * @return true 鉴权通过
     */
    public static boolean verify(boolean signEnabled, String signSecret, String upstreamToken,
            String headerToken, String queryToken, Object bodyToken,
            String headerTimestamp, String headerSignature,
            String phone, String content)
    {
        boolean signConfigured = signEnabled && StringUtils.isNotEmpty(signSecret);
        boolean tokenConfigured = StringUtils.isNotEmpty(upstreamToken);
        // 1) 签名优先
        if (signConfigured
                && CallbackSignUtils.verify(signSecret, phone + "|" + content, headerTimestamp, headerSignature))
        {
            return true;
        }
        // 2) 仅配签名：签名未通过即拒绝（无 token 兜底）
        if (signConfigured && !tokenConfigured)
        {
            return false;
        }
        // 3) token 兜底（仅配 token 或双配时签名失败降级）
        if (tokenConfigured)
        {
            String token = headerToken;
            if (StringUtils.isEmpty(token))
            {
                token = queryToken;
            }
            if (StringUtils.isEmpty(token))
            {
                token = bodyToken == null ? null : String.valueOf(bodyToken);
            }
            return upstreamToken.equals(token);
        }
        // 4) 均未配置：联调放行
        return true;
    }
}
