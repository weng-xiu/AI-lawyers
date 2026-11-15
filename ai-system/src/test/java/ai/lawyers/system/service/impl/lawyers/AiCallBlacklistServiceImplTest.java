package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import ai.lawyers.system.domain.lawyers.AiCallBlacklist;
import ai.lawyers.system.mapper.lawyers.AiCallBlacklistMapper;

/**
 * 通话黑白名单 Service 单测。
 *
 * <p>覆盖新增默认值、更新/删除透传、名单命中判定（类型/状态/生效期）、
 * 退订名单的幂等恢复与默认原因。</p>
 *
 * @author ai-lawyers
 */
class AiCallBlacklistServiceImplTest
{
    private static final String PHONE = "13800138000";

    private AiCallBlacklistServiceImpl service;

    @Mock private AiCallBlacklistMapper blacklistMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiCallBlacklistServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "blacklistMapper", blacklistMapper);
    }

    // ---------------- CRUD ----------------

    @Test
    void insert_fillsDefaultsAndCreateTime()
    {
        AiCallBlacklist entity = new AiCallBlacklist();
        entity.setPhoneNumber(PHONE);
        when(blacklistMapper.insert(entity)).thenReturn(1);

        assertEquals(1, service.insert(entity));

        assertEquals(1, entity.getStatus());
        assertEquals(1, entity.getListType());
        assertNotNull(entity.getCreateTime());
    }

    @Test
    void insert_keepsProvidedListTypeAndStatus()
    {
        AiCallBlacklist entity = new AiCallBlacklist();
        entity.setPhoneNumber(PHONE);
        entity.setListType(2);
        entity.setStatus(0);
        when(blacklistMapper.insert(entity)).thenReturn(1);

        service.insert(entity);

        assertEquals(2, entity.getListType());
        assertEquals(0, entity.getStatus());
    }

    @Test
    void update_setsUpdateTimeAndDelegates()
    {
        AiCallBlacklist entity = new AiCallBlacklist();
        entity.setId(5L);
        when(blacklistMapper.update(entity)).thenReturn(1);

        assertEquals(1, service.update(entity));
        assertNotNull(entity.getUpdateTime());
    }

    @Test
    void queriesAndDelete_delegate()
    {
        Long[] ids = {1L, 2L};
        AiCallBlacklist entity = new AiCallBlacklist();
        List<AiCallBlacklist> list = Collections.singletonList(entity);
        when(blacklistMapper.selectList(entity)).thenReturn(list);
        when(blacklistMapper.selectById(1L)).thenReturn(entity);
        when(blacklistMapper.deleteByIds(ids)).thenReturn(2);

        assertEquals(list, service.selectList(entity));
        assertSame(entity, service.selectById(1L));
        assertEquals(2, service.deleteByIds(ids));
    }

    // ---------------- 命中判定 ----------------

    @Test
    void isBlacklisted_emptyPhone_falseWithoutQuery()
    {
        assertFalse(service.isBlacklisted(""));
        assertFalse(service.isBlacklisted(null));
        verifyNoInteractions(blacklistMapper);
    }

    @Test
    void isBlacklisted_nullOrEmptyRecords_false()
    {
        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(null);
        assertFalse(service.isBlacklisted(PHONE));

        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(Collections.emptyList());
        assertFalse(service.isBlacklisted(PHONE));
    }

    @Test
    void isBlacklisted_enabledEffectiveRecord_true()
    {
        when(blacklistMapper.selectByPhone(PHONE))
                .thenReturn(Collections.singletonList(record(1, 1, 1, null, null)));

        assertTrue(service.isBlacklisted(PHONE));
    }

    @Test
    void isBlacklisted_skipsNullAndMismatchedRecords()
    {
        AiCallBlacklist white = record(2, 1, 1, null, null);
        AiCallBlacklist disabled = record(1, 0, 1, null, null);
        AiCallBlacklist noType = record(null, 1, 1, null, null);
        AiCallBlacklist noStatus = record(1, null, 1, null, null);
        List<AiCallBlacklist> records = Arrays.asList(null, white, disabled, noType, noStatus);
        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(records);

        assertFalse(service.isBlacklisted(PHONE));
    }

    @Test
    void isBlacklisted_outsideEffectiveWindow_false()
    {
        Date now = new Date();
        AiCallBlacklist notStarted = record(1, 1, 1, new Date(now.getTime() + 3600_000L), null);
        AiCallBlacklist expired = record(1, 1, 1, null, new Date(now.getTime() - 3600_000L));
        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(Arrays.asList(notStarted, expired));

        assertFalse(service.isBlacklisted(PHONE));
    }

    @Test
    void isWhitelisted_onlyWhiteTypeMatches()
    {
        when(blacklistMapper.selectByPhone(PHONE))
                .thenReturn(Collections.singletonList(record(2, 1, 1, null, null)));

        assertTrue(service.isWhitelisted(PHONE));
        assertFalse(service.isBlacklisted(PHONE));
    }

    // ---------------- 退订名单 ----------------

    @Test
    void addUnsubscribe_emptyPhone_falseWithoutQuery()
    {
        assertFalse(service.addUnsubscribe("", "T"));
        verifyNoInteractions(blacklistMapper);
    }

    @Test
    void addUnsubscribe_newRecord_insertsWithDefaults()
    {
        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(Collections.emptyList());
        when(blacklistMapper.insert(any())).thenReturn(1);

        assertTrue(service.addUnsubscribe(PHONE, ""));

        ArgumentCaptor<AiCallBlacklist> captor = ArgumentCaptor.forClass(AiCallBlacklist.class);
        verify(blacklistMapper).insert(captor.capture());
        AiCallBlacklist saved = captor.getValue();
        assertEquals(PHONE, saved.getPhoneNumber());
        assertEquals(3, saved.getListType());
        assertEquals(1, saved.getStatus());
        assertEquals("短信回复T退订", saved.getReason());
        assertEquals("SMS_UPSTREAM", saved.getCreateBy());
    }

    @Test
    void addUnsubscribe_newRecord_usesCustomReason()
    {
        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(Collections.emptyList());
        when(blacklistMapper.insert(any())).thenReturn(1);

        assertTrue(service.addUnsubscribe(PHONE, "用户来电要求退订"));

        ArgumentCaptor<AiCallBlacklist> captor = ArgumentCaptor.forClass(AiCallBlacklist.class);
        verify(blacklistMapper).insert(captor.capture());
        assertEquals("用户来电要求退订", captor.getValue().getReason());
    }

    @Test
    void addUnsubscribe_insertRejected_false()
    {
        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(Collections.emptyList());
        when(blacklistMapper.insert(any())).thenReturn(0);

        assertFalse(service.addUnsubscribe(PHONE, "退订"));
    }

    @Test
    void addUnsubscribe_existingRecord_reactivatesIdempotently()
    {
        AiCallBlacklist exist = record(3, 0, 9L, null, null);
        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(Collections.singletonList(exist));
        when(blacklistMapper.updateUnsubscribeActive(any())).thenReturn(1);

        assertTrue(service.addUnsubscribe(PHONE, null));

        ArgumentCaptor<AiCallBlacklist> captor = ArgumentCaptor.forClass(AiCallBlacklist.class);
        verify(blacklistMapper).updateUnsubscribeActive(captor.capture());
        assertEquals(9L, captor.getValue().getId());
        assertEquals("短信回复T退订", captor.getValue().getReason());
        assertEquals("SMS_UPSTREAM", captor.getValue().getUpdateBy());
        verify(blacklistMapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void addUnsubscribe_existingRecordUpdateRejected_false()
    {
        AiCallBlacklist exist = record(3, 0, 9L, null, null);
        when(blacklistMapper.selectByPhone(PHONE)).thenReturn(Arrays.asList(null, exist));
        when(blacklistMapper.updateUnsubscribeActive(any())).thenReturn(0);

        assertFalse(service.addUnsubscribe(PHONE, "退订"));
    }

    // ---------------- 辅助 ----------------

    private AiCallBlacklist record(Integer listType, Integer status, long id, Date start, Date end)
    {
        AiCallBlacklist record = new AiCallBlacklist();
        record.setId(id);
        record.setPhoneNumber(PHONE);
        record.setListType(listType);
        record.setStatus(status);
        record.setEffectiveStart(start);
        record.setEffectiveEnd(end);
        return record;
    }
}
