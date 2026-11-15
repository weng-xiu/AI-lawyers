package ai.lawyers.system.service.impl.lawyers;

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
import ai.lawyers.system.domain.lawyers.AiVideoConsultLog;
import ai.lawyers.system.mapper.lawyers.AiVideoConsultLogMapper;

/**
 * 视频咨询日志服务单测。
 *
 * <p>覆盖按日志 ID 查询、列表查询与新增。</p>
 *
 * @author ai-lawyers
 */
class AiVideoConsultLogServiceImplTest
{
    private AiVideoConsultLogServiceImpl service;

    @Mock private AiVideoConsultLogMapper aiVideoConsultLogMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiVideoConsultLogServiceImpl();
        ReflectionTestUtils.setField(service, "aiVideoConsultLogMapper", aiVideoConsultLogMapper);
    }

    @Test
    void selectAiVideoConsultLogByLogId_delegates()
    {
        AiVideoConsultLog expected = new AiVideoConsultLog();
        when(aiVideoConsultLogMapper.selectAiVideoConsultLogByLogId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiVideoConsultLogByLogId(1L));
    }

    @Test
    void selectAiVideoConsultLogList_delegates()
    {
        AiVideoConsultLog query = new AiVideoConsultLog();
        List<AiVideoConsultLog> list = Collections.singletonList(new AiVideoConsultLog());
        when(aiVideoConsultLogMapper.selectAiVideoConsultLogList(query)).thenReturn(list);

        assertSame(list, service.selectAiVideoConsultLogList(query));
    }

    @Test
    void insertAiVideoConsultLog_delegates()
    {
        AiVideoConsultLog log = new AiVideoConsultLog();
        when(aiVideoConsultLogMapper.insertAiVideoConsultLog(log)).thenReturn(1);

        assertEquals(1, service.insertAiVideoConsultLog(log));
    }
}
