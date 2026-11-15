package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallAgentStatus;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallAgentStatusMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;

/**
 * 话单 Service 单测。
 *
 * <p>覆盖查询条件 token 派生、写前号码加密与盲索引、token 重建及失败隔离、
 * 统计与工作台聚合口径、其余透传方法。</p>
 *
 * @author ai-lawyers
 */
class AiCallRecordServiceImplTest
{
    private static final String PHONE = "13800138000";

    private AiCallRecordServiceImpl service;

    @Mock private AiCallRecordMapper recordMapper;
    @Mock private AiCallAgentStatusMapper agentStatusMapper;
    @Mock private IPiiSearchTokenService piiSearchTokenService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallRecordServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallRecordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "aiCallAgentStatusMapper", agentStatusMapper);
        ReflectionTestUtils.setField(service, "piiSearchTokenService", piiSearchTokenService);
    }

    // ---------------- 查询 ----------------

    @Test
    void selectByRecordId_delegates()
    {
        AiCallRecord record = new AiCallRecord();
        when(recordMapper.selectAiCallRecordByRecordId(1L)).thenReturn(record);

        assertSame(record, service.selectAiCallRecordByRecordId(1L));
    }

    @Test
    void selectList_withPhone_derivesTokenGroups()
    {
        AiCallRecord query = new AiCallRecord();
        query.setCallerNumber(PHONE);
        when(recordMapper.selectAiCallRecordList(query)).thenReturn(Collections.emptyList());

        service.selectAiCallRecordList(query);

        assertNotNull(query.getTokenGroups());
        assertEquals(PiiCryptoUtils.tokenGroups(PHONE), query.getTokenGroups());
    }

    @Test
    void selectList_existingTokenGroupsKept()
    {
        AiCallRecord query = new AiCallRecord();
        query.setCallerNumber(PHONE);
        List<List<String>> preset = new ArrayList<>();
        preset.add(Collections.singletonList("t"));
        query.setTokenGroups(preset);
        when(recordMapper.selectAiCallRecordList(query)).thenReturn(Collections.emptyList());

        service.selectAiCallRecordList(query);

        assertSame(preset, query.getTokenGroups());
    }

    @Test
    void selectList_withoutPhone_leavesTokenGroupsNull()
    {
        AiCallRecord query = new AiCallRecord();
        when(recordMapper.selectAiCallRecordList(query)).thenReturn(Collections.emptyList());

        service.selectAiCallRecordList(query);

        assertNull(query.getTokenGroups());
    }

    @Test
    void selectList_nullQuery_doesNotThrow()
    {
        assertEquals(Collections.emptyList(), service.selectAiCallRecordList(null));
        verify(recordMapper).selectAiCallRecordList(isNull());
    }

    // ---------------- 新增/修改 ----------------

    @Test
    void insert_encryptsNumber_buildsBlindIndexAndRebuildsTokens()
    {
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(PHONE);
        givenInsertAssignsId(77L);

        assertEquals(1, service.insertAiCallRecord(record));

        assertTrue(PiiCryptoUtils.isEncrypted(record.getCallerNumber()));
        assertEquals(PHONE, PiiCryptoUtils.decrypt(record.getCallerNumber()));
        assertEquals(PiiCryptoUtils.blindIndex(PHONE), record.getCallerNumberIndex());
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 77L, PHONE);
    }

    @Test
    void insert_alreadyEncryptedNumber_rebuildsWithPlainNumber()
    {
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(PiiCryptoUtils.encrypt(PHONE));
        givenInsertAssignsId(78L);

        assertEquals(1, service.insertAiCallRecord(record));

        assertTrue(PiiCryptoUtils.isEncrypted(record.getCallerNumber()));
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 78L, PHONE);
    }

    @Test
    void insert_emptyNumber_skipsEncryptionAndTokenRebuild()
    {
        AiCallRecord record = new AiCallRecord();
        when(recordMapper.insertAiCallRecord(record)).thenReturn(1);

        assertEquals(1, service.insertAiCallRecord(record));

        assertNull(record.getCallerNumber());
        assertNull(record.getCallerNumberIndex());
        verifyNoInteractions(piiSearchTokenService);
    }

    @Test
    void insert_tokenRebuildFailure_isIsolated()
    {
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(PHONE);
        givenInsertAssignsId(79L);
        doThrow(new RuntimeException("token table down"))
                .when(piiSearchTokenService).rebuild(anyString(), anyLong(), anyString());

        assertEquals(1, service.insertAiCallRecord(record));
    }

    @Test
    void insert_withoutTokenService_stillPersists()
    {
        ReflectionTestUtils.setField(service, "piiSearchTokenService", null);
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(PHONE);
        givenInsertAssignsId(80L);

        assertEquals(1, service.insertAiCallRecord(record));
    }

    @Test
    void update_normalizesNumberAndRebuildsTokens()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(9L);
        record.setCallerNumber(PHONE);
        when(recordMapper.updateAiCallRecord(record)).thenReturn(1);

        assertEquals(1, service.updateAiCallRecord(record));

        assertTrue(PiiCryptoUtils.isEncrypted(record.getCallerNumber()));
        assertEquals(PiiCryptoUtils.blindIndex(PHONE), record.getCallerNumberIndex());
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 9L, PHONE);
    }

    @Test
    void update_withoutNumber_skipsTokenRebuild()
    {
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(9L);
        when(recordMapper.updateAiCallRecord(record)).thenReturn(1);

        assertEquals(1, service.updateAiCallRecord(record));
        verifyNoInteractions(piiSearchTokenService);
    }

    // ---------------- 透传 ----------------

    @Test
    void simpleDelegates_passThrough()
    {
        Long[] ids = {1L, 2L};
        List<AiCallRecord> byAgent = Collections.singletonList(new AiCallRecord());
        Map<String, Object> stats = new HashMap<>();
        List<Map<String, Object>> byAgentStats = Collections.singletonList(new HashMap<>());
        List<Map<String, Object>> byCategory = Collections.singletonList(new HashMap<>());
        List<Map<String, Object>> byDate = Collections.singletonList(new HashMap<>());
        AiCallRecord record = new AiCallRecord();
        when(recordMapper.deleteAiCallRecordByRecordId(1L)).thenReturn(1);
        when(recordMapper.deleteAiCallRecordByRecordIds(ids)).thenReturn(2);
        when(recordMapper.selectAiCallRecordByAgentId(5L)).thenReturn(byAgent);
        when(recordMapper.getCallStatistics()).thenReturn(stats);
        when(recordMapper.getCallStatisticsByAgent()).thenReturn(byAgentStats);
        when(recordMapper.getCallStatisticsByCategory()).thenReturn(byCategory);
        when(recordMapper.getCallStatisticsByDate(7)).thenReturn(byDate);
        when(recordMapper.selectAiCallRecordByCallUuid("uuid-1")).thenReturn(record);
        when(recordMapper.updateRecordingInfo(record)).thenReturn(1);

        assertEquals(1, service.deleteAiCallRecordByRecordId(1L));
        assertEquals(2, service.deleteAiCallRecordByRecordIds(ids));
        assertEquals(byAgent, service.selectAiCallRecordByAgentId(5L));
        assertSame(stats, service.getCallStatistics());
        assertEquals(byAgentStats, service.getCallStatisticsByAgent());
        assertEquals(byCategory, service.getCallStatisticsByCategory());
        assertEquals(byDate, service.getCallStatisticsByDate(7));
        assertSame(record, service.selectAiCallRecordByCallUuid("uuid-1"));
        assertEquals(1, service.updateRecordingInfo(record));
    }

    @Test
    void getWorkbenchSummary_assemblesAllSections()
    {
        Map<String, Object> today = new HashMap<>();
        today.put("count", 3);
        Map<String, Object> total = new HashMap<>();
        total.put("count", 30);
        List<Map<String, Object>> team = Collections.singletonList(new HashMap<>());
        List<AiCallAgentStatus> online = Arrays.asList(new AiCallAgentStatus(), new AiCallAgentStatus());
        List<AiCallRecord> recent = Collections.singletonList(new AiCallRecord());
        when(recordMapper.selectTodayCallStats()).thenReturn(today);
        when(recordMapper.getCallStatistics()).thenReturn(total);
        when(recordMapper.getCallStatisticsByAgent()).thenReturn(team);
        when(agentStatusMapper.selectOnlineAgents()).thenReturn(online);
        when(recordMapper.selectRecentCalls(10)).thenReturn(recent);

        Map<String, Object> result = service.getWorkbenchSummary();

        assertSame(today, result.get("todayCallStats"));
        assertSame(total, result.get("totalStats"));
        assertSame(team, result.get("teamStats"));
        assertEquals(2, result.get("onlineAgentCount"));
        assertSame(recent, result.get("recentCalls"));
    }

    // ---------------- 辅助 ----------------

    private void givenInsertAssignsId(Long recordId)
    {
        doAnswer(invocation -> {
            ((AiCallRecord) invocation.getArgument(0)).setRecordId(recordId);
            return 1;
        }).when(recordMapper).insertAiCallRecord(any());
    }
}
