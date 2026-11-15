package ai.lawyers.system.service.impl.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.enums.CarrierEnum;
import ai.lawyers.system.mapper.lawyers.trunk.AiCallTrunkMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiNumberSegmentMapper;

/**
 * 运营商识别与线路选路服务单测。
 *
 * <p>覆盖号段缓存刷新、最长前缀优先识别、运营商未知兜底、候选线路过滤与按编码取线路。</p>
 *
 * @author ai-lawyers
 */
class CarrierRouteServiceImplTest
{
    private CarrierRouteServiceImpl service;

    @Mock private AiNumberSegmentMapper numberSegmentMapper;
    @Mock private AiCallTrunkMapper callTrunkMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new CarrierRouteServiceImpl();
        ReflectionTestUtils.setField(service, "numberSegmentMapper", numberSegmentMapper);
        ReflectionTestUtils.setField(service, "callTrunkMapper", callTrunkMapper);
    }

    private static AiNumberSegment segment(String prefix, String carrier)
    {
        AiNumberSegment segment = new AiNumberSegment();
        segment.setSegmentPrefix(prefix);
        segment.setCarrier(carrier);
        return segment;
    }

    private static AiCallTrunk trunk(Long trunkId)
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkId(trunkId);
        return trunk;
    }

    @Test
    void init_mapperThrows_doesNotPropagate()
    {
        when(numberSegmentMapper.selectAllEnabled()).thenThrow(new IllegalStateException("table missing"));

        service.init();
    }

    @Test
    void refreshSegmentCache_skipsEmptyPrefix()
    {
        when(numberSegmentMapper.selectAllEnabled())
            .thenReturn(Arrays.asList(segment("138", "CMCC"), segment("", "NONE"), segment(null, "NONE")));

        service.refreshSegmentCache();

        verify(numberSegmentMapper).selectAllEnabled();
    }

    @Test
    void recognizeSegment_emptyNumber_returnsNull()
    {
        assertNull(service.recognizeSegment(null));
        assertNull(service.recognizeSegment("  "));
    }

    @Test
    void recognizeSegment_longestPrefixWins()
    {
        AiNumberSegment shortSeg = segment("138", "CMCC");
        AiNumberSegment longSeg = segment("1380", "CUCC");
        when(numberSegmentMapper.selectAllEnabled()).thenReturn(Arrays.asList(shortSeg, longSeg));

        assertEquals("1380", service.recognizeSegment("13800138000").getSegmentPrefix());
    }

    @Test
    void recognizeSegment_noMatch_returnsNull()
    {
        when(numberSegmentMapper.selectAllEnabled()).thenReturn(Collections.singletonList(segment("139", "CMCC")));

        assertNull(service.recognizeSegment("13800138000"));
    }

    @Test
    void recognizeCarrier_matched_returnsCarrier()
    {
        when(numberSegmentMapper.selectAllEnabled())
            .thenReturn(Collections.singletonList(segment("138", "CMCC")));

        assertEquals("CMCC", service.recognizeCarrier("13800138000"));
    }

    @Test
    void recognizeCarrier_noMatch_returnsUnknown()
    {
        when(numberSegmentMapper.selectAllEnabled()).thenReturn(Collections.emptyList());

        assertEquals(CarrierEnum.UNKNOWN.getCode(), service.recognizeCarrier("12345"));
    }

    @Test
    void selectCandidateTrunks_nullCarrier_usesUnknown()
    {
        List<AiCallTrunk> candidates = Collections.singletonList(trunk(1L));
        when(callTrunkMapper.selectAvailableTrunks(CarrierEnum.UNKNOWN.getCode())).thenReturn(candidates);

        assertSame(candidates, service.selectCandidateTrunks(null, null));
    }

    @Test
    void selectCandidateTrunks_noAvailable_returnsEmpty()
    {
        when(callTrunkMapper.selectAvailableTrunks("CMCC")).thenReturn(null);

        assertTrue(service.selectCandidateTrunks("CMCC", null).isEmpty());
    }

    @Test
    void selectCandidateTrunks_excludesTrunkIds()
    {
        List<AiCallTrunk> candidates = Arrays.asList(trunk(1L), trunk(2L), trunk(3L));
        when(callTrunkMapper.selectAvailableTrunks("CMCC")).thenReturn(candidates);

        List<AiCallTrunk> result = service.selectCandidateTrunks("CMCC", Collections.singletonList(2L));

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getTrunkId());
        assertEquals(3L, result.get(1).getTrunkId());
    }

    @Test
    void getTrunkByCode_blank_returnsNull()
    {
        assertNull(service.getTrunkByCode(null));
        assertNull(service.getTrunkByCode("   "));
    }

    @Test
    void getTrunkByCode_trimsAndDelegates()
    {
        AiCallTrunk expected = trunk(1L);
        when(callTrunkMapper.selectAiCallTrunkByCode("T1")).thenReturn(expected);

        assertSame(expected, service.getTrunkByCode(" T1 "));
    }
}
