package ai.lawyers.system.service.impl.lawyers.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.schedule.AiWorkShift;
import ai.lawyers.system.mapper.lawyers.schedule.AiWorkShiftMapper;

/**
 * 班次定义服务单测。
 *
 * <p>覆盖班次查询、新增时状态与跨天标记默认值及创建时间回填、修改时间回填与删除委托。</p>
 *
 * @author ai-lawyers
 */
class AiWorkShiftServiceImplTest
{
    private AiWorkShiftServiceImpl service;

    @Mock private AiWorkShiftMapper aiWorkShiftMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiWorkShiftServiceImpl();
        ReflectionTestUtils.setField(service, "aiWorkShiftMapper", aiWorkShiftMapper);
    }

    @Test
    void selectAiWorkShiftByShiftId_delegates()
    {
        AiWorkShift expected = new AiWorkShift();
        when(aiWorkShiftMapper.selectAiWorkShiftByShiftId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiWorkShiftByShiftId(1L));
    }

    @Test
    void selectAiWorkShiftList_delegates()
    {
        AiWorkShift query = new AiWorkShift();
        List<AiWorkShift> list = Collections.singletonList(new AiWorkShift());
        when(aiWorkShiftMapper.selectAiWorkShiftList(query)).thenReturn(list);

        assertSame(list, service.selectAiWorkShiftList(query));
    }

    @Test
    void selectAllEnabled_delegates()
    {
        List<AiWorkShift> list = Collections.singletonList(new AiWorkShift());
        when(aiWorkShiftMapper.selectAllEnabled()).thenReturn(list);

        assertSame(list, service.selectAllEnabled());
    }

    @Test
    void insertAiWorkShift_fillsDefaultsAndCreateTime()
    {
        AiWorkShift shift = new AiWorkShift();
        when(aiWorkShiftMapper.insertAiWorkShift(any(AiWorkShift.class))).thenReturn(1);

        assertEquals(1, service.insertAiWorkShift(shift));
        assertEquals(1, shift.getStatus());
        assertEquals(0, shift.getIsOvernight());
        assertNotNull(shift.getCreateTime());
    }

    @Test
    void insertAiWorkShift_keepsExplicitValues()
    {
        AiWorkShift shift = new AiWorkShift();
        shift.setStatus(0);
        shift.setIsOvernight(1);
        when(aiWorkShiftMapper.insertAiWorkShift(shift)).thenReturn(1);

        assertEquals(1, service.insertAiWorkShift(shift));
        assertEquals(0, shift.getStatus());
        assertEquals(1, shift.getIsOvernight());
    }

    @Test
    void updateAiWorkShift_fillsUpdateTime()
    {
        AiWorkShift shift = new AiWorkShift();
        when(aiWorkShiftMapper.updateAiWorkShift(shift)).thenReturn(1);

        assertEquals(1, service.updateAiWorkShift(shift));
        assertNotNull(shift.getUpdateTime());
    }

    @Test
    void deleteAiWorkShiftByShiftIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiWorkShiftMapper.deleteAiWorkShiftByShiftIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiWorkShiftByShiftIds(ids));
    }

    @Test
    void deleteAiWorkShiftByShiftId_delegates()
    {
        when(aiWorkShiftMapper.deleteAiWorkShiftByShiftId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiWorkShiftByShiftId(1L));
    }
}
