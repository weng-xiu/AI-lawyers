package ai.lawyers.framework.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.framework.web.service.TokenService;

/**
 * N5：{@link WebSocketAuthGuard} 握手鉴权矩阵测试。
 *
 * <p>覆盖：query token 解析（含 URL 编码/别名/脏值）、呼叫通道 userId 归属校验、
 * 图文通道登录态校验、以及应急开关 {@code websocket.auth.enabled=false} 全放行。</p>
 *
 * @author ai-lawyers
 */
class WebSocketAuthGuardTest
{
    private static final Long USER_ID = 100L;
    private static final String GOOD_TOKEN = "good-jwt";
    private static final String BAD_TOKEN = "bad-jwt";

    private TokenService tokenService;
    private WebSocketAuthGuard guard;
    private LoginUser loginUser;

    @BeforeEach
    void setUp()
    {
        tokenService = mock(TokenService.class);
        loginUser = new LoginUser();
        loginUser.setUserId(USER_ID);
        when(tokenService.getLoginUserByToken(GOOD_TOKEN)).thenReturn(loginUser);
        when(tokenService.getLoginUserByToken(BAD_TOKEN)).thenReturn(null);

        guard = new WebSocketAuthGuard();
        ReflectionTestUtils.setField(guard, "tokenService", tokenService);
        ReflectionTestUtils.setField(guard, "authEnabled", true);
    }

    // ---------- extractToken ----------

    @Test
    void extractToken_plainToken()
    {
        assertThat(WebSocketAuthGuard.extractToken("token=abc")).isEqualTo("abc");
    }

    @Test
    void extractToken_accessTokenAliasAndExtraParams()
    {
        assertThat(WebSocketAuthGuard.extractToken("foo=1&access_token=xyz&bar=2")).isEqualTo("xyz");
    }

    @Test
    void extractToken_urlEncoded()
    {
        // JWT 含 '.' 不受影响；含编码字符（空格/中文）需解码
        assertThat(WebSocketAuthGuard.extractToken("token=abc%20x")).isEqualTo("abc x");
    }

    @Test
    void extractToken_nullEmptyOrMissing_returnsNull()
    {
        assertThat(WebSocketAuthGuard.extractToken(null)).isNull();
        assertThat(WebSocketAuthGuard.extractToken("")).isNull();
        assertThat(WebSocketAuthGuard.extractToken("foo=1")).isNull();
        assertThat(WebSocketAuthGuard.extractToken("=notoken")).isNull();
    }

    // ---------- 呼叫通道：登录态 + userId 归属一致 ----------

    @Test
    void authorizeCall_goodTokenSameUser_passes()
    {
        assertThat(guard.authorizeCall(USER_ID, "token=" + GOOD_TOKEN)).isTrue();
    }

    @Test
    void authorizeCall_goodTokenOtherUser_rejected()
    {
        // 越权订阅他人话务事件：令牌属于 100，路径却写 200 → 拒绝
        assertThat(guard.authorizeCall(200L, "token=" + GOOD_TOKEN)).isFalse();
    }

    @Test
    void authorizeCall_missingOrBadToken_rejected()
    {
        assertThat(guard.authorizeCall(USER_ID, null)).isFalse();
        assertThat(guard.authorizeCall(USER_ID, "")).isFalse();
        assertThat(guard.authorizeCall(USER_ID, "token=" + BAD_TOKEN)).isFalse();
    }

    @Test
    void authorizeCall_nullUserId_rejected()
    {
        assertThat(guard.authorizeCall(null, "token=" + GOOD_TOKEN)).isFalse();
    }

    // ---------- 图文通道：仅验登录态 ----------

    @Test
    void authorizeChat_goodToken_passes()
    {
        assertThat(guard.authorizeChat("token=" + GOOD_TOKEN)).isTrue();
    }

    @Test
    void authorizeChat_badToken_rejected()
    {
        assertThat(guard.authorizeChat("token=" + BAD_TOKEN)).isFalse();
        assertThat(guard.authorizeChat(null)).isFalse();
    }

    // ---------- 流式语音通道（P3-A1）：仅验登录态，角色合法性由端点校验 ----------

    @Test
    void authorizeVoice_goodToken_passes()
    {
        assertThat(guard.authorizeVoice("token=" + GOOD_TOKEN)).isTrue();
    }

    @Test
    void authorizeVoice_badOrMissingToken_rejected()
    {
        assertThat(guard.authorizeVoice("token=" + BAD_TOKEN)).isFalse();
        assertThat(guard.authorizeVoice(null)).isFalse();
        assertThat(guard.authorizeVoice("role=agent")).isFalse();
    }

    // ---------- maskQueryToken：日志出口兜底掩码 ----------

    @Test
    void maskQueryToken_nullPassThrough()
    {
        assertThat(WebSocketAuthGuard.maskQueryToken(null)).isNull();
    }

    @Test
    void maskQueryToken_masksTokenAndKeepsRest()
    {
        assertThat(WebSocketAuthGuard.maskQueryToken(
                "ws upgrade error /ws/call/100?token=eyJhbGciOi.JzdWIiOiIx.MjY"))
                .isEqualTo("ws upgrade error /ws/call/100?token=***");
        // 别名 + 后续参数：只掩码令牌段
        assertThat(WebSocketAuthGuard.maskQueryToken(
                "/ws/chat/s1?foo=1&access_token=abc.def.ghi&bar=2"))
                .isEqualTo("/ws/chat/s1?foo=1&access_token=***&bar=2");
        // 无令牌原样返回
        assertThat(WebSocketAuthGuard.maskQueryToken("/ws/call/100")).isEqualTo("/ws/call/100");
    }

    // ---------- 应急回退开关 ----------

    @Test
    void authDisabled_everythingPassesThrough()
    {
        ReflectionTestUtils.setField(guard, "authEnabled", false);
        // 无 token、userId 不一致，开关关闭时一律放行（仅限联调应急）
        assertThat(guard.authorizeCall(999L, null)).isTrue();
        assertThat(guard.authorizeChat("foo=1")).isTrue();
        assertThat(guard.authorizeVoice("foo=1")).isTrue();
        assertThat(guard.isAuthEnabled()).isFalse();
    }
}
