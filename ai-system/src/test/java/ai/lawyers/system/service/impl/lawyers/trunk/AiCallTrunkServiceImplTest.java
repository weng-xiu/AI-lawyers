package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.domain.lawyers.trunk.DialRequest;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiNumberSegmentMapper;
import ai.lawyers.system.service.lawyers.trunk.ICarrierRouteService;
import ai.lawyers.system.service.lawyers.trunk.gateway.CallGatewayFactory;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;
import ai.lawyers.system.service.lawyers.trunk.gateway.ICallGatewayAdapter;

/**
 * 线路与号段配置管理服务单测。
 *
 * <p>覆盖线路增删改查与启停、拨测时的线路/适配器缺失与正常下发、拨测结果线路信息回填，
 * 以及号段增删改后刷新运营商路由缓存与匹配长度补全。</p>
 *
 * @author ai-lawyers
 */
class AiCallTrunkServiceImplTest
{
    private AiCallTrunkServiceImpl service;

    @Mock private AiCallTrunkMapper trunkMapper;
    @Mock private AiNumberSegmentMapper segmentMapper;
    @Mock private CallGatewayFactory gatewayFactory;
    @Mock private ICarrierRouteService carrierRouteService;
    @Mock private ICallGatewayAdapter adapter;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallTrunkServiceImpl();
        ReflectionTestUtils.setField(service, "trunkMapper", trunkMapper);
        ReflectionTestUtils.setField(service, "segmentMapper", segmentMapper);
        ReflectionTestUtils.setField(service, "gatewayFactory", gatewayFactory);
        ReflectionTestUtils.setField(service, "carrierRouteService", carrierRouteService);
    }

    private static AiCallTrunk trunk(Long id, String vendor)
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkId(id);
        trunk.setTrunkCode("T" + id);
        trunk.setVendor(vendor);
        trunk.setCarrier("CMCC");
        trunk.setCallerDisplay("10086");
        return trunk;
    }

    @Test
    void selectByTrunkId_delegates()
    {
        AiCallTrunk expected = trunk(1L, "ASTERISK");
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiCallTrunkByTrunkId(1L));
    }

    @Test
    void selectList_delegates()
    {
        AiCallTrunk query = new AiCallTrunk();
        List<AiCallTrunk> list = Collections.singletonList(trunk(1L, "ASTERISK"));
        when(trunkMapper.selectAiCallTrunkList(query)).thenReturn(list);

        assertSame(list, service.selectAiCallTrunkList(query));
    }

    @Test
    void insert_fillsCreateTime()
    {
        AiCallTrunk trunk = trunk(1L, "ASTERISK");
        when(trunkMapper.insertAiCallTrunk(trunk)).thenReturn(1);

        assertEquals(1, service.insertAiCallTrunk(trunk));
        assertNotNull(trunk.getCreateTime());
    }

    @Test
    void update_fillsUpdateTime()
    {
        AiCallTrunk trunk = trunk(1L, "ASTERISK");
        when(trunkMapper.updateAiCallTrunk(trunk)).thenReturn(1);

        assertEquals(1, service.updateAiCallTrunk(trunk));
        assertNotNull(trunk.getUpdateTime());
    }

    @Test
    void deleteByIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(trunkMapper.deleteAiCallTrunkByTrunkIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiCallTrunkByTrunkIds(ids));
    }

    @Test
    void changeEnableFlag_updatesOnlyFlagAndTime()
    {
        when(trunkMapper.updateAiCallTrunk(anyTrunk())).thenReturn(1);

        assertEquals(1, service.changeEnableFlag(1L, "0"));

        ArgumentCaptor<AiCallTrunk> captor = ArgumentCaptor.forClass(AiCallTrunk.class);
        verify(trunkMapper).updateAiCallTrunk(captor.capture());
        assertEquals(1L, captor.getValue().getTrunkId());
        assertEquals("0", captor.getValue().getEnableFlag());
        assertNotNull(captor.getValue().getUpdateTime());
    }

    private static AiCallTrunk anyTrunk()
    {
        return org.mockito.ArgumentMatchers.any(AiCallTrunk.class);
    }

    @Test
    void testTrunk_missingTrunk_returnsDown()
    {
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(null);

        GatewayHealth health = service.testTrunk(1L);

        assertFalse(health.isReachable());
        assertEquals("线路不存在", health.getMessage());
    }

    @Test
    void testTrunk_missingAdapter_returnsDown()
    {
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(trunk(1L, "UNKNOWN"));
        when(gatewayFactory.get(anyTrunk())).thenReturn(null);

        GatewayHealth health = service.testTrunk(1L);

        assertFalse(health.isReachable());
        assertTrue(health.getMessage().contains("vendor=UNKNOWN"));
    }

    @Test
    void testTrunk_adapterDelegates()
    {
        AiCallTrunk trunk = trunk(1L, "ASTERISK");
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(trunk);
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.checkHealth(trunk)).thenReturn(GatewayHealth.up(12L));

        GatewayHealth health = service.testTrunk(1L);

        assertTrue(health.isReachable());
        assertEquals(12L, health.getLatencyMs());
    }

    @Test
    void testCall_missingTrunk_returnsFail()
    {
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(null);

        DialResult result = service.testCall(1L, "138");

        assertFalse(result.isSuccess());
        assertEquals("TRUNK_NOT_FOUND", result.getErrorCode());
    }

    @Test
    void testCall_missingAdapter_returnsFail()
    {
        AiCallTrunk trunk = trunk(1L, "UNKNOWN");
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(trunk);
        when(gatewayFactory.get(trunk)).thenReturn(null);

        DialResult result = service.testCall(1L, "138");

        assertEquals("GATEWAY_ADAPTER_NOT_FOUND", result.getErrorCode());
    }

    @Test
    void testCall_adapterReturnsNull_returnsFail()
    {
        AiCallTrunk trunk = trunk(1L, "ASTERISK");
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(trunk);
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.originate(org.mockito.ArgumentMatchers.eq(trunk), anyDialRequest())).thenReturn(null);

        assertEquals("GATEWAY_NO_RESPONSE", service.testCall(1L, "138").getErrorCode());
    }

    private static DialRequest anyDialRequest()
    {
        return org.mockito.ArgumentMatchers.any(DialRequest.class);
    }

    @Test
    void testCall_success_fillsRequestAndTrunkInfo()
    {
        AiCallTrunk trunk = trunk(1L, "ASTERISK");
        when(trunkMapper.selectAiCallTrunkByTrunkId(1L)).thenReturn(trunk);
        when(gatewayFactory.get(trunk)).thenReturn(adapter);
        when(adapter.originate(org.mockito.ArgumentMatchers.eq(trunk), anyDialRequest()))
            .thenReturn(DialResult.ok("uuid-1"));

        DialResult result = service.testCall(1L, "13800138000");

        assertTrue(result.isSuccess());
        assertEquals(1L, result.getTrunkId());
        assertEquals("T1", result.getTrunkCode());
        assertEquals("CMCC", result.getTrunkCarrier());

        ArgumentCaptor<DialRequest> captor = ArgumentCaptor.forClass(DialRequest.class);
        verify(adapter).originate(org.mockito.ArgumentMatchers.eq(trunk), captor.capture());
        DialRequest request = captor.getValue();
        assertEquals("13800138000", request.getCalleeNumber());
        assertEquals("10086", request.getCallerNumber());
        assertEquals(30, request.getRingTimeout());
        assertEquals(false, request.isEnableRecord());
        assertEquals("PLAYBACK", request.getAnswerAction());
        assertEquals("拨测", request.getCreateBy());
    }

    @Test
    void selectSegmentList_delegates()
    {
        AiNumberSegment query = new AiNumberSegment();
        List<AiNumberSegment> list = Collections.singletonList(new AiNumberSegment());
        when(segmentMapper.selectAiNumberSegmentList(query)).thenReturn(list);

        assertSame(list, service.selectAiNumberSegmentList(query));
    }

    @Test
    void insertSegment_fillsMatchLengthAndRefreshesCache()
    {
        AiNumberSegment segment = new AiNumberSegment();
        segment.setSegmentPrefix("1380");
        when(segmentMapper.insertAiNumberSegment(segment)).thenReturn(1);

        assertEquals(1, service.insertAiNumberSegment(segment));
        assertEquals(4, segment.getMatchLength());
        assertNotNull(segment.getCreateTime());
        verify(carrierRouteService).refreshSegmentCache();
    }

    @Test
    void insertSegment_keepsExplicitMatchLength()
    {
        AiNumberSegment segment = new AiNumberSegment();
        segment.setSegmentPrefix("1380");
        segment.setMatchLength(3);
        when(segmentMapper.insertAiNumberSegment(segment)).thenReturn(1);

        assertEquals(1, service.insertAiNumberSegment(segment));
        assertEquals(3, segment.getMatchLength());
    }

    @Test
    void updateSegment_refreshesMatchLengthAndCache()
    {
        AiNumberSegment segment = new AiNumberSegment();
        segment.setSegmentPrefix("13800");
        when(segmentMapper.updateAiNumberSegment(segment)).thenReturn(1);

        assertEquals(1, service.updateAiNumberSegment(segment));
        assertEquals(5, segment.getMatchLength());
        assertNotNull(segment.getUpdateTime());
        verify(carrierRouteService).refreshSegmentCache();
    }

    @Test
    void deleteSegments_refreshesCache()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(segmentMapper.deleteAiNumberSegmentBySegmentIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiNumberSegmentBySegmentIds(ids));
        verify(carrierRouteService).refreshSegmentCache();
    }
}
