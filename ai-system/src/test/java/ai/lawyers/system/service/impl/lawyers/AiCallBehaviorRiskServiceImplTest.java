package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.risk.AiCallRiskRecord;
import ai.lawyers.system.mapper.lawyers.AiHotspotSuppressMapper;
import ai.lawyers.system.mapper.lawyers.risk.AiCallRiskRecordMapper;

/**
 * P2-15：呼叫行为风控 Service 单测。
 *
 * <p>覆盖扫描链路（开关/空结果/已置底跳过/待复核幂等/阈值过滤/留痕与自动置底）、
 * 复核链路（校验/确认补建置底规则/忽略）、以及号码脱敏与置底失败的异常隔离。</p>
 *
 * @author ai-lawyers
 */
class AiCallBehaviorRiskServiceImplTest
{
    private static final String INDEX = PiiCryptoUtils.blindIndex("13800138000");

    private AiCallBehaviorRiskServiceImpl service;

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AiCallRiskRecordMapper riskMapper;
    @Mock private AiHotspotSuppressMapper suppressMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallBehaviorRiskServiceImpl();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "riskMapper", riskMapper);
        ReflectionTestUtils.setField(service, "suppressMapper", suppressMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "windowHours", 72);
        ReflectionTestUtils.setField(service, "minCalls", 10);
        ReflectionTestUtils.setField(service, "scoreThreshold", 60);
        ReflectionTestUtils.setField(service, "mediumThreshold", 60);
        ReflectionTestUtils.setField(service, "highThreshold", 80);
        ReflectionTestUtils.setField(service, "shortSeconds", 15);
        ReflectionTestUtils.setField(service, "nightStartHour", 22);
        ReflectionTestUtils.setField(service, "nightEndHour", 7);
        ReflectionTestUtils.setField(service, "autoSuppress", "PRIORITY");
        ReflectionTestUtils.setField(service, "suppressTriggerCount", 3);
        ReflectionTestUtils.setField(service, "suppressWindowSeconds", 86400);
    }

    // ---------------- 扫描 ----------------

    @Test
    void scanRisk_disabled_returnsZeroWithoutTouchingDependencies()
    {
        ReflectionTestUtils.setField(service, "enabled", false);

        assertEquals(0, service.scanRisk());
        verifyNoInteractions(jdbcTemplate, riskMapper, suppressMapper);
    }

    @Test
    void scanRisk_noAggregatedRows_returnsZero()
    {
        when(suppressMapper.selectSuppressList(any())).thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        assertEquals(0, service.scanRisk());
        verify(riskMapper, never()).insertRisk(any());
    }

    @Test
    void scanRisk_nullAggregatedRows_returnsZero()
    {
        when(suppressMapper.selectSuppressList(any())).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(null);

        assertEquals(0, service.scanRisk());
        verify(riskMapper, never()).insertRisk(any());
    }

    @Test
    void scanRisk_highRiskNumber_insertsRecordAndAutoSuppresses()
    {
        givenRules(Collections.emptyList());
        givenAggregate(row(INDEX, 30, 30, 30, 30));
        givenStoredNumber("13800138000");
        givenSuppressInsertAssignsId(99L);

        int inserted = service.scanRisk();

        assertEquals(1, inserted);
        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).insertRisk(captor.capture());
        AiCallRiskRecord saved = captor.getValue();
        assertEquals(INDEX, saved.getCallerNumberIndex());
        assertEquals("138****8000", saved.getMaskedNumber());
        assertEquals(72, saved.getWindowHours());
        assertEquals(30, saved.getCallCount());
        assertEquals(100, saved.getRiskScore());
        assertEquals("HIGH", saved.getRiskLevel());
        assertEquals("0", saved.getReviewStatus());
        assertEquals(99L, saved.getSuppressId());
        assertEquals("频次40+超短20+夜间20+未接20", saved.getScoreDetail());

        ArgumentCaptor<AiHotspotSuppress> ruleCaptor = ArgumentCaptor.forClass(AiHotspotSuppress.class);
        verify(suppressMapper).insertSuppress(ruleCaptor.capture());
        AiHotspotSuppress rule = ruleCaptor.getValue();
        assertEquals("PHONE", rule.getMatchType());
        assertEquals("13800138000", rule.getMatchValue());
        assertEquals("PRIORITY", rule.getAction());
        assertEquals(-100, rule.getPriorityLevel());
        assertEquals(3, rule.getTriggerCount());
        assertEquals(86400, rule.getWindowSeconds());
        assertEquals("0", rule.getStatus());
        assertEquals("risk-scan", rule.getCreateBy());
    }

    @Test
    void scanRisk_autoSuppressOff_onlyLeavesRecord()
    {
        ReflectionTestUtils.setField(service, "autoSuppress", "OFF");
        givenRules(Collections.emptyList());
        givenAggregate(row(INDEX, 30, 30, 30, 30));
        givenStoredNumber("13800138000");

        assertEquals(1, service.scanRisk());

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).insertRisk(captor.capture());
        assertNull(captor.getValue().getSuppressId());
        verify(suppressMapper, never()).insertSuppress(any());
    }

    @Test
    void scanRisk_belowThreshold_skipsInsert()
    {
        givenRules(Collections.emptyList());
        givenAggregate(row(INDEX, 10, 0, 0, 0));

        assertEquals(0, service.scanRisk());
        verify(riskMapper, never()).insertRisk(any());
        verify(suppressMapper, never()).insertSuppress(any());
    }

    @Test
    void scanRisk_alreadySuppressed_skipsInsert()
    {
        AiHotspotSuppress existing = new AiHotspotSuppress();
        existing.setMatchType("PHONE");
        existing.setMatchValue("13800138000");
        existing.setStatus("0");
        givenRules(Collections.singletonList(existing));
        givenAggregate(row(INDEX, 30, 30, 30, 30));

        assertEquals(0, service.scanRisk());
        verify(riskMapper, never()).selectLatestByIndex(anyString(), anyString());
        verify(riskMapper, never()).insertRisk(any());
    }

    @Test
    void scanRisk_pendingReviewExists_skipsInsert()
    {
        givenRules(Collections.emptyList());
        givenAggregate(row(INDEX, 30, 30, 30, 30));
        when(riskMapper.selectLatestByIndex(INDEX, "0")).thenReturn(new AiCallRiskRecord());

        assertEquals(0, service.scanRisk());
        verify(riskMapper, never()).insertRisk(any());
    }

    @Test
    void scanRisk_suppressRuleCreationFails_recordStillInserted()
    {
        givenRules(Collections.emptyList());
        givenAggregate(row(INDEX, 30, 30, 30, 30));
        givenStoredNumber("13800138000");
        when(suppressMapper.insertSuppress(any())).thenThrow(new RuntimeException("uk_match"));

        assertEquals(1, service.scanRisk());

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).insertRisk(captor.capture());
        assertNull(captor.getValue().getSuppressId());
    }

    @Test
    void scanRisk_plainNumberMissing_skipsSuppressButKeepsRecord()
    {
        givenRules(Collections.emptyList());
        givenAggregate(row(INDEX, 30, 30, 30, 30));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        assertEquals(1, service.scanRisk());

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).insertRisk(captor.capture());
        assertNull(captor.getValue().getMaskedNumber());
        assertNull(captor.getValue().getSuppressId());
        verify(suppressMapper, never()).insertSuppress(any());
    }

    @Test
    void scanRisk_numberLookupThrows_maskedNumberNullButRecordInserted()
    {
        ReflectionTestUtils.setField(service, "autoSuppress", "OFF");
        givenRules(Collections.emptyList());
        givenAggregate(row(INDEX, 30, 30, 30, 30));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        assertEquals(1, service.scanRisk());

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).insertRisk(captor.capture());
        assertNull(captor.getValue().getMaskedNumber());
    }

    @Test
    void scanRisk_encryptedStoredNumber_isDecryptedThenMasked()
    {
        ReflectionTestUtils.setField(service, "autoSuppress", "OFF");
        givenRules(Collections.emptyList());
        givenAggregate(row(INDEX, 30, 30, 30, 30));
        givenStoredNumber(PiiCryptoUtils.encrypt("13800138000"));

        assertEquals(1, service.scanRisk());

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).insertRisk(captor.capture());
        assertEquals("138****8000", captor.getValue().getMaskedNumber());
    }

    // ---------------- 复核 ----------------

    @Test
    void reviewRisk_nullReview_throws()
    {
        assertThrows(ServiceException.class, () -> service.reviewRisk(null));
    }

    @Test
    void reviewRisk_missingRiskId_throws()
    {
        assertThrows(ServiceException.class, () -> service.reviewRisk(new AiCallRiskRecord()));
    }

    @Test
    void reviewRisk_recordNotFound_throws()
    {
        AiCallRiskRecord review = reviewOf(5L, "1");
        when(riskMapper.selectRiskById(5L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.reviewRisk(review));
    }

    @Test
    void reviewRisk_invalidStatus_throws()
    {
        AiCallRiskRecord review = reviewOf(5L, "0");
        when(riskMapper.selectRiskById(5L)).thenReturn(pendingRecord(5L));

        assertThrows(ServiceException.class, () -> service.reviewRisk(review));
        verify(riskMapper, never()).updateReview(any());
    }

    @Test
    void reviewRisk_ignore_updatesWithoutCreatingRule()
    {
        AiCallRiskRecord review = reviewOf(5L, "2");
        review.setReviewBy("leader");
        review.setReviewRemark("误报");
        when(riskMapper.selectRiskById(5L)).thenReturn(pendingRecord(5L));
        when(riskMapper.updateReview(any())).thenReturn(1);

        assertEquals(1, service.reviewRisk(review));

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).updateReview(captor.capture());
        assertEquals("2", captor.getValue().getReviewStatus());
        assertEquals("leader", captor.getValue().getReviewBy());
        assertEquals("误报", captor.getValue().getReviewRemark());
        verify(suppressMapper, never()).insertSuppress(any());
    }

    @Test
    void reviewRisk_confirmWithoutSuppressId_createsRule()
    {
        AiCallRiskRecord record = pendingRecord(5L);
        record.setMaskedNumber("138****8000");
        AiCallRiskRecord review = reviewOf(5L, "1");
        when(riskMapper.selectRiskById(5L)).thenReturn(record);
        givenStoredNumber("13800138000");
        givenSuppressInsertAssignsId(88L);
        when(riskMapper.updateReview(any())).thenReturn(1);

        assertEquals(1, service.reviewRisk(review));

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).updateReview(captor.capture());
        assertEquals("1", captor.getValue().getReviewStatus());
        assertEquals(88L, captor.getValue().getSuppressId());
        verify(suppressMapper).insertSuppress(any());
    }

    @Test
    void reviewRisk_confirmWithExistingSuppressId_doesNotCreateAgain()
    {
        AiCallRiskRecord record = pendingRecord(5L);
        record.setSuppressId(66L);
        AiCallRiskRecord review = reviewOf(5L, "1");
        when(riskMapper.selectRiskById(5L)).thenReturn(record);
        when(riskMapper.updateReview(any())).thenReturn(1);

        assertEquals(1, service.reviewRisk(review));

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).updateReview(captor.capture());
        assertEquals(66L, captor.getValue().getSuppressId());
        verify(suppressMapper, never()).insertSuppress(any());
    }

    @Test
    void reviewRisk_confirmWithoutPlainNumber_stillUpdates()
    {
        AiCallRiskRecord record = pendingRecord(5L);
        AiCallRiskRecord review = reviewOf(5L, "1");
        when(riskMapper.selectRiskById(5L)).thenReturn(record);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());
        when(riskMapper.updateReview(any())).thenReturn(1);

        assertEquals(1, service.reviewRisk(review));

        ArgumentCaptor<AiCallRiskRecord> captor = ArgumentCaptor.forClass(AiCallRiskRecord.class);
        verify(riskMapper).updateReview(captor.capture());
        assertNull(captor.getValue().getSuppressId());
    }

    @Test
    void selectRiskList_delegatesToMapper()
    {
        AiCallRiskRecord query = new AiCallRiskRecord();
        List<AiCallRiskRecord> expected = Collections.singletonList(pendingRecord(1L));
        when(riskMapper.selectRiskList(query)).thenReturn(expected);

        assertEquals(expected, service.selectRiskList(query));
    }

    // ---------------- 工具方法 ----------------

    @Test
    void intOf_handlesNullNumberStringAndInvalid()
    {
        assertEquals(0, (Integer) ReflectionTestUtils.invokeMethod(service, "intOf", (Object) null));
        assertEquals(5, (Integer) ReflectionTestUtils.invokeMethod(service, "intOf", (Object) 5));
        assertEquals(7, (Integer) ReflectionTestUtils.invokeMethod(service, "intOf", (Object) "7"));
        assertEquals(0, (Integer) ReflectionTestUtils.invokeMethod(service, "intOf", (Object) "abc"));
    }

    private void givenRules(List<AiHotspotSuppress> rules)
    {
        when(suppressMapper.selectSuppressList(any())).thenReturn(rules);
    }

    private void givenAggregate(Map<String, Object> row)
    {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Collections.singletonList(row));
    }

    private void givenStoredNumber(String stored)
    {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(stored)));
    }

    private void givenSuppressInsertAssignsId(Long suppressId)
    {
        doAnswer(invocation -> {
            AiHotspotSuppress rule = invocation.getArgument(0);
            rule.setSuppressId(suppressId);
            return 1;
        }).when(suppressMapper).insertSuppress(any());
    }

    private Map<String, Object> row(String index, int callCount, int shortCount, int nightCount, int missedCount)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("caller_number_index", index);
        row.put("call_count", callCount);
        row.put("short_count", shortCount);
        row.put("night_count", nightCount);
        row.put("missed_count", missedCount);
        return row;
    }

    private AiCallRiskRecord pendingRecord(Long riskId)
    {
        AiCallRiskRecord record = new AiCallRiskRecord();
        record.setRiskId(riskId);
        record.setCallerNumberIndex(INDEX);
        record.setRiskScore(100);
        record.setReviewStatus("0");
        return record;
    }

    private AiCallRiskRecord reviewOf(Long riskId, String status)
    {
        AiCallRiskRecord review = new AiCallRiskRecord();
        review.setRiskId(riskId);
        review.setReviewStatus(status);
        return review;
    }
}
