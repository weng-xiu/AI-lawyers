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
import ai.lawyers.system.domain.lawyers.AiCallback;
import ai.lawyers.system.mapper.lawyers.AiCallbackMapper;

/**
 * 客户回访服务单测。
 *
 * <p>覆盖回访的查询、新增、修改、删除、统计，以及满意度趋势天数默认值处理。</p>
 *
 * @author ai-lawyers
 */
class AiCallbackServiceImplTest
{
    private AiCallbackServiceImpl service;

    @Mock private AiCallbackMapper aiCallbackMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallbackServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallbackMapper", aiCallbackMapper);
    }

    @Test
    void selectAiCallbackByCallbackId_delegates()
    {
        AiCallback expected = new AiCallback();
        when(aiCallbackMapper.selectAiCallbackByCallbackId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiCallbackByCallbackId(1L));
    }

    @Test
    void selectAiCallbackList_delegates()
    {
        AiCallback query = new AiCallback();
        List<AiCallback> list = Collections.singletonList(new AiCallback());
        when(aiCallbackMapper.selectAiCallbackList(query)).thenReturn(list);

        assertSame(list, service.selectAiCallbackList(query));
    }

    @Test
    void insertAiCallback_delegates()
    {
        AiCallback callback = new AiCallback();
        when(aiCallbackMapper.insertAiCallback(callback)).thenReturn(1);

        assertEquals(1, service.insertAiCallback(callback));
    }

    @Test
    void updateAiCallback_delegates()
    {
        AiCallback callback = new AiCallback();
        when(aiCallbackMapper.updateAiCallback(callback)).thenReturn(1);

        assertEquals(1, service.updateAiCallback(callback));
    }

    @Test
    void deleteAiCallbackByCallbackId_delegates()
    {
        when(aiCallbackMapper.deleteAiCallbackByCallbackId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiCallbackByCallbackId(1L));
    }

    @Test
    void deleteAiCallbackByCallbackIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiCallbackMapper.deleteAiCallbackByCallbackIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiCallbackByCallbackIds(ids));
    }

    @Test
    void selectCallbackStats_delegates()
    {
        Map<String, Object> stats = new HashMap<>();
        when(aiCallbackMapper.selectCallbackStats()).thenReturn(stats);

        assertSame(stats, service.selectCallbackStats());
    }

    @Test
    void selectSatisfactionTrend_nullDays_usesSeven()
    {
        List<Map<String, Object>> trend = Collections.emptyList();
        when(aiCallbackMapper.selectSatisfactionTrend(7)).thenReturn(trend);

        assertSame(trend, service.selectSatisfactionTrend(null));
    }

    @Test
    void selectSatisfactionTrend_nonPositiveDays_usesSeven()
    {
        List<Map<String, Object>> trend = Collections.emptyList();
        when(aiCallbackMapper.selectSatisfactionTrend(7)).thenReturn(trend);

        assertSame(trend, service.selectSatisfactionTrend(0));
    }

    @Test
    void selectSatisfactionTrend_positiveDays_passedThrough()
    {
        List<Map<String, Object>> trend = Collections.emptyList();
        when(aiCallbackMapper.selectSatisfactionTrend(30)).thenReturn(trend);

        assertSame(trend, service.selectSatisfactionTrend(30));
    }
}
