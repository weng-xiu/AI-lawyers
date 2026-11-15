package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiMissedCall;
import ai.lawyers.system.mapper.lawyers.AiMissedCallMapper;

/**
 * 未接来电服务单测。
 *
 * <p>覆盖未接来电的查询、新增、修改、删除与统计委托。</p>
 *
 * @author ai-lawyers
 */
class AiMissedCallServiceImplTest
{
    private AiMissedCallServiceImpl service;

    @Mock private AiMissedCallMapper aiMissedCallMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiMissedCallServiceImpl();
        ReflectionTestUtils.setField(service, "aiMissedCallMapper", aiMissedCallMapper);
    }

    @Test
    void selectAiMissedCallByMissedCallId_delegates()
    {
        AiMissedCall expected = new AiMissedCall();
        when(aiMissedCallMapper.selectAiMissedCallByMissedCallId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiMissedCallByMissedCallId(1L));
    }

    @Test
    void selectAiMissedCallList_delegates()
    {
        AiMissedCall query = new AiMissedCall();
        List<AiMissedCall> list = Collections.singletonList(new AiMissedCall());
        when(aiMissedCallMapper.selectAiMissedCallList(query)).thenReturn(list);

        assertSame(list, service.selectAiMissedCallList(query));
    }

    @Test
    void insertAiMissedCall_delegates()
    {
        AiMissedCall missedCall = new AiMissedCall();
        when(aiMissedCallMapper.insertAiMissedCall(missedCall)).thenReturn(1);

        assertEquals(1, service.insertAiMissedCall(missedCall));
    }

    @Test
    void updateAiMissedCall_delegates()
    {
        AiMissedCall missedCall = new AiMissedCall();
        when(aiMissedCallMapper.updateAiMissedCall(missedCall)).thenReturn(1);

        assertEquals(1, service.updateAiMissedCall(missedCall));
    }

    @Test
    void deleteAiMissedCallByMissedCallId_delegates()
    {
        when(aiMissedCallMapper.deleteAiMissedCallByMissedCallId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiMissedCallByMissedCallId(1L));
    }

    @Test
    void deleteAiMissedCallByMissedCallIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiMissedCallMapper.deleteAiMissedCallByMissedCallIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiMissedCallByMissedCallIds(ids));
    }

    @Test
    void selectMissedCallStats_delegates()
    {
        Map<String, Object> stats = new HashMap<>();
        when(aiMissedCallMapper.selectMissedCallStats()).thenReturn(stats);

        assertSame(stats, service.selectMissedCallStats());
    }
}
