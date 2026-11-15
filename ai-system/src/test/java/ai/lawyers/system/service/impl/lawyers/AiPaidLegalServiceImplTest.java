package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiPaidLegalService;
import ai.lawyers.system.mapper.lawyers.AiPaidLegalServiceMapper;

/**
 * 有偿法律服务单测。
 *
 * <p>覆盖有偿法律服务的查询、新增/修改时时间回填与单个/批量删除委托。</p>
 *
 * @author ai-lawyers
 */
class AiPaidLegalServiceImplTest
{
    private AiPaidLegalServiceImpl service;

    @Mock private AiPaidLegalServiceMapper aiPaidLegalServiceMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiPaidLegalServiceImpl();
        ReflectionTestUtils.setField(service, "aiPaidLegalServiceMapper", aiPaidLegalServiceMapper);
    }

    @Test
    void selectAiPaidLegalServiceByPaidId_delegates()
    {
        AiPaidLegalService expected = new AiPaidLegalService();
        when(aiPaidLegalServiceMapper.selectAiPaidLegalServiceById(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiPaidLegalServiceByPaidId(1L));
    }

    @Test
    void selectAiPaidLegalServiceList_delegates()
    {
        AiPaidLegalService query = new AiPaidLegalService();
        List<AiPaidLegalService> list = Collections.singletonList(new AiPaidLegalService());
        when(aiPaidLegalServiceMapper.selectAiPaidLegalServiceList(query)).thenReturn(list);

        assertSame(list, service.selectAiPaidLegalServiceList(query));
    }

    @Test
    void insertAiPaidLegalService_fillsCreateTime()
    {
        AiPaidLegalService entity = new AiPaidLegalService();
        when(aiPaidLegalServiceMapper.insertAiPaidLegalService(entity)).thenReturn(1);

        assertEquals(1, service.insertAiPaidLegalService(entity));
        assertNotNull(entity.getCreateTime());
    }

    @Test
    void updateAiPaidLegalService_fillsUpdateTime()
    {
        AiPaidLegalService entity = new AiPaidLegalService();
        when(aiPaidLegalServiceMapper.updateAiPaidLegalService(entity)).thenReturn(1);

        assertEquals(1, service.updateAiPaidLegalService(entity));
        assertNotNull(entity.getUpdateTime());
    }

    @Test
    void deleteAiPaidLegalServiceByPaidIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiPaidLegalServiceMapper.deleteAiPaidLegalServiceByIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiPaidLegalServiceByPaidIds(ids));
    }

    @Test
    void deleteAiPaidLegalServiceByPaidId_delegates()
    {
        when(aiPaidLegalServiceMapper.deleteAiPaidLegalServiceById(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiPaidLegalServiceByPaidId(1L));
    }
}
