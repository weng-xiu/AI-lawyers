package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * IVR流程执行日志实体测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class AiIvrExecutionLogTest
{
    @Test
    void 属性读写()
    {
        Date startTime = new Date();
        Date endTime = new Date();
        AiIvrExecutionLog log = new AiIvrExecutionLog();
        log.setExecId(1L);
        log.setRecordId(2L);
        log.setSessionId("sess-001");
        log.setFlowId(3L);
        log.setFlowName("法律咨询主流程");
        log.setCurrentNodeId(4L);
        log.setCurrentNodeType("MENU");
        log.setCurrentNodeName("按键导航");
        log.setExecuteResult("SUCCESS");
        log.setVariables("{\"channel\":\"phone\"}");
        log.setStatus("1");
        log.setErrorMsg("无错误");
        log.setStartTime(startTime);
        log.setEndTime(endTime);

        assertEquals(1L, log.getExecId());
        assertEquals(2L, log.getRecordId());
        assertEquals("sess-001", log.getSessionId());
        assertEquals(3L, log.getFlowId());
        assertEquals("法律咨询主流程", log.getFlowName());
        assertEquals(4L, log.getCurrentNodeId());
        assertEquals("MENU", log.getCurrentNodeType());
        assertEquals("按键导航", log.getCurrentNodeName());
        assertEquals("SUCCESS", log.getExecuteResult());
        assertEquals("{\"channel\":\"phone\"}", log.getVariables());
        assertEquals("1", log.getStatus());
        assertEquals("无错误", log.getErrorMsg());
        assertEquals(startTime, log.getStartTime());
        assertEquals(endTime, log.getEndTime());
    }

    @Test
    void 默认值为空()
    {
        AiIvrExecutionLog log = new AiIvrExecutionLog();

        assertNull(log.getExecId());
        assertNull(log.getRecordId());
        assertNull(log.getSessionId());
        assertNull(log.getExecuteResult());
        assertNull(log.getStatus());
        assertNull(log.getStartTime());
    }
}
