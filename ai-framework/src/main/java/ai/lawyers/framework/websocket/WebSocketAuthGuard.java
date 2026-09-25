package ai.lawyers.framework.websocket;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.framework.web.service.TokenService;

/**
 * N5：WebSocket 握手期 JWT 鉴权守卫。
 *
 * <p>{@code /ws/**} 在 Spring Security 中保持 {@code permitAll}（JSR-356 握手无法携带
 * 自定义 HTTP 头，JwtAuthenticationTokenFilter 不覆盖该通道），改由各 {@code @ServerEndpoint}
 * 在 {@code @OnOpen} 中调用本守卫完成鉴权：</p>
 * <ul>
 *   <li>令牌经握手 URL query 参数传递：{@code ?token=xxx}（兼容 {@code access_token}），
 *       前端登录后从本地令牌读取，浏览器原生 WebSocket 不支持 Authorization 头；</li>
 *   <li>{@code /ws/call/{userId}} 额外校验令牌用户与路径 userId 归属一致，防越权订阅他人话务事件；</li>
 *   <li>{@code /ws/chat/{sessionId}} 仅要求登录态有效，会话级归属由业务层保证。</li>
 * </ul>
 *
 * <p>应急回退：{@code websocket.auth.enabled=false}（环境变量 {@code WEBSOCKET_AUTH_ENABLED}）
 * 可临时关闭鉴权，仅用于联调/紧急排障，生产必须保持默认 true。</p>
 *
 * @author ai-lawyers
 */
@Component
public class WebSocketAuthGuard
{
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthGuard.class);

    /** 异常文本/URL 中误带的 query token 掩码（防 Tomcat WS 异常消息泄露 JWT） */
    private static final Pattern TOKEN_IN_TEXT =
            Pattern.compile("([?&](?:token|access_token)=)[^&\\s\"<>]*");

    @Value("${websocket.auth.enabled:true}")
    private boolean authEnabled;

    @Autowired
    private TokenService tokenService;

    @PostConstruct
    public void init()
    {
        if (authEnabled)
        {
            log.info("WebSocket 握手鉴权已启用（/ws/** 需携带有效 JWT）");
        }
        else
        {
            log.warn("========== 高风险配置告警 ========== WebSocket 握手鉴权已关闭"
                    + "（websocket.auth.enabled=false），仅限联调应急，生产严禁关闭");
        }
    }

    /** 鉴权是否启用（供端点判断是否需要解析 token） */
    public boolean isAuthEnabled()
    {
        return authEnabled;
    }

    /**
     * 校验呼叫事件通道：令牌有效且 userId 与登录用户一致。
     *
     * @param userId 路径参数中的用户 ID
     * @param queryString 会话 query 串
     * @return true 放行；false 拒绝（端点应以 1008 策略违例关闭连接）
     */
    public boolean authorizeCall(Long userId, String queryString)
    {
        if (!authEnabled)
        {
            return true;
        }
        if (userId == null)
        {
            return false;
        }
        LoginUser loginUser = resolveLoginUser(queryString);
        return loginUser != null && userId.equals(loginUser.getUserId());
    }

    /**
     * 校验图文会话通道：登录态有效即可。
     *
     * @param queryString 会话 query 串
     * @return true 放行；false 拒绝
     */
    public boolean authorizeChat(String queryString)
    {
        if (!authEnabled)
        {
            return true;
        }
        return resolveLoginUser(queryString) != null;
    }

    /**
     * 校验流式语音通道（P3-A1 {@code /ws/voice/{sessionId}/{role}}）：登录态有效即可。
     *
     * <p>语音流为会话内媒体/字幕通道，角色合法性（agent/caller）由端点校验；
     * 会话级业务归属由话单/会话层保证，与图文通道口径一致。</p>
     *
     * @param queryString 会话 query 串
     * @return true 放行；false 拒绝（端点以 1008 策略违例关闭）
     */
    public boolean authorizeVoice(String queryString)
    {
        if (!authEnabled)
        {
            return true;
        }
        return resolveLoginUser(queryString) != null;
    }

    /**
     * 解析握手 query 中的令牌并换取登录态；任何异常均视为未认证（不抛异常影响握手流程）。
     */
    private LoginUser resolveLoginUser(String queryString)
    {
        String token = extractToken(queryString);
        if (StringUtils.isEmpty(token))
        {
            return null;
        }
        try
        {
            return tokenService.getLoginUserByToken(token);
        }
        catch (Exception e)
        {
            log.warn("WebSocket 握手令牌解析异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将任意文本中 query 形式的令牌替换为 ***（供 WS onError 等日志出口兜底，
     * 防止容器异常消息携带完整握手 URI 而泄露 JWT）。
     */
    public static String maskQueryToken(String text)
    {
        if (text == null)
        {
            return null;
        }
        return TOKEN_IN_TEXT.matcher(text).replaceAll("$1***");
    }

    /**
     * 从 query 串解析 token 参数（兼容 access_token），URLDecoder 解码；非法编码返回 null。
     */
    static String extractToken(String queryString)
    {
        if (StringUtils.isEmpty(queryString))
        {
            return null;
        }
        for (String pair : queryString.split("&"))
        {
            int idx = pair.indexOf('=');
            if (idx <= 0)
            {
                continue;
            }
            String name = pair.substring(0, idx);
            if ("token".equals(name) || "access_token".equals(name))
            {
                String raw = pair.substring(idx + 1);
                try
                {
                    return URLDecoder.decode(raw, StandardCharsets.UTF_8.name());
                }
                catch (UnsupportedEncodingException | IllegalArgumentException e)
                {
                    return null;
                }
            }
        }
        return null;
    }
}
