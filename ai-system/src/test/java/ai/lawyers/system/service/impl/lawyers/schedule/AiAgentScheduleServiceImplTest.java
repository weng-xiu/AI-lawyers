package ai.lawyers.system.service.impl.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentSchedule;
import ai.lawyers.system.domain.lawyers.schedule.AiAgentScheduleVO;
import ai.lawyers.system.mapper.lawyers.schedule.AiAgentScheduleMapper;

/**
 * 坐席排班服务单测。
 *
 * <p>覆盖排班查询、增删改委托、新增/批量新增默认值回填、签到签到的入参校验与当日排班存在性校验。</p>
 *
 * @author ai-lawyers
 */
class AiAgentScheduleServiceImplTest
{
    private AiAgentScheduleServiceImpl service;

    @Mock private AiAgentScheduleMapper aiAgentScheduleMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiAgentScheduleServiceImpl();
        ReflectionTestUtils.setField(service, "aiAgentScheduleMapper", aiAgentScheduleMapper);
    }

    private static AiAgentSchedule schedule(Integer scheduleType, Integer status)
    {
        AiAgentSchedule schedule = new AiAgentSchedule();
        schedule.setScheduleType(scheduleType);
        schedule.setStatus(status);
        return schedule;
    }

    @Test
    void selectAiAgentScheduleByScheduleId_delegates()
    {
        AiAgentSchedule expected = new AiAgentSchedule();
        when(aiAgentScheduleMapper.selectAiAgentScheduleByScheduleId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiAgentScheduleByScheduleId(1L));
    }

    @Test
    void selectScheduleList_delegates()
    {
        AiAgentScheduleVO query = new AiAgentScheduleVO();
        List<AiAgentScheduleVO> list = Collections.singletonList(new AiAgentScheduleVO());
        when(aiAgentScheduleMapper.selectScheduleList(query)).thenReturn(list);

        assertSame(list, service.selectScheduleList(query));
    }

    @Test
    void selectByDateRange_delegates()
    {
        List<AiAgentScheduleVO> list = Collections.singletonList(new AiAgentScheduleVO());
        when(aiAgentScheduleMapper.selectByDateRange("2026-01-01", "2026-01-31")).thenReturn(list);

        assertSame(list, service.selectByDateRange("2026-01-01", "2026-01-31"));
    }

    @Test
    void getMyScheduleToday_nullAgent_returnsNull()
    {
        assertNull(service.getMyScheduleToday(null));
        verify(aiAgentScheduleMapper, never()).selectTodayByAgentId(anyLong());
    }

    @Test
    void getMyScheduleToday_delegates()
    {
        AiAgentScheduleVO expected = new AiAgentScheduleVO();
        when(aiAgentScheduleMapper.selectTodayByAgentId(1L)).thenReturn(expected);

        assertSame(expected, service.getMyScheduleToday(1L));
    }

    @Test
    void insertAiAgentSchedule_fillsDefaultsAndCreateTime()
    {
        AiAgentSchedule schedule = new AiAgentSchedule();
        when(aiAgentScheduleMapper.insertAiAgentSchedule(schedule)).thenReturn(1);

        assertEquals(1, service.insertAiAgentSchedule(schedule));
        assertEquals(1, schedule.getScheduleType());
        assertEquals(1, schedule.getStatus());
        assertNotNull(schedule.getCreateTime());
    }

    @Test
    void insertAiAgentSchedule_keepsExplicitValues()
    {
        AiAgentSchedule schedule = schedule(2, 0);
        when(aiAgentScheduleMapper.insertAiAgentSchedule(schedule)).thenReturn(1);

        assertEquals(1, service.insertAiAgentSchedule(schedule));
        assertEquals(2, schedule.getScheduleType());
        assertEquals(0, schedule.getStatus());
    }

    @Test
    void batchInsert_empty_returnsZeroWithoutMapper()
    {
        assertEquals(0, service.batchInsert(null));
        assertEquals(0, service.batchInsert(Collections.emptyList()));
        verify(aiAgentScheduleMapper, never()).batchInsert(any());
    }

    @Test
    void batchInsert_fillsDefaults()
    {
        AiAgentSchedule first = new AiAgentSchedule();
        AiAgentSchedule second = new AiAgentSchedule();
        List<AiAgentSchedule> list = Arrays.asList(first, second);
        when(aiAgentScheduleMapper.batchInsert(list)).thenReturn(2);

        assertEquals(2, service.batchInsert(list));
        assertEquals(1, first.getScheduleType());
        assertEquals(1, second.getStatus());
    }

    @Test
    void updateAiAgentSchedule_fillsUpdateTime()
    {
        AiAgentSchedule schedule = new AiAgentSchedule();
        when(aiAgentScheduleMapper.updateAiAgentSchedule(schedule)).thenReturn(1);

        assertEquals(1, service.updateAiAgentSchedule(schedule));
        assertNotNull(schedule.getUpdateTime());
    }

    @Test
    void deleteByScheduleIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiAgentScheduleMapper.deleteAiAgentScheduleByScheduleIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiAgentScheduleByScheduleIds(ids));
    }

    @Test
    void deleteByScheduleId_delegates()
    {
        when(aiAgentScheduleMapper.deleteAiAgentScheduleByScheduleId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiAgentScheduleByScheduleId(1L));
    }

    @Test
    void checkIn_nullAgent_returnsZero()
    {
        assertEquals(0, service.checkIn(null));
        verify(aiAgentScheduleMapper, never()).checkIn(anyLong(), anyString(), any());
    }

    @Test
    void checkIn_noScheduleToday_returnsZero()
    {
        when(aiAgentScheduleMapper.selectByAgentAndDate(anyLong(), anyString())).thenReturn(null);

        assertEquals(0, service.checkIn(1L));
        verify(aiAgentScheduleMapper, never()).checkIn(anyLong(), anyString(), any());
    }

    @Test
    void checkIn_scheduleExists_delegates()
    {
        when(aiAgentScheduleMapper.selectByAgentAndDate(anyLong(), anyString()))
            .thenReturn(new AiAgentSchedule());
        when(aiAgentScheduleMapper.checkIn(anyLong(), anyString(), any())).thenReturn(1);

        assertEquals(1, service.checkIn(1L));
    }

    @Test
    void checkOut_nullAgent_returnsZero()
    {
        assertEquals(0, service.checkOut(null));
        verify(aiAgentScheduleMapper, never()).checkOut(anyLong(), anyString(), any());
    }

    @Test
    void checkOut_noScheduleToday_returnsZero()
    {
        when(aiAgentScheduleMapper.selectByAgentAndDate(anyLong(), anyString())).thenReturn(null);

        assertEquals(0, service.checkOut(1L));
        verify(aiAgentScheduleMapper, never()).checkOut(anyLong(), anyString(), any());
    }

    @Test
    void checkOut_scheduleExists_delegates()
    {
        when(aiAgentScheduleMapper.selectByAgentAndDate(anyLong(), anyString()))
            .thenReturn(new AiAgentSchedule());
        when(aiAgentScheduleMapper.checkOut(anyLong(), anyString(), any())).thenReturn(1);

        assertEquals(1, service.checkOut(1L));
    }
}
