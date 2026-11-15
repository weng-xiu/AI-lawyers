package ai.lawyers.system.service.impl.lawyers.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
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
import ai.lawyers.system.domain.lawyers.outbound.AiOutboundCallee;
import ai.lawyers.system.mapper.lawyers.outbound.AiOutboundCalleeMapper;

/**
 * 外呼被叫服务单测。
 *
 * <p>覆盖被叫查询、新增/修改/删除委托，以及批量导入时任务号回填与空集合处理。</p>
 *
 * @author ai-lawyers
 */
class AiOutboundCalleeServiceImplTest
{
    private AiOutboundCalleeServiceImpl service;

    @Mock private AiOutboundCalleeMapper aiOutboundCalleeMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiOutboundCalleeServiceImpl();
        ReflectionTestUtils.setField(service, "aiOutboundCalleeMapper", aiOutboundCalleeMapper);
    }

    @Test
    void selectAiOutboundCalleeByCalleeId_delegates()
    {
        AiOutboundCallee expected = new AiOutboundCallee();
        when(aiOutboundCalleeMapper.selectAiOutboundCalleeByCalleeId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiOutboundCalleeByCalleeId(1L));
    }

    @Test
    void selectAiOutboundCalleeList_delegates()
    {
        AiOutboundCallee query = new AiOutboundCallee();
        List<AiOutboundCallee> list = Collections.singletonList(new AiOutboundCallee());
        when(aiOutboundCalleeMapper.selectAiOutboundCalleeList(query)).thenReturn(list);

        assertSame(list, service.selectAiOutboundCalleeList(query));
    }

    @Test
    void selectAiOutboundCalleeByTaskId_delegates()
    {
        List<AiOutboundCallee> list = Collections.singletonList(new AiOutboundCallee());
        when(aiOutboundCalleeMapper.selectAiOutboundCalleeByTaskId(1L)).thenReturn(list);

        assertSame(list, service.selectAiOutboundCalleeByTaskId(1L));
    }

    @Test
    void insertAiOutboundCallee_delegates()
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        when(aiOutboundCalleeMapper.insertAiOutboundCallee(callee)).thenReturn(1);

        assertEquals(1, service.insertAiOutboundCallee(callee));
    }

    @Test
    void batchInsertCallees_null_returnsZeroWithoutMapper()
    {
        assertEquals(0, service.batchInsertCallees(1L, null));
        verify(aiOutboundCalleeMapper, never()).batchInsertCallees(any());
    }

    @Test
    void batchInsertCallees_empty_returnsZeroWithoutMapper()
    {
        assertEquals(0, service.batchInsertCallees(1L, Collections.emptyList()));
        verify(aiOutboundCalleeMapper, never()).batchInsertCallees(any());
    }

    @Test
    void batchInsertCallees_fillsTaskIdAndDelegates()
    {
        AiOutboundCallee first = new AiOutboundCallee();
        AiOutboundCallee second = new AiOutboundCallee();
        List<AiOutboundCallee> callees = Arrays.asList(first, second);
        when(aiOutboundCalleeMapper.batchInsertCallees(callees)).thenReturn(2);

        assertEquals(2, service.batchInsertCallees(9L, callees));
        assertEquals(9L, first.getTaskId());
        assertEquals(9L, second.getTaskId());
    }

    @Test
    void updateAiOutboundCallee_delegates()
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        when(aiOutboundCalleeMapper.updateAiOutboundCallee(callee)).thenReturn(1);

        assertEquals(1, service.updateAiOutboundCallee(callee));
    }

    @Test
    void deleteAiOutboundCalleeByCalleeId_delegates()
    {
        when(aiOutboundCalleeMapper.deleteAiOutboundCalleeByCalleeId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiOutboundCalleeByCalleeId(1L));
    }

    @Test
    void deleteAiOutboundCalleeByTaskId_delegates()
    {
        when(aiOutboundCalleeMapper.deleteAiOutboundCalleeByTaskId(1L)).thenReturn(3);

        assertEquals(3, service.deleteAiOutboundCalleeByTaskId(1L));
    }

    @Test
    void deleteAiOutboundCalleeByCalleeIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiOutboundCalleeMapper.deleteAiOutboundCalleeByCalleeIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiOutboundCalleeByCalleeIds(ids));
    }

    @Test
    void importCalleesFromExcel_delegatesToBatchInsert()
    {
        AiOutboundCallee callee = new AiOutboundCallee();
        List<AiOutboundCallee> callees = Collections.singletonList(callee);
        when(aiOutboundCalleeMapper.batchInsertCallees(callees)).thenReturn(1);

        assertEquals(1, service.importCalleesFromExcel(5L, callees));
        assertEquals(5L, callee.getTaskId());
    }
}
