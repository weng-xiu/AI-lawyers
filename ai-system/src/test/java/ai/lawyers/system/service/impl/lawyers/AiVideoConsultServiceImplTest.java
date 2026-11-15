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
import ai.lawyers.system.domain.lawyers.AiVideoConsult;
import ai.lawyers.system.mapper.lawyers.AiVideoConsultMapper;

/**
 * 视频咨询服务单测。
 *
 * <p>覆盖视频咨询的查询、新增、修改与批量删除委托。</p>
 *
 * @author ai-lawyers
 */
class AiVideoConsultServiceImplTest
{
    private AiVideoConsultServiceImpl service;

    @Mock private AiVideoConsultMapper aiVideoConsultMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiVideoConsultServiceImpl();
        ReflectionTestUtils.setField(service, "aiVideoConsultMapper", aiVideoConsultMapper);
    }

    @Test
    void selectAiVideoConsultByConsultId_delegates()
    {
        AiVideoConsult expected = new AiVideoConsult();
        when(aiVideoConsultMapper.selectAiVideoConsultByConsultId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiVideoConsultByConsultId(1L));
    }

    @Test
    void selectAiVideoConsultList_delegates()
    {
        AiVideoConsult query = new AiVideoConsult();
        List<AiVideoConsult> list = Collections.singletonList(new AiVideoConsult());
        when(aiVideoConsultMapper.selectAiVideoConsultList(query)).thenReturn(list);

        assertSame(list, service.selectAiVideoConsultList(query));
    }

    @Test
    void insertAiVideoConsult_delegates()
    {
        AiVideoConsult consult = new AiVideoConsult();
        when(aiVideoConsultMapper.insertAiVideoConsult(consult)).thenReturn(1);

        assertEquals(1, service.insertAiVideoConsult(consult));
    }

    @Test
    void updateAiVideoConsult_delegates()
    {
        AiVideoConsult consult = new AiVideoConsult();
        when(aiVideoConsultMapper.updateAiVideoConsult(consult)).thenReturn(1);

        assertEquals(1, service.updateAiVideoConsult(consult));
    }

    @Test
    void deleteAiVideoConsultByConsultId_delegates()
    {
        when(aiVideoConsultMapper.deleteAiVideoConsultByConsultId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiVideoConsultByConsultId(1L));
    }

    @Test
    void deleteAiVideoConsultByConsultIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiVideoConsultMapper.deleteAiVideoConsultByConsultIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiVideoConsultByConsultIds(ids));
    }
}
