package ai.lawyers.system.service.impl.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrEdgeMapper;

/**
 * IVR 连线服务单测。
 *
 * <p>覆盖连线查询、新增/修改/删除委托，批量插入时流程号回填、空集合处理与保存流程连线先删后插。</p>
 *
 * @author ai-lawyers
 */
class AiIvrEdgeServiceImplTest
{
    private AiIvrEdgeServiceImpl service;

    @Mock private AiIvrEdgeMapper aiIvrEdgeMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiIvrEdgeServiceImpl();
        ReflectionTestUtils.setField(service, "aiIvrEdgeMapper", aiIvrEdgeMapper);
    }

    @Test
    void selectAiIvrEdgeByEdgeId_delegates()
    {
        AiIvrEdge expected = new AiIvrEdge();
        when(aiIvrEdgeMapper.selectAiIvrEdgeByEdgeId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiIvrEdgeByEdgeId(1L));
    }

    @Test
    void selectAiIvrEdgeList_delegates()
    {
        AiIvrEdge query = new AiIvrEdge();
        List<AiIvrEdge> list = Collections.singletonList(new AiIvrEdge());
        when(aiIvrEdgeMapper.selectAiIvrEdgeList(query)).thenReturn(list);

        assertSame(list, service.selectAiIvrEdgeList(query));
    }

    @Test
    void selectAiIvrEdgeByFlowId_delegates()
    {
        List<AiIvrEdge> list = Collections.singletonList(new AiIvrEdge());
        when(aiIvrEdgeMapper.selectAiIvrEdgeByFlowId(1L)).thenReturn(list);

        assertSame(list, service.selectAiIvrEdgeByFlowId(1L));
    }

    @Test
    void insertAiIvrEdge_delegates()
    {
        AiIvrEdge edge = new AiIvrEdge();
        when(aiIvrEdgeMapper.insertAiIvrEdge(edge)).thenReturn(1);

        assertEquals(1, service.insertAiIvrEdge(edge));
    }

    @Test
    void batchInsertEdges_null_returnsZeroWithoutMapper()
    {
        assertEquals(0, service.batchInsertEdges(1L, null));
        verify(aiIvrEdgeMapper, never()).insertAiIvrEdge(any());
    }

    @Test
    void batchInsertEdges_empty_returnsZeroWithoutMapper()
    {
        assertEquals(0, service.batchInsertEdges(1L, Collections.emptyList()));
        verify(aiIvrEdgeMapper, never()).insertAiIvrEdge(any());
    }

    @Test
    void batchInsertEdges_fillsFlowIdAndSumsCounts()
    {
        AiIvrEdge first = new AiIvrEdge();
        AiIvrEdge second = new AiIvrEdge();
        List<AiIvrEdge> edges = Arrays.asList(first, second);
        when(aiIvrEdgeMapper.insertAiIvrEdge(first)).thenReturn(1);
        when(aiIvrEdgeMapper.insertAiIvrEdge(second)).thenReturn(0);

        assertEquals(1, service.batchInsertEdges(7L, edges));
        assertEquals(7L, first.getFlowId());
        assertEquals(7L, second.getFlowId());
        verify(aiIvrEdgeMapper, times(2)).insertAiIvrEdge(any(AiIvrEdge.class));
    }

    @Test
    void updateAiIvrEdge_delegates()
    {
        AiIvrEdge edge = new AiIvrEdge();
        when(aiIvrEdgeMapper.updateAiIvrEdge(edge)).thenReturn(1);

        assertEquals(1, service.updateAiIvrEdge(edge));
    }

    @Test
    void deleteAiIvrEdgeByEdgeId_delegates()
    {
        when(aiIvrEdgeMapper.deleteAiIvrEdgeByEdgeId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiIvrEdgeByEdgeId(1L));
    }

    @Test
    void deleteAiIvrEdgeByFlowId_delegates()
    {
        when(aiIvrEdgeMapper.deleteAiIvrEdgeByFlowId(1L)).thenReturn(2);

        assertEquals(2, service.deleteAiIvrEdgeByFlowId(1L));
    }

    @Test
    void deleteAiIvrEdgeByEdgeIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiIvrEdgeMapper.deleteAiIvrEdgeByEdgeIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiIvrEdgeByEdgeIds(ids));
    }

    @Test
    void saveFlowEdges_deletesThenInserts()
    {
        AiIvrEdge edge = new AiIvrEdge();
        List<AiIvrEdge> edges = Collections.singletonList(edge);
        when(aiIvrEdgeMapper.insertAiIvrEdge(edge)).thenReturn(1);

        assertEquals(1, service.saveFlowEdges(3L, edges));
        verify(aiIvrEdgeMapper).deleteAiIvrEdgeByFlowId(3L);
        assertEquals(3L, edge.getFlowId());
    }
}
