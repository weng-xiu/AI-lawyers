package ai.lawyers.system.service.lawyers.sms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ai.lawyers.common.utils.sign.CallbackSignUtils;

/**
 * {@link UpstreamAuthVerifier} 鉴权组合矩阵测试（W4 上行回调安全收口）。
 *
 * <p>覆盖：未配置放行 / 仅签名 / 仅 token（header·query·body 三通道）/ 双配（签名优先 + token 兜底）。</p>
 *
 * @author ai-lawyers
 */
class UpstreamAuthVerifierTest
{
    private static final String PHONE = "13800138000";
    private static final String CONTENT = "T";
    private static final String SECRET = "test-sign-secret-2026";
    private static final String TOKEN = "test-upstream-token-2026";

    private String validSign()
    {
        return CallbackSignUtils.sign(SECRET, PHONE + "|" + CONTENT, String.valueOf(System.currentTimeMillis()));
    }

    // ---------- 均未配置：联调放行 ----------

    @Test
    void verify_noAuthConfigured_passesThrough()
    {
        assertThat(UpstreamAuthVerifier.verify(false, null, null, null, null, null, null, null, PHONE, CONTENT))
                .isTrue();
    }

    // ---------- 仅配签名 ----------

    @Test
    void verify_signOnly_validSign_passes()
    {
        String ts = String.valueOf(System.currentTimeMillis());
        String sign = CallbackSignUtils.sign(SECRET, PHONE + "|" + CONTENT, ts);
        assertThat(UpstreamAuthVerifier.verify(true, SECRET, null, null, null, null, ts, sign, PHONE, CONTENT))
                .isTrue();
    }

    @Test
    void verify_signOnly_invalidSign_rejected()
    {
        String ts = String.valueOf(System.currentTimeMillis());
        assertThat(UpstreamAuthVerifier.verify(true, SECRET, null, null, null, null, ts, "bad-sign", PHONE, CONTENT))
                .isFalse();
    }

    @Test
    void verify_signOnly_missingSign_rejected()
    {
        assertThat(UpstreamAuthVerifier.verify(true, SECRET, null, null, null, null, null, null, PHONE, CONTENT))
                .isFalse();
    }

    // ---------- 仅配 token：header/query/body 三通道 ----------

    @Test
    void verify_tokenOnly_validHeaderToken_passes()
    {
        assertThat(UpstreamAuthVerifier.verify(false, null, TOKEN, TOKEN, null, null, null, null, PHONE, CONTENT))
                .isTrue();
    }

    @Test
    void verify_tokenOnly_validQueryToken_passes()
    {
        assertThat(UpstreamAuthVerifier.verify(false, null, TOKEN, null, TOKEN, null, null, null, PHONE, CONTENT))
                .isTrue();
    }

    @Test
    void verify_tokenOnly_validBodyToken_passes()
    {
        assertThat(UpstreamAuthVerifier.verify(false, null, TOKEN, null, null, TOKEN, null, null, PHONE, CONTENT))
                .isTrue();
    }

    @Test
    void verify_tokenOnly_wrongToken_rejected()
    {
        assertThat(UpstreamAuthVerifier.verify(false, null, TOKEN, "wrong", null, null, null, null, PHONE, CONTENT))
                .isFalse();
        assertThat(UpstreamAuthVerifier.verify(false, null, TOKEN, null, null, null, null, null, PHONE, CONTENT))
                .isFalse();
    }

    // ---------- 双配：签名优先，token 兜底（关键回归，防"双配时 token 失效"坑） ----------

    @Test
    void verify_bothConfigured_validSign_passesWithoutToken()
    {
        String ts = String.valueOf(System.currentTimeMillis());
        String sign = CallbackSignUtils.sign(SECRET, PHONE + "|" + CONTENT, ts);
        assertThat(UpstreamAuthVerifier.verify(true, SECRET, TOKEN, null, null, null, ts, sign, PHONE, CONTENT))
                .isTrue();
    }

    @Test
    void verify_bothConfigured_signInvalid_validToken_fallback_passes()
    {
        // 服务商仅支持自定义 header 携带 token：签名缺失/失败时降级 token 校验
        assertThat(UpstreamAuthVerifier.verify(true, SECRET, TOKEN, TOKEN, null, null, null, null, PHONE, CONTENT))
                .isTrue();
        assertThat(UpstreamAuthVerifier.verify(true, SECRET, TOKEN, TOKEN, null, null,
                String.valueOf(System.currentTimeMillis()), "bad-sign", PHONE, CONTENT))
                .isTrue();
    }

    @Test
    void verify_bothConfigured_neitherValid_rejected()
    {
        assertThat(UpstreamAuthVerifier.verify(true, SECRET, TOKEN, null, null, null, null, null, PHONE, CONTENT))
                .isFalse();
        assertThat(UpstreamAuthVerifier.verify(true, SECRET, TOKEN, "wrong", null, null,
                String.valueOf(System.currentTimeMillis()), "bad-sign", PHONE, CONTENT))
                .isFalse();
    }
}
