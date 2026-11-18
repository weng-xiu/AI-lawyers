package ai.lawyers.web.controller.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.lawyers.system.domain.lawyers.ivr.AiIvrEdge;
import ai.lawyers.system.domain.lawyers.ivr.AiIvrNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * IVR流程设计保存请求测试：属性读写与默认值。
 *
 * @author ai-lawyers
 */
class IvrFlowDesignRequestTest
{
    @Test
    void 属性读写()
    {
        AiIvrNode node = new AiIvrNode();
        node.setNodeId(10L);
        AiIvrEdge edge = new AiIvrEdge();
        edge.setEdgeId(20L);

        IvrFlowDesignRequest request = new IvrFlowDesignRequest();
        request.setFlowId(1L);
        request.setFlowData("{\"nodes\":[],\"edges\":[]}");
        request.setNodes(List.of(node));
        request.setEdges(List.of(edge));

        assertEquals(1L, request.getFlowId());
        assertEquals("{\"nodes\":[],\"edges\":[]}", request.getFlowData());
        assertEquals(1, request.getNodes().size());
        assertEquals(10L, request.getNodes().get(0).getNodeId());
        assertEquals(1, request.getEdges().size());
        assertEquals(20L, request.getEdges().get(0).getEdgeId());
    }

    @Test
    void 默认值为空()
    {
        IvrFlowDesignRequest request = new IvrFlowDesignRequest();

        assertNull(request.getFlowId());
        assertNull(request.getFlowData());
        assertNull(request.getNodes());
        assertNull(request.getEdges());
    }
}
