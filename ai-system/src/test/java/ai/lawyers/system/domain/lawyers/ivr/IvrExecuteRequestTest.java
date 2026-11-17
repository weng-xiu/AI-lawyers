package ai.lawyers.system.domain.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * IVR流程执行请求测试：属性读写、集合默认值与空值兜底。
 *
 * @author ai-lawyers
 */
class IvrExecuteRequestTest
{
    @Test
    void 属性读写()
    {
        IvrExecuteRequest request = new IvrExecuteRequest();
        request.setFlowId(1L);
        request.setRecordId(2L);
        request.setSessionId("sess-001");
        request.setCallerNumber("13200000000");
        request.setCalleeNumber("12348");
        request.setInputs(Arrays.asList("1", "2"));
        Map<String, Object> variables = new HashMap<>();
        variables.put("channel", "phone");
        request.setVariables(variables);

        assertEquals(1L, request.getFlowId());
        assertEquals(2L, request.getRecordId());
        assertEquals("sess-001", request.getSessionId());
        assertEquals("13200000000", request.getCallerNumber());
        assertEquals("12348", request.getCalleeNumber());
        assertEquals(Arrays.asList("1", "2"), request.getInputs());
        assertEquals("phone", request.getVariables().get("channel"));
    }

    @Test
    void 集合默认非空()
    {
        IvrExecuteRequest request = new IvrExecuteRequest();

        assertNotNull(request.getInputs());
        assertTrue(request.getInputs().isEmpty());
        assertNotNull(request.getVariables());
        assertTrue(request.getVariables().isEmpty());
    }

    @Test
    void 集合置空时兜底为空集合()
    {
        IvrExecuteRequest request = new IvrExecuteRequest();
        request.setInputs(null);
        request.setVariables(null);

        assertNotNull(request.getInputs());
        assertTrue(request.getInputs().isEmpty());
        assertNotNull(request.getVariables());
        assertTrue(request.getVariables().isEmpty());
    }

    @Test
    void 默认对象字段为空()
    {
        IvrExecuteRequest request = new IvrExecuteRequest();

        assertNull(request.getFlowId());
        assertNull(request.getRecordId());
        assertNull(request.getSessionId());
        assertNull(request.getCallerNumber());
        assertNull(request.getCalleeNumber());
    }
}
