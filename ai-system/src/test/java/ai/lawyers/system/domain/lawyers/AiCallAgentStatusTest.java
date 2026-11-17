package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 坐席状态实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiCallAgentStatusTest
{
    @Test
    void 属性读写()
    {
        Date loginTime = new Date();
        Date logoutTime = new Date();
        Date callStartTime = new Date();
        AiCallAgentStatus status = new AiCallAgentStatus();
        status.setAgentId(1L);
        status.setUserId(2L);
        status.setAgentName("张三");
        status.setStatus("1");
        status.setLoginTime(loginTime);
        status.setLogoutTime(logoutTime);
        status.setLastLoginIp("127.0.0.1");
        status.setCallMode("0");
        status.setCurrentCallId(3L);
        status.setCurrentCallPhone("13800000000");
        status.setCallStartTime(callStartTime);
        status.setCallStatus("1");
        status.setSipExtension("8001");
        status.setUserName("zhangsan");
        status.setNickName("张三");
        status.setPhonenumber("13800000000");
        status.setDeptName("客服部");

        assertEquals(1L, status.getAgentId());
        assertEquals(2L, status.getUserId());
        assertEquals("张三", status.getAgentName());
        assertEquals("1", status.getStatus());
        assertEquals(loginTime, status.getLoginTime());
        assertEquals(logoutTime, status.getLogoutTime());
        assertEquals("127.0.0.1", status.getLastLoginIp());
        assertEquals("0", status.getCallMode());
        assertEquals(3L, status.getCurrentCallId());
        assertEquals("13800000000", status.getCurrentCallPhone());
        assertEquals(callStartTime, status.getCallStartTime());
        assertEquals("1", status.getCallStatus());
        assertEquals("8001", status.getSipExtension());
        assertEquals("zhangsan", status.getUserName());
        assertEquals("张三", status.getNickName());
        assertEquals("13800000000", status.getPhonenumber());
        assertEquals("客服部", status.getDeptName());
    }

    @Test
    void 默认值为空()
    {
        AiCallAgentStatus status = new AiCallAgentStatus();

        assertNull(status.getAgentId());
        assertNull(status.getAgentName());
        assertNull(status.getStatus());
        assertNull(status.getCurrentCallId());
        assertNull(status.getCallStatus());
        assertNull(status.getSipExtension());
    }

    @Test
    void 字符串输出包含关键字段()
    {
        AiCallAgentStatus status = new AiCallAgentStatus();
        status.setAgentId(1L);
        status.setAgentName("张三");
        status.setStatus("1");
        status.setSipExtension("8001");

        String text = status.toString();

        assertTrue(text.contains("张三"));
        assertTrue(text.contains("8001"));
        assertTrue(text.contains("agentId"));
    }
}
