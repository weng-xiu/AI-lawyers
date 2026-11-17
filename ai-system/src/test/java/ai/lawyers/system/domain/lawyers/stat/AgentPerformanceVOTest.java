package ai.lawyers.system.domain.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 坐席效能报表行测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AgentPerformanceVOTest
{
    @Test
    void 属性读写()
    {
        Date loginTime = new Date();
        Date logoutTime = new Date();
        AgentPerformanceVO vo = new AgentPerformanceVO();
        vo.setAgentId(1L);
        vo.setAgentName("张三");
        vo.setStatus("1");
        vo.setCallStatus("0");
        vo.setLoginTime(loginTime);
        vo.setLogoutTime(logoutTime);
        vo.setSignedInMinutes(480L);
        vo.setInboundCalls(30L);
        vo.setOutboundCalls(10L);
        vo.setTransferredOut(3L);
        vo.setTalkDuration(7200L);
        vo.setAvgTalkDuration(180L);
        vo.setAiAssistCount(5L);
        vo.setAvgSatisfaction(4.5D);

        assertEquals(1L, vo.getAgentId());
        assertEquals("张三", vo.getAgentName());
        assertEquals("1", vo.getStatus());
        assertEquals("0", vo.getCallStatus());
        assertEquals(loginTime, vo.getLoginTime());
        assertEquals(logoutTime, vo.getLogoutTime());
        assertEquals(480L, vo.getSignedInMinutes());
        assertEquals(30L, vo.getInboundCalls());
        assertEquals(10L, vo.getOutboundCalls());
        assertEquals(3L, vo.getTransferredOut());
        assertEquals(7200L, vo.getTalkDuration());
        assertEquals(180L, vo.getAvgTalkDuration());
        assertEquals(5L, vo.getAiAssistCount());
        assertEquals(4.5D, vo.getAvgSatisfaction().doubleValue());
    }

    @Test
    void 默认值为空()
    {
        AgentPerformanceVO vo = new AgentPerformanceVO();

        assertNull(vo.getAgentId());
        assertNull(vo.getAgentName());
        assertNull(vo.getStatus());
        assertNull(vo.getInboundCalls());
        assertNull(vo.getAvgSatisfaction());
    }
}
