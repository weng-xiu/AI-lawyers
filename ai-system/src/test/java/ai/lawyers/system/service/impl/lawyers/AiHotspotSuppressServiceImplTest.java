package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppress;
import ai.lawyers.system.domain.lawyers.AiHotspotSuppressLog;
import ai.lawyers.system.mapper.lawyers.AiHotspotSuppressMapper;

/**
 * 高频置底 Service 单测。
 *
 * <p>覆盖保存前归一/校验、规则匹配（入站号码、关键词、外呼号码）、生效期与状态过滤、
 * 频次阈值判定、命中留痕，以及留痕失败时的降级语义。</p>
 *
 * @author ai-lawyers
 */
class AiHotspotSuppressServiceImplTest
{
    private static final Long RULE_ID = 1L;

    private AiHotspotSuppressServiceImpl service;

    @Mock private AiHotspotSuppressMapper suppressMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiHotspotSuppressServiceImpl();
        ReflectionTestUtils.setField(service, "suppressMapper", suppressMapper);
    }

    // ---------------- 保存校验 ----------------

    @Test
    void insertSuppress_fillsDefaultsBeforePersist()
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setRuleName("默认值规则");
        rule.setMatchValue("13800138000");
        when(suppressMapper.insertSuppress(rule)).thenReturn(1);

        assertEquals(1, service.insertSuppress(rule));

        assertEquals("PHONE", rule.getMatchType());
        assertEquals("PRIORITY", rule.getAction());
        assertEquals(-100, rule.getPriorityLevel());
        assertEquals(0, rule.getTriggerCount());
        assertEquals(0, rule.getWindowSeconds());
        assertEquals("0", rule.getStatus());
    }

    @Test
    void insertSuppress_missingRuleName_throws()
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setMatchValue("13800138000");

        assertThrows(ServiceException.class, () -> service.insertSuppress(rule));
        verify(suppressMapper, never()).insertSuppress(any());
    }

    @Test
    void insertSuppress_missingMatchValue_throws()
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setRuleName("无匹配值");

        assertThrows(ServiceException.class, () -> service.insertSuppress(rule));
        verify(suppressMapper, never()).insertSuppress(any());
    }

    @Test
    void insertSuppress_priorityActionWithNonNegativePriority_throws()
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setRuleName("置底规则");
        rule.setMatchValue("13800138000");
        rule.setAction("PRIORITY");
        rule.setPriorityLevel(0);

        assertThrows(ServiceException.class, () -> service.insertSuppress(rule));
        verify(suppressMapper, never()).insertSuppress(any());
    }

    @Test
    void updateSuppress_missingId_throws()
    {
        assertThrows(ServiceException.class, () -> service.updateSuppress(new AiHotspotSuppress()));
        verify(suppressMapper, never()).updateSuppress(any());
    }

    @Test
    void updateSuppress_valid_delegates()
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setSuppressId(RULE_ID);
        rule.setRuleName("更新规则");
        rule.setMatchValue("13800138000");
        rule.setAction("REJECT");
        when(suppressMapper.updateSuppress(rule)).thenReturn(1);

        assertEquals(1, service.updateSuppress(rule));
        assertEquals("REJECT", rule.getAction());
    }

    @Test
    void deleteAndQueries_delegate()
    {
        Long[] ids = {1L, 2L};
        AiHotspotSuppress query = new AiHotspotSuppress();
        List<AiHotspotSuppress> rules = Collections.singletonList(phoneRule("13800138000"));
        List<AiHotspotSuppressLog> logs = Collections.singletonList(new AiHotspotSuppressLog());
        when(suppressMapper.deleteSuppressByIds(ids)).thenReturn(2);
        when(suppressMapper.selectSuppressList(query)).thenReturn(rules);
        when(suppressMapper.selectSuppressById(RULE_ID)).thenReturn(rules.get(0));
        when(suppressMapper.selectLogList(any())).thenReturn(logs);

        assertEquals(2, service.deleteSuppressByIds(ids));
        assertEquals(rules, service.selectSuppressList(query));
        assertSame(rules.get(0), service.selectSuppressById(RULE_ID));
        assertEquals(logs, service.selectLogList(new AiHotspotSuppressLog()));
    }

    // ---------------- 入站匹配 ----------------

    @Test
    void matchInbound_phoneRuleMatches_returnsRuleAndLogs()
    {
        AiHotspotSuppress rule = phoneRule("13800138000");
        givenRules(rule);
        when(suppressMapper.countRecentHits(eq(RULE_ID), any(Date.class))).thenReturn(1);

        AiHotspotSuppress hit = service.matchInbound("13800138000", null, "uuid-1", "4001234567");

        assertSame(rule, hit);
        ArgumentCaptor<AiHotspotSuppressLog> captor = ArgumentCaptor.forClass(AiHotspotSuppressLog.class);
        verify(suppressMapper).insertLog(captor.capture());
        AiHotspotSuppressLog log = captor.getValue();
        assertEquals(RULE_ID, log.getSuppressId());
        assertEquals("INBOUND", log.getDirection());
        assertEquals("PHONE", log.getMatchType());
        assertEquals("13800138000", log.getCallerNumber());
        assertEquals("4001234567", log.getCalleeNumber());
        assertEquals("uuid-1", log.getChannelUuid());
        assertEquals(-100, log.getPriority());
        verify(suppressMapper).incrementHitCount(RULE_ID);
    }

    @Test
    void matchInbound_keywordRuleMatchesContent()
    {
        AiHotspotSuppress rule = keywordRule("退费");
        givenRules(rule);
        when(suppressMapper.countRecentHits(eq(RULE_ID), any(Date.class))).thenReturn(1);

        assertSame(rule, service.matchInbound("13900139000", "我要退费", "uuid-2", "4001234567"));
    }

    @Test
    void matchInbound_nonMatchingNumber_returnsNull()
    {
        givenRules(phoneRule("13800138000"));

        assertNull(service.matchInbound("13900139000", null, "uuid-3", "4001234567"));
        verify(suppressMapper, never()).insertLog(any());
    }

    @Test
    void matchInbound_noRules_returnsNull()
    {
        when(suppressMapper.selectSuppressList(any())).thenReturn(Collections.emptyList());

        assertNull(service.matchInbound("13800138000", "退费", "uuid-4", "4001234567"));
        verify(suppressMapper, never()).insertLog(any());
    }

    @Test
    void matchInbound_disabledRule_ignored()
    {
        AiHotspotSuppress rule = phoneRule("13800138000");
        rule.setStatus("1");
        givenRules(rule);

        assertNull(service.matchInbound("13800138000", null, "uuid-5", "4001234567"));
        verify(suppressMapper, never()).insertLog(any());
    }

    @Test
    void matchInbound_outsideEffectiveWindow_ignored()
    {
        AiHotspotSuppress notStarted = phoneRule("13800138000");
        notStarted.setEffectiveStart(new Date(System.currentTimeMillis() + 3600_000L));
        AiHotspotSuppress expired = phoneRule("13800138000");
        expired.setEffectiveEnd(new Date(System.currentTimeMillis() - 3600_000L));
        when(suppressMapper.selectSuppressList(any())).thenReturn(Arrays.asList(notStarted, expired));

        assertNull(service.matchInbound("13800138000", null, "uuid-6", "4001234567"));
        verify(suppressMapper, never()).insertLog(any());
    }

    @Test
    void matchInbound_frequencyNotReached_returnsNullButLogs()
    {
        AiHotspotSuppress rule = phoneRule("13800138000");
        rule.setTriggerCount(3);
        rule.setWindowSeconds(3600);
        givenRules(rule);
        when(suppressMapper.countRecentHits(eq(RULE_ID), any(Date.class))).thenReturn(2);

        assertNull(service.matchInbound("13800138000", null, "uuid-7", "4001234567"));
        verify(suppressMapper).insertLog(any());
        verify(suppressMapper).incrementHitCount(RULE_ID);
    }

    // ---------------- 外呼匹配 ----------------

    @Test
    void matchOutbound_onlyPhoneRulesAreEvaluated()
    {
        AiHotspotSuppress keyword = keywordRule("退费");
        AiHotspotSuppress phone = phoneRule("13700137000");
        when(suppressMapper.selectSuppressList(any())).thenReturn(Arrays.asList(keyword, phone));
        when(suppressMapper.countRecentHits(eq(RULE_ID), any(Date.class))).thenReturn(1);

        assertSame(phone, service.matchOutbound("13700137000", 7L));

        ArgumentCaptor<AiHotspotSuppressLog> captor = ArgumentCaptor.forClass(AiHotspotSuppressLog.class);
        verify(suppressMapper).insertLog(captor.capture());
        assertEquals("OUTBOUND", captor.getValue().getDirection());
        assertEquals("13700137000", captor.getValue().getCalleeNumber());
        assertNull(captor.getValue().getCallerNumber());
        assertEquals("task-7", captor.getValue().getChannelUuid());
    }

    @Test
    void matchOutbound_nullTaskId_leavesChannelUuidNull()
    {
        AiHotspotSuppress phone = phoneRule("13700137000");
        givenRules(phone);
        when(suppressMapper.countRecentHits(eq(RULE_ID), any(Date.class))).thenReturn(1);

        assertSame(phone, service.matchOutbound("13700137000", null));

        ArgumentCaptor<AiHotspotSuppressLog> captor = ArgumentCaptor.forClass(AiHotspotSuppressLog.class);
        verify(suppressMapper).insertLog(captor.capture());
        assertNull(captor.getValue().getChannelUuid());
    }

    // ---------------- 关键词匹配 ----------------

    @Test
    void matchKeyword_emptyContent_returnsNullWithoutQuery()
    {
        assertNull(service.matchKeyword("", "13800138000", "session-1", "INBOUND"));
        assertNull(service.matchKeyword(null, "13800138000", "session-1", "INBOUND"));
        verifyNoInteractions(suppressMapper);
    }

    @Test
    void matchKeyword_defaultDirectionIsInbound()
    {
        AiHotspotSuppress rule = keywordRule("投诉");
        givenRules(rule);
        when(suppressMapper.countRecentHits(eq(RULE_ID), any(Date.class))).thenReturn(1);

        assertSame(rule, service.matchKeyword("我要投诉", "13800138000", "session-2", ""));

        ArgumentCaptor<AiHotspotSuppressLog> captor = ArgumentCaptor.forClass(AiHotspotSuppressLog.class);
        verify(suppressMapper).insertLog(captor.capture());
        assertEquals("INBOUND", captor.getValue().getDirection());
        assertEquals("13800138000", captor.getValue().getCallerNumber());
        assertEquals("session-2", captor.getValue().getChannelUuid());
    }

    @Test
    void matchKeyword_explicitDirectionPreserved()
    {
        AiHotspotSuppress rule = keywordRule("投诉");
        givenRules(rule);
        when(suppressMapper.countRecentHits(eq(RULE_ID), any(Date.class))).thenReturn(1);

        assertSame(rule, service.matchKeyword("我要投诉", "13800138000", "session-3", "OUTBOUND"));

        ArgumentCaptor<AiHotspotSuppressLog> captor = ArgumentCaptor.forClass(AiHotspotSuppressLog.class);
        verify(suppressMapper).insertLog(captor.capture());
        assertEquals("OUTBOUND", captor.getValue().getDirection());
    }

    // ---------------- 留痕失败降级 ----------------

    @Test
    void matchInbound_logFailureWithoutFrequency_stillReturnsRule()
    {
        AiHotspotSuppress rule = phoneRule("13800138000");
        givenRules(rule);
        when(suppressMapper.insertLog(any())).thenThrow(new RuntimeException("log table down"));

        assertSame(rule, service.matchInbound("13800138000", null, "uuid-8", "4001234567"));
        verify(suppressMapper, never()).countRecentHits(any(), any());
    }

    @Test
    void matchInbound_logFailureWithFrequency_returnsNull()
    {
        AiHotspotSuppress rule = phoneRule("13800138000");
        rule.setTriggerCount(3);
        givenRules(rule);
        when(suppressMapper.insertLog(any())).thenThrow(new RuntimeException("log table down"));

        assertNull(service.matchInbound("13800138000", null, "uuid-9", "4001234567"));
        verify(suppressMapper, never()).countRecentHits(any(), any());
    }

    // ---------------- 辅助 ----------------

    private void givenRules(AiHotspotSuppress... rules)
    {
        when(suppressMapper.selectSuppressList(any())).thenReturn(Arrays.asList(rules));
    }

    private AiHotspotSuppress phoneRule(String value)
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setSuppressId(RULE_ID);
        rule.setRuleName("号码规则");
        rule.setMatchType("PHONE");
        rule.setMatchValue(value);
        rule.setAction("PRIORITY");
        rule.setPriorityLevel(-100);
        rule.setTriggerCount(0);
        rule.setWindowSeconds(0);
        rule.setStatus("0");
        return rule;
    }

    private AiHotspotSuppress keywordRule(String value)
    {
        AiHotspotSuppress rule = new AiHotspotSuppress();
        rule.setSuppressId(RULE_ID);
        rule.setRuleName("关键词规则");
        rule.setMatchType("KEYWORD");
        rule.setMatchValue(value);
        rule.setAction("PRIORITY");
        rule.setPriorityLevel(-100);
        rule.setTriggerCount(0);
        rule.setWindowSeconds(0);
        rule.setStatus("0");
        return rule;
    }
}
