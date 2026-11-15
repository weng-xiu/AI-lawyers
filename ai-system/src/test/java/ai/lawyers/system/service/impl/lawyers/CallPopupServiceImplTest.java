package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;

/**
 * 来电弹屏服务单测。
 *
 * <p>覆盖号码盲索引查询与读后解密、无档案默认分析字段、频繁来电情绪提示、
 * 历史/工单/轨迹聚合排序，以及更新档案时的写入加密、索引重算与检索 token 重建容错。</p>
 *
 * @author ai-lawyers
 */
class CallPopupServiceImplTest
{
    private static final String CALLER = "13800138000";

    private CallPopupServiceImpl service;

    @Mock private AiCallerProfileMapper aiCallerProfileMapper;
    @Mock private AiCallRecordMapper aiCallRecordMapper;
    @Mock private AiCallTicketMapper aiCallTicketMapper;
    @Mock private IPiiSearchTokenService piiSearchTokenService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new CallPopupServiceImpl();
        ReflectionTestUtils.setField(service, "aiCallerProfileMapper", aiCallerProfileMapper);
        ReflectionTestUtils.setField(service, "aiCallRecordMapper", aiCallRecordMapper);
        ReflectionTestUtils.setField(service, "aiCallTicketMapper", aiCallTicketMapper);
        ReflectionTestUtils.setField(service, "piiSearchTokenService", piiSearchTokenService);
    }

    private static AiCallRecord record(String category, String content, Date time, String status, Integer duration)
    {
        AiCallRecord record = new AiCallRecord();
        record.setCategoryName(category);
        record.setContent(content);
        record.setCallTime(time);
        record.setStatus(status);
        record.setDuration(duration);
        return record;
    }

    private static AiCallTicket ticket(String title, String content, Date time, String status)
    {
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTitle(title);
        ticket.setContent(content);
        ticket.setCreateTime(time);
        ticket.setStatus(status);
        ticket.setTicketNo("T-1");
        return ticket;
    }

    @Test
    void getPopupProfile_noStatsNoProfile_usesDefaults()
    {
        when(aiCallRecordMapper.selectCallerCallStats(anyString())).thenReturn(null);
        when(aiCallerProfileMapper.selectAiCallerProfileByCallerNumberIndex(anyString())).thenReturn(null);

        Map<String, Object> result = service.getPopupProfile(CALLER);

        assertEquals(0L, result.get("callCount"));
        assertEquals(0L, result.get("monthCallCount"));
        assertNull(result.get("lastCallTime"));
        assertNull(result.get("profile"));
        assertEquals("未知", result.get("intentPrediction"));
        assertEquals(0, result.get("intentConfidence"));
        assertEquals("", result.get("consultPreference"));
        assertEquals("", result.get("highFreqProblem"));
        assertEquals(0, result.get("freqMentionCount"));
        assertEquals("0", result.get("riskLevel"));
        assertEquals("平稳", result.get("emotionStatus"));
        assertEquals("", result.get("emotionWarning"));
        assertEquals("", result.get("customerLevel"));
        assertEquals("", result.get("tags"));
    }

    @Test
    void getPopupProfile_frequentCalls_buildsEmotionWarning()
    {
        Map<String, Object> stats = new HashMap<>();
        stats.put("callCount", "12");
        stats.put("monthCallCount", 4);
        Date last = new Date();
        stats.put("lastCallTime", last);
        when(aiCallRecordMapper.selectCallerCallStats(anyString())).thenReturn(stats);
        when(aiCallerProfileMapper.selectAiCallerProfileByCallerNumberIndex(anyString())).thenReturn(null);

        Map<String, Object> result = service.getPopupProfile(CALLER);

        assertEquals(12L, result.get("callCount"));
        assertEquals(4L, result.get("monthCallCount"));
        assertSame(last, result.get("lastCallTime"));
        assertTrue(result.get("emotionWarning").toString().contains("本月4次"));
    }

    @Test
    void getPopupProfile_invalidNumberString_fallsBackZero()
    {
        Map<String, Object> stats = new HashMap<>();
        stats.put("callCount", "abc");
        when(aiCallRecordMapper.selectCallerCallStats(anyString())).thenReturn(stats);
        when(aiCallerProfileMapper.selectAiCallerProfileByCallerNumberIndex(anyString())).thenReturn(null);

        assertEquals(0L, service.getPopupProfile(CALLER).get("callCount"));
    }

    @Test
    void getPopupProfile_profilePresent_decryptsAndKeepsValues()
    {
        AiCallerProfile stored = new AiCallerProfile();
        stored.setCallerNumber(PiiCryptoUtils.encrypt(CALLER));
        stored.setCallerIdCard(PiiCryptoUtils.encrypt("110101199001010011"));
        stored.setIntentPrediction("劳动纠纷");
        stored.setIntentConfidence(90);
        stored.setConsultPreference("电话");
        stored.setHighFreqProblem("欠薪");
        stored.setFreqMentionCount(3);
        stored.setRiskLevel("2");
        stored.setEmotionStatus("激动");
        stored.setEmotionWarning("注意安抚");
        stored.setCustomerLevel("VIP");
        stored.setTags("老客户");
        when(aiCallRecordMapper.selectCallerCallStats(anyString())).thenReturn(null);
        when(aiCallerProfileMapper.selectAiCallerProfileByCallerNumberIndex(anyString())).thenReturn(stored);

        Map<String, Object> result = service.getPopupProfile(CALLER);

        AiCallerProfile profile = (AiCallerProfile) result.get("profile");
        assertEquals(CALLER, profile.getCallerNumber());
        assertEquals("110101199001010011", profile.getCallerIdCard());
        assertEquals("劳动纠纷", result.get("intentPrediction"));
        assertEquals(90, result.get("intentConfidence"));
        assertEquals("电话", result.get("consultPreference"));
        assertEquals("欠薪", result.get("highFreqProblem"));
        assertEquals(3, result.get("freqMentionCount"));
        assertEquals("2", result.get("riskLevel"));
        assertEquals("激动", result.get("emotionStatus"));
        assertEquals("注意安抚", result.get("emotionWarning"));
        assertEquals("VIP", result.get("customerLevel"));
        assertEquals("老客户", result.get("tags"));
    }

    @Test
    void getPopupHistory_usesBlindIndex()
    {
        List<AiCallRecord> list = Arrays.asList(record("劳动", "内容", new Date(), "1", 10));
        when(aiCallRecordMapper.selectAiCallRecordByCallerNumber(
            PiiCryptoUtils.blindIndex(CALLER), 5)).thenReturn(list);

        assertSame(list, service.getPopupHistory(CALLER, 5));
    }

    @Test
    void getPopupTickets_setsCallerNumber()
    {
        List<AiCallTicket> list = Arrays.asList(ticket("标题", "内容", new Date(), "0"));
        when(aiCallTicketMapper.selectAiCallTicketList(any(AiCallTicket.class))).thenReturn(list);

        assertSame(list, service.getPopupTickets(CALLER));

        ArgumentCaptor<AiCallTicket> captor = ArgumentCaptor.forClass(AiCallTicket.class);
        verify(aiCallTicketMapper).selectAiCallTicketList(captor.capture());
        assertEquals(CALLER, captor.getValue().getCallerNumber());
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void getPopupTrack_mergesAndSortsDescendingWithNullLast()
    {
        Date newer = new Date(2000L);
        Date older = new Date(1000L);
        AiCallRecord call = record("劳动", "通话内容", newer, "1", 30);
        AiCallTicket ticket = ticket("工单标题", "工单内容", older, "0");
        AiCallTicket nullTimeTicket = ticket("无时间", "内容", null, "0");
        when(aiCallRecordMapper.selectAiCallRecordByCallerNumber(anyString(), eq(50)))
            .thenReturn((List) Arrays.asList(call, "not-a-record"));
        when(aiCallTicketMapper.selectAiCallTicketList(any(AiCallTicket.class)))
            .thenReturn((List) Arrays.asList(ticket, nullTimeTicket, "not-a-ticket"));

        List<Map<String, Object>> track = service.getPopupTrack(CALLER);

        assertEquals(3, track.size());
        assertEquals("call", track.get(0).get("type"));
        assertEquals("劳动通话", track.get(0).get("title"));
        assertEquals("ticket", track.get(1).get("type"));
        assertEquals("工单标题", track.get(1).get("title"));
        assertEquals("无时间", track.get(2).get("title"));
    }

    @Test
    void getPopupTrack_empty_returnsEmpty()
    {
        when(aiCallRecordMapper.selectAiCallRecordByCallerNumber(anyString(), anyInt()))
            .thenReturn(java.util.Collections.emptyList());
        when(aiCallTicketMapper.selectAiCallTicketList(any(AiCallTicket.class)))
            .thenReturn(java.util.Collections.emptyList());

        assertTrue(service.getPopupTrack(CALLER).isEmpty());
    }

    @Test
    void selectProfile_decryptsPii()
    {
        AiCallerProfile stored = new AiCallerProfile();
        stored.setCallerNumber(PiiCryptoUtils.encrypt(CALLER));
        stored.setCallerIdCard(PiiCryptoUtils.encrypt("110101199001010011"));
        when(aiCallerProfileMapper.selectAiCallerProfileByCallerNumberIndex(anyString())).thenReturn(stored);

        AiCallerProfile result = service.selectAiCallerProfileByCallerNumber(CALLER);

        assertEquals(CALLER, result.getCallerNumber());
        assertEquals("110101199001010011", result.getCallerIdCard());
    }

    @Test
    void updateProfile_withNumberAndIdCard_encryptsAndRebuildsToken()
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(7L);
        profile.setCallerNumber(CALLER);
        profile.setCallerIdCard("110101199001010011");
        when(aiCallerProfileMapper.updateAiCallerProfile(profile)).thenReturn(1);

        assertEquals(1, service.updateAiCallerProfile(profile));

        assertEquals(PiiCryptoUtils.blindIndex(CALLER), profile.getCallerNumberIndex());
        assertEquals(PiiCryptoUtils.blindIndex("110101199001010011"), profile.getCallerIdCardIndex());
        assertEquals(CALLER, PiiCryptoUtils.decrypt(profile.getCallerNumber()));
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALLER_PROFILE, 7L, CALLER);
    }

    @Test
    void updateProfile_idempotentEncrypt_keepsPlainValue()
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setCallerNumber(PiiCryptoUtils.encrypt(CALLER));
        when(aiCallerProfileMapper.updateAiCallerProfile(profile)).thenReturn(1);

        assertEquals(1, service.updateAiCallerProfile(profile));
        assertEquals(CALLER, PiiCryptoUtils.decrypt(profile.getCallerNumber()));
        assertEquals(PiiCryptoUtils.blindIndex(CALLER), profile.getCallerNumberIndex());
    }

    @Test
    void updateProfile_noNumber_skipsTokenRebuild()
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(7L);
        when(aiCallerProfileMapper.updateAiCallerProfile(profile)).thenReturn(1);

        assertEquals(1, service.updateAiCallerProfile(profile));
        verify(piiSearchTokenService, never()).rebuild(anyString(), any(), anyString());
    }

    @Test
    void updateProfile_rowsZero_skipsTokenRebuild()
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(7L);
        profile.setCallerNumber(CALLER);
        when(aiCallerProfileMapper.updateAiCallerProfile(profile)).thenReturn(0);

        assertEquals(0, service.updateAiCallerProfile(profile));
        verify(piiSearchTokenService, never()).rebuild(anyString(), any(), anyString());
    }

    @Test
    void updateProfile_rebuildThrows_doesNotPropagate()
    {
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(7L);
        profile.setCallerNumber(CALLER);
        when(aiCallerProfileMapper.updateAiCallerProfile(profile)).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("token down"))
            .when(piiSearchTokenService).rebuild(anyString(), any(), anyString());

        assertEquals(1, service.updateAiCallerProfile(profile));
    }

    @Test
    void updateProfile_tokenServiceMissing_stillUpdates()
    {
        ReflectionTestUtils.setField(service, "piiSearchTokenService", null);
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(7L);
        profile.setCallerNumber(CALLER);
        when(aiCallerProfileMapper.updateAiCallerProfile(profile)).thenReturn(1);

        assertEquals(1, service.updateAiCallerProfile(profile));
    }

    @Test
    void getPopupProfile_profileBlankFields_usesDefaults()
    {
        AiCallerProfile stored = new AiCallerProfile();
        stored.setCallerNumber(PiiCryptoUtils.encrypt(CALLER));
        when(aiCallRecordMapper.selectCallerCallStats(anyString())).thenReturn(null);
        when(aiCallerProfileMapper.selectAiCallerProfileByCallerNumberIndex(anyString())).thenReturn(stored);

        Map<String, Object> result = service.getPopupProfile(CALLER);

        assertEquals("未知", result.get("intentPrediction"));
        assertEquals(0, result.get("intentConfidence"));
        assertEquals("0", result.get("riskLevel"));
        assertEquals("平稳", result.get("emotionStatus"));
        assertNotNull(result.get("profile"));
    }
}
