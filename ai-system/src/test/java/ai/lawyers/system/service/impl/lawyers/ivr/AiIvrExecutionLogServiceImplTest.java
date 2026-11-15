package ai.lawyers.system.service.impl.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrExecutionLog;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrExecutionLogMapper;

/**
 * IVR 执行日志服务单测。
 *
 * <p>覆盖按执行 ID、会话 ID 查询、列表查询、新增与修改。</p>
 *
 * @author ai-lawyers
 */
class AiIvrExecutionLogServiceImplTest
{
    private AiIvrExecutionLogServiceImpl service;

    @Mock private AiIvrExecutionLogMapper aiIvrExecutionLogMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiIvrExecutionLogServiceImpl();
        ReflectionTestUtils.setField(service, "aiIvrExecutionLogMapper", aiIvrExecutionLogMapper);
    }

    @Test
    void selectAiIvrExecutionLogByExecId_delegates()
    {
        AiIvrExecutionLog expected = new AiIvrExecutionLog();
        when(aiIvrExecutionLogMapper.selectAiIvrExecutionLogByExecId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiIvrExecutionLogByExecId(1L));
    }

    @Test
    void selectAiIvrExecutionLogList_delegates()
    {
        AiIvrExecutionLog query = new AiIvrExecutionLog();
        List<AiIvrExecutionLog> list = Collections.singletonList(new AiIvrExecutionLog());
        when(aiIvrExecutionLogMapper.selectAiIvrExecutionLogList(query)).thenReturn(list);

        assertSame(list, service.selectAiIvrExecutionLogList(query));
    }

    @Test
    void selectAiIvrExecutionLogBySessionId_delegates()
    {
        AiIvrExecutionLog expected = new AiIvrExecutionLog();
        when(aiIvrExecutionLogMapper.selectAiIvrExecutionLogBySessionId("s-1")).thenReturn(expected);

        assertSame(expected, service.selectAiIvrExecutionLogBySessionId("s-1"));
    }

    @Test
    void insertAiIvrExecutionLog_delegates()
    {
        AiIvrExecutionLog log = new AiIvrExecutionLog();
        when(aiIvrExecutionLogMapper.insertAiIvrExecutionLog(log)).thenReturn(1);

        assertEquals(1, service.insertAiIvrExecutionLog(log));
    }

    @Test
    void updateAiIvrExecutionLog_delegates()
    {
        AiIvrExecutionLog log = new AiIvrExecutionLog();
        when(aiIvrExecutionLogMapper.updateAiIvrExecutionLog(log)).thenReturn(1);

        assertEquals(1, service.updateAiIvrExecutionLog(log));
    }
}
