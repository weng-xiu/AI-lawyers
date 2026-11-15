package ai.lawyers.system.service.impl.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrFlow;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrEdgeMapper;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrFlowMapper;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrNodeMapper;

/**
 * IVR 流程服务单测。
 *
 * <p>覆盖流程查询与删除委托、发布状态流转，以及保存流程设计时节点/连线整体覆盖、
 * 临时ID映射与排序号默认值。</p>
 *
 * @author ai-lawyers
 */
class AiIvrFlowServiceImplTest
{
    private AiIvrFlowServiceImpl service;

    @Mock private AiIvrFlowMapper aiIvrFlowMapper;
    @Mock private AiIvrNodeMapper aiIvrNodeMapper;
    @Mock private AiIvrEdgeMapper aiIvrEdgeMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiIvrFlowServiceImpl();
        ReflectionTestUtils.setField(service, "aiIvrFlowMapper", aiIvrFlowMapper);
        ReflectionTestUtils.setField(service, "aiIvrNodeMapper", aiIvrNodeMapper);
        ReflectionTestUtils.setField(service, "aiIvrEdgeMapper", aiIvrEdgeMapper);
    }

    private static AiIvrNode node(Long nodeId, Integer sortOrder)
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(nodeId);
        node.setSortOrder(sortOrder);
        return node;
    }

    private static AiIvrEdge edge(Long source, Long target, Integer sortOrder)
    {
        AiIvrEdge edge = new AiIvrEdge();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        edge.setSortOrder(sortOrder);
        return edge;
    }

    @Test
    void selectAiIvrFlowByFlowId_delegates()
    {
        AiIvrFlow expected = new AiIvrFlow();
        when(aiIvrFlowMapper.selectAiIvrFlowByFlowId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiIvrFlowByFlowId(1L));
    }

    @Test
    void selectAiIvrFlowList_delegates()
    {
        AiIvrFlow query = new AiIvrFlow();
        List<AiIvrFlow> list = Collections.singletonList(new AiIvrFlow());
        when(aiIvrFlowMapper.selectAiIvrFlowList(query)).thenReturn(list);

        assertSame(list, service.selectAiIvrFlowList(query));
    }

    @Test
    void insertAiIvrFlow_delegates()
    {
        AiIvrFlow flow = new AiIvrFlow();
        when(aiIvrFlowMapper.insertAiIvrFlow(flow)).thenReturn(1);

        assertEquals(1, service.insertAiIvrFlow(flow));
    }

    @Test
    void updateAiIvrFlow_delegates()
    {
        AiIvrFlow flow = new AiIvrFlow();
        when(aiIvrFlowMapper.updateAiIvrFlow(flow)).thenReturn(1);

        assertEquals(1, service.updateAiIvrFlow(flow));
    }

    @Test
    void deleteByFlowId_delegates()
    {
        when(aiIvrFlowMapper.deleteAiIvrFlowByFlowId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiIvrFlowByFlowId(1L));
    }

    @Test
    void deleteByFlowIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiIvrFlowMapper.deleteAiIvrFlowByFlowIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiIvrFlowByFlowIds(ids));
    }

    @Test
    void selectDefaultFlow_delegates()
    {
        AiIvrFlow expected = new AiIvrFlow();
        when(aiIvrFlowMapper.selectDefaultFlow()).thenReturn(expected);

        assertSame(expected, service.selectDefaultFlow());
    }

    @Test
    void selectPublishedFlows_delegates()
    {
        List<AiIvrFlow> list = Collections.singletonList(new AiIvrFlow());
        when(aiIvrFlowMapper.selectPublishedFlows()).thenReturn(list);

        assertSame(list, service.selectPublishedFlows());
    }

    @Test
    void publishFlow_setsPublishedStatus()
    {
        when(aiIvrFlowMapper.updateAiIvrFlow(any(AiIvrFlow.class))).thenReturn(1);

        assertEquals(1, service.publishFlow(1L));

        ArgumentCaptor<AiIvrFlow> captor = ArgumentCaptor.forClass(AiIvrFlow.class);
        verify(aiIvrFlowMapper).updateAiIvrFlow(captor.capture());
        assertEquals(1L, captor.getValue().getFlowId());
        assertEquals("1", captor.getValue().getStatus());
    }

    @Test
    void saveFlowDesign_nullFlowId_returnsZero()
    {
        assertEquals(0, service.saveFlowDesign(null, "{}", null, null));
        verify(aiIvrFlowMapper, never()).updateAiIvrFlow(any());
    }

    @Test
    void saveFlowDesign_replacesNodesAndRemapsEdgeIds()
    {
        when(aiIvrFlowMapper.updateAiIvrFlow(any(AiIvrFlow.class))).thenReturn(1);
        AiIvrNode first = node(100L, null);
        AiIvrNode second = node(101L, 5);
        when(aiIvrNodeMapper.insertAiIvrNode(first)).thenAnswer(invocation -> {
            first.setNodeId(1L);
            return 1;
        });
        when(aiIvrNodeMapper.insertAiIvrNode(second)).thenAnswer(invocation -> {
            second.setNodeId(2L);
            return 1;
        });
        AiIvrEdge edge = edge(100L, 101L, null);

        assertEquals(1, service.saveFlowDesign(9L, "{\"a\":1}",
            Arrays.asList(first, second), Collections.singletonList(edge)));

        verify(aiIvrNodeMapper).deleteAiIvrNodeByFlowId(9L);
        verify(aiIvrEdgeMapper).deleteAiIvrEdgeByFlowId(9L);
        assertEquals(9L, first.getFlowId());
        assertEquals(0, first.getSortOrder());
        assertEquals(5, second.getSortOrder());

        ArgumentCaptor<AiIvrEdge> captor = ArgumentCaptor.forClass(AiIvrEdge.class);
        verify(aiIvrEdgeMapper).insertAiIvrEdge(captor.capture());
        AiIvrEdge saved = captor.getValue();
        assertNull(saved.getEdgeId());
        assertEquals(9L, saved.getFlowId());
        assertEquals(1L, saved.getSourceNodeId());
        assertEquals(2L, saved.getTargetNodeId());
        assertEquals(0, saved.getSortOrder());
    }

    @Test
    void saveFlowDesign_emptyNodesAndEdges_onlyUpdatesAndDeletes()
    {
        when(aiIvrFlowMapper.updateAiIvrFlow(any(AiIvrFlow.class))).thenReturn(1);

        assertEquals(1, service.saveFlowDesign(9L, "{}", Collections.emptyList(), null));

        verify(aiIvrNodeMapper).deleteAiIvrNodeByFlowId(9L);
        verify(aiIvrEdgeMapper).deleteAiIvrEdgeByFlowId(9L);
        verify(aiIvrNodeMapper, never()).insertAiIvrNode(any());
        verify(aiIvrEdgeMapper, never()).insertAiIvrEdge(any());
        verify(aiIvrFlowMapper, times(1)).updateAiIvrFlow(any());
    }
}
