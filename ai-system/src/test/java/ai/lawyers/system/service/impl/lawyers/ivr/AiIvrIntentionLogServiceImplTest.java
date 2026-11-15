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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrIntentionLog;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrIntentionLogMapper;

/**
 * IVR 意图日志服务单测。
 *
 * <p>覆盖按日志 ID、记录 ID 查询、列表查询与新增。</p>
 *
 * @author ai-lawyers
 */
class AiIvrIntentionLogServiceImplTest
{
    private AiIvrIntentionLogServiceImpl service;

    @Mock private AiIvrIntentionLogMapper aiIvrIntentionLogMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiIvrIntentionLogServiceImpl();
        ReflectionTestUtils.setField(service, "aiIvrIntentionLogMapper", aiIvrIntentionLogMapper);
    }

    @Test
    void selectAiIvrIntentionLogByLogId_delegates()
    {
        AiIvrIntentionLog expected = new AiIvrIntentionLog();
        when(aiIvrIntentionLogMapper.selectAiIvrIntentionLogByLogId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiIvrIntentionLogByLogId(1L));
    }

    @Test
    void selectAiIvrIntentionLogList_delegates()
    {
        AiIvrIntentionLog query = new AiIvrIntentionLog();
        List<AiIvrIntentionLog> list = Collections.singletonList(new AiIvrIntentionLog());
        when(aiIvrIntentionLogMapper.selectAiIvrIntentionLogList(query)).thenReturn(list);

        assertSame(list, service.selectAiIvrIntentionLogList(query));
    }

    @Test
    void selectAiIvrIntentionLogByRecordId_delegates()
    {
        List<AiIvrIntentionLog> list = Collections.singletonList(new AiIvrIntentionLog());
        when(aiIvrIntentionLogMapper.selectAiIvrIntentionLogByRecordId(9L)).thenReturn(list);

        assertSame(list, service.selectAiIvrIntentionLogByRecordId(9L));
    }

    @Test
    void insertAiIvrIntentionLog_delegates()
    {
        AiIvrIntentionLog log = new AiIvrIntentionLog();
        when(aiIvrIntentionLogMapper.insertAiIvrIntentionLog(log)).thenReturn(1);

        assertEquals(1, service.insertAiIvrIntentionLog(log));
    }
}
