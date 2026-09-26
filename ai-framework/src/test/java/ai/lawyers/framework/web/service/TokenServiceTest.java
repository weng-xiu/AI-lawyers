package ai.lawyers.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * TokenService 密钥轮换兼容性测试。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>主密钥签发 + 解析往返；</li>
 *   <li>previousSecret 签发的旧 token 能被降级验签解析；</li>
 *   <li>未知密钥签发的 token 解析失败（抛 JwtException）；</li>
 *   <li>过期 token 抛 ExpiredJwtException（不因降级而掩盖）。</li>
 * </ol>
 */
class TokenServiceTest
{
    // 注意：密钥须含非 base64 字符（'-'），强制 resolveKeyBytes 走 UTF-8 字节路径，
    // 且长度 >=64 字节以满足 HS512 要求的 512 位密钥强度
    private static final String SECRET = "primary-secret-0123456789-abcdefghij-0123456789-abcdefghij-0123456789";
    private static final String PREV_SECRET = "previous-secret-0123456789-abcdefghij-0123456789-abcdefghij-0123456789";

    private TokenService tokenService;

    @BeforeEach
    void setUp() throws Exception
    {
        tokenService = new TokenService();
        setField("secret", SECRET);
        setField("previousSecret", PREV_SECRET);
    }

    private void setField(String name, String value) throws Exception
    {
        Field f = TokenService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(tokenService, value);
    }

    private String invokeCreateToken(Map<String, Object> claims) throws Exception
    {
        Method m = TokenService.class.getDeclaredMethod("createToken", Map.class);
        m.setAccessible(true);
        return (String) m.invoke(tokenService, claims);
    }

    private Claims invokeParseToken(String token) throws Exception
    {
        Method m = TokenService.class.getDeclaredMethod("parseToken", String.class);
        m.setAccessible(true);
        try
        {
            return (Claims) m.invoke(tokenService, token);
        }
        catch (java.lang.reflect.InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof ExpiredJwtException)
            {
                throw (ExpiredJwtException) cause;
            }
            if (cause instanceof JwtException)
            {
                throw (JwtException) cause;
            }
            throw e;
        }
    }

    private String signWithKey(String key, Map<String, Object> claims, Date expiration)
    {
        SecretKey sk = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder().claims(claims).signWith(sk, Jwts.SIG.HS512);
        if (expiration != null)
        {
            builder.expiration(expiration);
        }
        return builder.compact();
    }

    @Test
    void testCreateAndParseWithPrimarySecret() throws Exception
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user123");
        claims.put("role", "admin");

        String token = invokeCreateToken(claims);
        Claims parsed = invokeParseToken(token);

        assertEquals("user123", parsed.getSubject());
        assertEquals("admin", parsed.get("role"));
    }

    @Test
    void testParseTokenSignedByPreviousSecret() throws Exception
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "legacy-user");

        String token = signWithKey(PREV_SECRET, claims, null);
        Claims parsed = invokeParseToken(token);

        assertEquals("legacy-user", parsed.getSubject());
    }

    @Test
    void testParseTokenSignedByUnknownSecretFails()
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "attacker");

        String token = signWithKey("unknownKey-unknownKey-unknownKey-unknownKey-unknownKey-unknownKey-", claims, null);

        assertThrows(JwtException.class, () -> invokeParseToken(token));
    }

    @Test
    void testExpiredTokenThrowsExpiredJwtException()
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "expired-user");

        String token = signWithKey(SECRET, claims, new Date(System.currentTimeMillis() - 60_000));

        assertThrows(ExpiredJwtException.class, () -> invokeParseToken(token));
    }
}
