package ai.lawyers.common.config.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.lawyers.common.annotation.Sensitive;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.enums.DesensitizedType;

/**
 * 数据脱敏序列化测试：未登录/普通用户脱敏、管理员不脱敏、无注解字段不受影响。
 *
 * @author ai-lawyers
 */
class SensitiveJsonSerializerTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void 清理登录上下文()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 未登录时按注解类型脱敏() throws Exception
    {
        String json = MAPPER.writeValueAsString(new UserView("13800138000", "张三"));

        assertTrue(json.contains("\"phone\":\"138****8000\""), json);
        assertTrue(json.contains("\"name\":\"张三\""), json);
    }

    @Test
    void 普通用户仍脱敏() throws Exception
    {
        login(2L, "agent");

        String json = MAPPER.writeValueAsString(new UserView("13800138000", "李四"));

        assertTrue(json.contains("\"phone\":\"138****8000\""), json);
    }

    @Test
    void 管理员不脱敏() throws Exception
    {
        login(1L, "admin");

        String json = MAPPER.writeValueAsString(new UserView("13800138000", "王五"));

        assertEquals("{\"phone\":\"13800138000\",\"name\":\"王五\"}", json);
    }

    /** 绑定登录用户到安全上下文 */
    private void login(Long userId, String userName)
    {
        SysUser user = new SysUser(userId);
        user.setUserName(userName);
        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    /** 脱敏视图对象 */
    public static class UserView
    {
        @Sensitive(desensitizedType = DesensitizedType.PHONE)
        public String phone;

        public String name;

        public UserView(String phone, String name)
        {
            this.phone = phone;
            this.name = name;
        }
    }
}
