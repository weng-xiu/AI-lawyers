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
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import ai.lawyers.system.mapper.lawyers.ivr.AiIvrNodeMapper;

/**
 * IVR 节点服务单测。
 *
 * <p>覆盖节点查询、新增/修改/删除委托，批量插入时流程号回填、空集合处理与保存流程节点先删后插。</p>
 *
 * @author ai-lawyers
 */
class AiIvrNodeServiceImplTest
{
    private AiIvrNodeServiceImpl service;

    @Mock private AiIvrNodeMapper aiIvrNodeMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiIvrNodeServiceImpl();
        ReflectionTestUtils.setField(service, "aiIvrNodeMapper", aiIvrNodeMapper);
    }

    @Test
    void selectAiIvrNodeByNodeId_delegates()
    {
        AiIvrNode expected = new AiIvrNode();
        when(aiIvrNodeMapper.selectAiIvrNodeByNodeId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiIvrNodeByNodeId(1L));
    }

    @Test
    void selectAiIvrNodeList_delegates()
    {
        AiIvrNode query = new AiIvrNode();
        List<AiIvrNode> list = Collections.singletonList(new AiIvrNode());
        when(aiIvrNodeMapper.selectAiIvrNodeList(query)).thenReturn(list);

        assertSame(list, service.selectAiIvrNodeList(query));
    }

    @Test
    void selectAiIvrNodeByFlowId_delegates()
    {
        List<AiIvrNode> list = Collections.singletonList(new AiIvrNode());
        when(aiIvrNodeMapper.selectAiIvrNodeByFlowId(1L)).thenReturn(list);

        assertSame(list, service.selectAiIvrNodeByFlowId(1L));
    }

    @Test
    void insertAiIvrNode_delegates()
    {
        AiIvrNode node = new AiIvrNode();
        when(aiIvrNodeMapper.insertAiIvrNode(node)).thenReturn(1);

        assertEquals(1, service.insertAiIvrNode(node));
    }

    @Test
    void batchInsertNodes_null_returnsZeroWithoutMapper()
    {
        assertEquals(0, service.batchInsertNodes(1L, null));
        verify(aiIvrNodeMapper, never()).insertAiIvrNode(any());
    }

    @Test
    void batchInsertNodes_empty_returnsZeroWithoutMapper()
    {
        assertEquals(0, service.batchInsertNodes(1L, Collections.emptyList()));
        verify(aiIvrNodeMapper, never()).insertAiIvrNode(any());
    }

    @Test
    void batchInsertNodes_fillsFlowIdAndSumsCounts()
    {
        AiIvrNode first = new AiIvrNode();
        AiIvrNode second = new AiIvrNode();
        List<AiIvrNode> nodes = Arrays.asList(first, second);
        when(aiIvrNodeMapper.insertAiIvrNode(first)).thenReturn(1);
        when(aiIvrNodeMapper.insertAiIvrNode(second)).thenReturn(1);

        assertEquals(2, service.batchInsertNodes(7L, nodes));
        assertEquals(7L, first.getFlowId());
        assertEquals(7L, second.getFlowId());
        verify(aiIvrNodeMapper, times(2)).insertAiIvrNode(any(AiIvrNode.class));
    }

    @Test
    void updateAiIvrNode_delegates()
    {
        AiIvrNode node = new AiIvrNode();
        when(aiIvrNodeMapper.updateAiIvrNode(node)).thenReturn(1);

        assertEquals(1, service.updateAiIvrNode(node));
    }

    @Test
    void deleteAiIvrNodeByNodeId_delegates()
    {
        when(aiIvrNodeMapper.deleteAiIvrNodeByNodeId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiIvrNodeByNodeId(1L));
    }

    @Test
    void deleteAiIvrNodeByFlowId_delegates()
    {
        when(aiIvrNodeMapper.deleteAiIvrNodeByFlowId(1L)).thenReturn(2);

        assertEquals(2, service.deleteAiIvrNodeByFlowId(1L));
    }

    @Test
    void deleteAiIvrNodeByNodeIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiIvrNodeMapper.deleteAiIvrNodeByNodeIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiIvrNodeByNodeIds(ids));
    }

    @Test
    void saveFlowNodes_deletesThenInserts()
    {
        AiIvrNode node = new AiIvrNode();
        List<AiIvrNode> nodes = Collections.singletonList(node);
        when(aiIvrNodeMapper.insertAiIvrNode(node)).thenReturn(1);

        assertEquals(1, service.saveFlowNodes(3L, nodes));
        verify(aiIvrNodeMapper).deleteAiIvrNodeByFlowId(3L);
        assertEquals(3L, node.getFlowId());
    }
}
