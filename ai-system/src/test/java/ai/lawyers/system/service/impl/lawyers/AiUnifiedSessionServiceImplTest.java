package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiUnifiedSessionMapper;
import ai.lawyers.system.service.lawyers.session.ChannelSessionGuard;
import ai.lawyers.system.service.lawyers.session.OccupyResult;

/**
 * 跨渠道统一会话服务单测。
 *
 * <p>覆盖会话登记/结束/心跳的入参校验与默认时间、活跃会话占用与列表、看门狗回收，
 * 以及时间线按档案号与来电号合并去重后按开始时间倒序。</p>
 *
 * @author ai-lawyers
 */
class AiUnifiedSessionServiceImplTest
{
    private AiUnifiedSessionServiceImpl service;

    @Mock private AiUnifiedSessionMapper unifiedSessionMapper;
    @Mock private ChannelSessionGuard sessionGuard;
    @Mock private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiUnifiedSessionServiceImpl();
        ReflectionTestUtils.setField(service, "unifiedSessionMapper", unifiedSessionMapper);
        ReflectionTestUtils.setField(service, "sessionGuard", sessionGuard);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
    }

    private static AiUnifiedSession session(Long sessionId, Long profileId, String caller,
                                            String bizType, String bizId, Date startTime)
    {
        AiUnifiedSession session = new AiUnifiedSession();
        session.setSessionId(sessionId);
        session.setProfileId(profileId);
        session.setCallerNumber(caller);
        session.setBizType(bizType);
        session.setBizId(bizId);
        session.setStartTime(startTime);
        return session;
    }

    @Test
    void recordSession_invalid_returnsZero()
    {
        assertEquals(0, service.recordSession(null));
        assertEquals(0, service.recordSession(session(1L, 1L, "138", "", "1", null)));
        assertEquals(0, service.recordSession(session(1L, 1L, "138", "CALL", "", null)));
        verify(unifiedSessionMapper, never()).insertIgnoreAiUnifiedSession(any());
    }

    @Test
    void recordSession_fillsStartTimeAndInserts()
    {
        AiUnifiedSession session = session(1L, 1L, "138", "CALL", "1", null);
        when(unifiedSessionMapper.insertIgnoreAiUnifiedSession(session)).thenReturn(1);

        assertEquals(1, service.recordSession(session));
        assertNotNull(session.getStartTime());
    }

    @Test
    void finishSession_invalid_returnsZero()
    {
        assertEquals(0, service.finishSession("", "1", new Date()));
        assertEquals(0, service.finishSession("CALL", null, new Date()));
        verify(unifiedSessionMapper, never()).finishSession(anyString(), anyString(), any());
    }

    @Test
    void finishSession_nullEndTime_usesNow()
    {
        when(unifiedSessionMapper.finishSession(eq("CALL"), eq("1"), any(Date.class))).thenReturn(1);

        assertEquals(1, service.finishSession("CALL", "1", null));
    }

    @Test
    void startActive_invalid_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> service.startActive(null, 60));
        assertThrows(IllegalArgumentException.class,
            () -> service.startActive(session(1L, 1L, "138", "", "1", null), 60));
        verify(sessionGuard, never()).tryStart(any(), anyInt());
    }

    @Test
    void startActive_delegatesToGuard()
    {
        AiUnifiedSession session = session(1L, 1L, "138", "CALL", "1", null);
        OccupyResult expected = OccupyResult.acquired(session, true);
        when(sessionGuard.tryStart(session, 60)).thenReturn(expected);

        assertSame(expected, service.startActive(session, 60));
    }

    @Test
    void heartbeat_invalid_skipsMapper()
    {
        service.heartbeat("", "1");
        service.heartbeat("CALL", "");

        verify(unifiedSessionMapper, never()).heartbeat(anyString(), anyString(), any());
    }

    @Test
    void heartbeat_valid_delegates()
    {
        service.heartbeat("CALL", "1");

        verify(unifiedSessionMapper).heartbeat(eq("CALL"), eq("1"), any(Date.class));
    }

    @Test
    void listActive_noCriteria_returnsEmpty()
    {
        assertTrue(service.listActive(null, "").isEmpty());
        verify(sessionGuard, never()).listActive(any(), any());
    }

    @Test
    void listActive_withCriteria_delegates()
    {
        List<AiUnifiedSession> list = Collections.singletonList(new AiUnifiedSession());
        when(sessionGuard.listActive(1L, "138")).thenReturn(list);

        assertSame(list, service.listActive(1L, "138"));
    }

    @Test
    void reclaimExpired_empty_returnsZero()
    {
        when(unifiedSessionMapper.selectExpiredActive(any(Date.class), anyInt()))
            .thenReturn(Collections.emptyList());

        assertEquals(0, service.reclaimExpired());
    }

    @Test
    void reclaimExpired_reclaimsFinishedSessions()
    {
        AiUnifiedSession session = session(1L, 1L, "138", "CALL", "1", new Date());
        session.setActiveOwner("node-1");
        session.setChannelType("VOICE");
        when(unifiedSessionMapper.selectExpiredActive(any(Date.class), anyInt()))
            .thenReturn(Collections.singletonList(session));
        when(unifiedSessionMapper.forceFinish(eq(1L), any(Date.class))).thenReturn(1);

        assertEquals(1, service.reclaimExpired());
        verify(unifiedSessionMapper).forceFinish(eq(1L), any(Date.class));
        verify(stringRedisTemplate).execute(any(), any(), any());
    }

    @Test
    void reclaimExpired_redisThrows_stillReturnsCount()
    {
        AiUnifiedSession session = session(1L, 1L, "138", "CALL", "1", new Date());
        when(unifiedSessionMapper.selectExpiredActive(any(Date.class), anyInt()))
            .thenReturn(Collections.singletonList(session));
        when(unifiedSessionMapper.forceFinish(eq(1L), any(Date.class))).thenReturn(1);
        when(stringRedisTemplate.execute(any(), any(), any())).thenThrow(new IllegalStateException("redis down"));

        assertEquals(1, service.reclaimExpired());
    }

    @Test
    void timeline_mergesByProfileAndCallerAndSortsDesc()
    {
        Date older = new Date(1000L);
        Date newer = new Date(2000L);
        AiUnifiedSession byProfile = session(1L, 1L, "138", "CALL", "1", older);
        AiUnifiedSession duplicate = session(2L, 1L, "139", "CHAT", "2", newer);
        AiUnifiedSession byCaller = session(3L, 2L, "138", "CHAT", "3", null);
        when(unifiedSessionMapper.selectByProfileId(1L)).thenReturn(Arrays.asList(byProfile, duplicate));
        when(unifiedSessionMapper.selectByCallerNumber("138")).thenReturn(Arrays.asList(duplicate, byCaller));

        List<AiUnifiedSession> result = service.timeline(1L, "138");

        assertEquals(3, result.size());
        // 空开始时间经 nullsLast 反转后排在最前，其余按开始时间倒序
        assertSame(byCaller, result.get(0));
        assertSame(duplicate, result.get(1));
        assertSame(byProfile, result.get(2));
    }

    @Test
    void timeline_onlyCaller_queriesCallerOnly()
    {
        List<AiUnifiedSession> list = Collections.singletonList(
            session(1L, null, "138", "CALL", "1", new Date()));
        when(unifiedSessionMapper.selectByCallerNumber("138")).thenReturn(list);

        List<AiUnifiedSession> result = service.timeline(null, "138");

        assertEquals(1, result.size());
        assertSame(list.get(0), result.get(0));
        verify(unifiedSessionMapper, never()).selectByProfileId(any());
    }
}
