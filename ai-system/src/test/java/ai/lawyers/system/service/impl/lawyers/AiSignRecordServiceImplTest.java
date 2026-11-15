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
import ai.lawyers.system.domain.lawyers.AiSignRecord;
import ai.lawyers.system.mapper.lawyers.AiSignRecordMapper;

/**
 * 签署记录服务单测。
 *
 * <p>覆盖签署记录的查询、新增、修改与单个/批量删除委托。</p>
 *
 * @author ai-lawyers
 */
class AiSignRecordServiceImplTest
{
    private AiSignRecordServiceImpl service;

    @Mock private AiSignRecordMapper aiSignRecordMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiSignRecordServiceImpl();
        ReflectionTestUtils.setField(service, "aiSignRecordMapper", aiSignRecordMapper);
    }

    @Test
    void selectAiSignRecordBySignId_delegates()
    {
        AiSignRecord expected = new AiSignRecord();
        when(aiSignRecordMapper.selectAiSignRecordBySignId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiSignRecordBySignId(1L));
    }

    @Test
    void selectAiSignRecordList_delegates()
    {
        AiSignRecord query = new AiSignRecord();
        List<AiSignRecord> list = Collections.singletonList(new AiSignRecord());
        when(aiSignRecordMapper.selectAiSignRecordList(query)).thenReturn(list);

        assertSame(list, service.selectAiSignRecordList(query));
    }

    @Test
    void insertAiSignRecord_delegates()
    {
        AiSignRecord record = new AiSignRecord();
        when(aiSignRecordMapper.insertAiSignRecord(record)).thenReturn(1);

        assertEquals(1, service.insertAiSignRecord(record));
    }

    @Test
    void updateAiSignRecord_delegates()
    {
        AiSignRecord record = new AiSignRecord();
        when(aiSignRecordMapper.updateAiSignRecord(record)).thenReturn(1);

        assertEquals(1, service.updateAiSignRecord(record));
    }

    @Test
    void deleteAiSignRecordBySignIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiSignRecordMapper.deleteAiSignRecordBySignIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiSignRecordBySignIds(ids));
    }

    @Test
    void deleteAiSignRecordBySignId_delegates()
    {
        when(aiSignRecordMapper.deleteAiSignRecordBySignId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiSignRecordBySignId(1L));
    }
}
