package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * IVR流程执行结果测试：属性读写与集合默认值。
 *
 * @author ai-lawyers
 */
class IvrExecuteResultTest
{
    @Test
    void 属性读写()
    {
        IvrExecuteResult result = new IvrExecuteResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setMessage("执行成功");
        result.setExecId(1L);
        result.setSessionId("sess-001");
        result.setFlowId(2L);
        result.setFlowName("法律咨询主流程");
        result.setStatus("1");
        result.setRecordId(3L);
        result.setCurrentNodeId(4L);
        result.setCurrentNodeType("MENU");
        result.setCurrentNodeName("按键导航");
        result.setMatchedIntention("LABOR");
        result.setMatchedIntentionName("劳动争议");
        result.setMatchMethod("keyword");
        result.setCategoryId(5L);
        result.setCategoryName("劳动关系");
        result.setTransferTarget("agent-group-1");
        result.getVariables().put("channel", "phone");
        result.getSteps().add(new IvrNodeStep());

        assertTrue(result.isSuccess());
        assertEquals("200", result.getCode());
        assertEquals("执行成功", result.getMessage());
        assertEquals(1L, result.getExecId());
        assertEquals("sess-001", result.getSessionId());
        assertEquals(2L, result.getFlowId());
        assertEquals("法律咨询主流程", result.getFlowName());
        assertEquals("1", result.getStatus());
        assertEquals(3L, result.getRecordId());
        assertEquals(4L, result.getCurrentNodeId());
        assertEquals("MENU", result.getCurrentNodeType());
        assertEquals("按键导航", result.getCurrentNodeName());
        assertEquals("LABOR", result.getMatchedIntention());
        assertEquals("劳动争议", result.getMatchedIntentionName());
        assertEquals("keyword", result.getMatchMethod());
        assertEquals(5L, result.getCategoryId());
        assertEquals("劳动关系", result.getCategoryName());
        assertEquals("agent-group-1", result.getTransferTarget());
        assertEquals("phone", result.getVariables().get("channel"));
        assertEquals(1, result.getSteps().size());
    }

    @Test
    void 集合默认非空()
    {
        IvrExecuteResult result = new IvrExecuteResult();

        assertNotNull(result.getVariables());
        assertTrue(result.getVariables().isEmpty());
        assertNotNull(result.getSteps());
        assertTrue(result.getSteps().isEmpty());
    }

    @Test
    void 默认值为空()
    {
        IvrExecuteResult result = new IvrExecuteResult();

        assertFalse(result.isSuccess());
        assertNull(result.getCode());
        assertNull(result.getMessage());
        assertNull(result.getExecId());
        assertNull(result.getSessionId());
        assertNull(result.getMatchedIntention());
        assertNull(result.getTransferTarget());
    }

    @Test
    void 集合可替换()
    {
        IvrExecuteResult result = new IvrExecuteResult();
        result.setVariables(Collections.emptyMap());
        result.setSteps(Collections.emptyList());

        assertTrue(result.getVariables().isEmpty());
        assertTrue(result.getSteps().isEmpty());
    }
}
