package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundResult;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundResultMapper;

/**
 * 外呼结果服务单测。
 *
 * <p>覆盖外呼结果查询、新增委托，以及按任务聚合接通/未接/失败与意向统计。</p>
 *
 * @author ai-lawyers
 */
class AiOutboundResultServiceImplTest
{
    private AiOutboundResultServiceImpl service;

    @Mock private AiOutboundResultMapper aiOutboundResultMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiOutboundResultServiceImpl();
        ReflectionTestUtils.setField(service, "aiOutboundResultMapper", aiOutboundResultMapper);
    }

    private static AiOutboundResult result(String callResult, String intentionCode)
    {
        AiOutboundResult result = new AiOutboundResult();
        result.setCallResult(callResult);
        result.setIntentionCode(intentionCode);
        return result;
    }

    @Test
    void selectAiOutboundResultByResultId_delegates()
    {
        AiOutboundResult expected = new AiOutboundResult();
        when(aiOutboundResultMapper.selectAiOutboundResultByResultId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiOutboundResultByResultId(1L));
    }

    @Test
    void selectAiOutboundResultList_delegates()
    {
        AiOutboundResult query = new AiOutboundResult();
        List<AiOutboundResult> list = Collections.singletonList(new AiOutboundResult());
        when(aiOutboundResultMapper.selectAiOutboundResultList(query)).thenReturn(list);

        assertSame(list, service.selectAiOutboundResultList(query));
    }

    @Test
    void selectAiOutboundResultByTaskId_delegates()
    {
        List<AiOutboundResult> list = Collections.singletonList(new AiOutboundResult());
        when(aiOutboundResultMapper.selectAiOutboundResultByTaskId(1L)).thenReturn(list);

        assertSame(list, service.selectAiOutboundResultByTaskId(1L));
    }

    @Test
    void selectAiOutboundResultByCalleeId_delegates()
    {
        AiOutboundResult expected = new AiOutboundResult();
        when(aiOutboundResultMapper.selectAiOutboundResultByCalleeId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiOutboundResultByCalleeId(1L));
    }

    @Test
    void insertAiOutboundResult_delegates()
    {
        AiOutboundResult result = new AiOutboundResult();
        when(aiOutboundResultMapper.insertAiOutboundResult(result)).thenReturn(1);

        assertEquals(1, service.insertAiOutboundResult(result));
    }

    @Test
    void getTaskResultStatistics_countsByCallResultAndIntention()
    {
        List<AiOutboundResult> results = Arrays.asList(
            result("1", "1"),
            result("1", "2"),
            result("2", null),
            result("3", "1"),
            result(null, "2"));
        when(aiOutboundResultMapper.selectAiOutboundResultByTaskId(1L)).thenReturn(results);

        Map<String, Object> stats = service.getTaskResultStatistics(1L);

        assertEquals(5, stats.get("total"));
        assertEquals(2, stats.get("answered"));
        assertEquals(1, stats.get("noAnswer"));
        assertEquals(1, stats.get("failed"));
        assertEquals(2, stats.get("interested"));
        assertEquals(2, stats.get("notInterested"));
    }

    @Test
    void getTaskResultStatistics_emptyResults_returnsZeros()
    {
        when(aiOutboundResultMapper.selectAiOutboundResultByTaskId(1L)).thenReturn(Collections.emptyList());

        Map<String, Object> stats = service.getTaskResultStatistics(1L);

        assertEquals(0, stats.get("total"));
        assertEquals(0, stats.get("answered"));
        assertEquals(0, stats.get("noAnswer"));
        assertEquals(0, stats.get("failed"));
        assertEquals(0, stats.get("interested"));
        assertEquals(0, stats.get("notInterested"));
    }
}
