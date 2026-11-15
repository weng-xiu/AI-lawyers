package ai.lawyers.system.service.lawyers.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.mapper.lawyers.AiUnifiedSessionMapper;

/**
 * 方案B：{@link ChannelSessionGuard} 跨渠道会话占用守卫测试。
 *
 * <p>覆盖开关关闭零行为、参数校验、Redis 抢占成功/命中既有占用/占用值脏数据回退 DB、
 * Redis 异常降级 DB 唯一键、唯一键冲突兜底与保守放行、占用键口径、
 * 释放（DB 结束 + CAS 删键、开关关闭/身份缺失/DB 异常）、续期与活跃列表合并去重。</p>
 *
 * @author ai-lawyers
 */
class ChannelSessionGuardTest
{
    private static final String KEY = "session:occupy:11:13800000000:wechat";

    private static final String TOKEN = "inbound:s1";

    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOps;

    private AiUnifiedSessionMapper unifiedSessionMapper;

    private ChannelSessionGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp()
    {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        unifiedSessionMapper = mock(AiUnifiedSessionMapper.class);
        guard = new ChannelSessionGuard();
        ReflectionTestUtils.setField(guard, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(guard, "unifiedSessionMapper", unifiedSessionMapper);
        ReflectionTestUtils.setField(guard, "enabled", true);
    }

    // ---------- 占用键口径 ----------

    @Test
    void ownerKeyMatchesDbGeneratedColumn()
    {
        assertThat(ChannelSessionGuard.ownerKey(11L, "13800000000", "wechat")).isEqualTo(KEY);
        assertThat(ChannelSessionGuard.ownerKey(null, null, "wechat")).isEqualTo("session:occupy:N:N:wechat");
        assertThat(ChannelSessionGuard.ownerKey(7L, "", "sms")).isEqualTo("session:occupy:7:N:sms");
        assertThat(ChannelSessionGuard.ownerValue(11L, "13800000000", "wechat")).isEqualTo("11:13800000000:wechat");
        assertThat(ChannelSessionGuard.ownerValue(null, null, "sms")).isEqualTo("N:N:sms");
    }

    // ---------- 抢占 ----------

    @Test
    void disabledSwitchAcquiresWithoutAnySideEffect()
    {
        ReflectionTestUtils.setField(guard, "enabled", false);

        OccupyResult result = guard.tryStart(null, 30);

        assertThat(result.isAcquired()).isTrue();
        assertThat(result.isRedisHealthy()).isTrue();
        verifyNoInteractions(stringRedisTemplate, unifiedSessionMapper);
    }

    @Test
    void rejectsSessionMissingRequiredFields()
    {
        AiUnifiedSession noChannel = session();
        noChannel.setChannelType(null);
        AiUnifiedSession noBiz = session();
        noBiz.setBizId(" ");
        AiUnifiedSession noIdentity = session();
        noIdentity.setProfileId(null);
        noIdentity.setCallerNumber(null);

        assertThatThrownBy(() -> guard.tryStart(noChannel, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channelType/bizType/bizId");
        assertThatThrownBy(() -> guard.tryStart(noBiz, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channelType/bizType/bizId");
        assertThatThrownBy(() -> guard.tryStart(noIdentity, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileId 或 callerNumber");
    }

    @Test
    void acquiresWhenRedisAndDbBothSucceed()
    {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq(TOKEN), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(unifiedSessionMapper.insertActiveSession(any())).thenReturn(1);
        AiUnifiedSession session = session();

        OccupyResult result = guard.tryStart(session, 60);

        assertThat(result.isAcquired()).isTrue();
        assertThat(result.isRedisHealthy()).isTrue();
        assertThat(result.getSession()).isSameAs(session);
        assertThat(session.getActiveFlag()).isEqualTo("1");
        assertThat(session.getStartTime()).isNotNull();
        assertThat(session.getLastHeartbeat()).isNotNull();
        assertThat(session.getExpireTime()).isNotNull();
        verify(unifiedSessionMapper).insertActiveSession(session);
    }

    @Test
    void keepsPresetStartTime()
    {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq(TOKEN), any(Duration.class))).thenReturn(true);
        when(unifiedSessionMapper.insertActiveSession(any())).thenReturn(1);
        Date preset = new Date(1_700_000_000_000L);
        AiUnifiedSession session = session();
        session.setStartTime(preset);

        guard.tryStart(session, 60);

        assertThat(session.getStartTime()).isEqualTo(preset);
    }

    @Test
    void blockedWhenRedisHoldsActiveSession()
    {
        AiUnifiedSession existing = new AiUnifiedSession();
        existing.setSessionId(5L);
        existing.setBizId("s-old");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq(TOKEN), any(Duration.class))).thenReturn(false);
        when(valueOps.get(KEY)).thenReturn("inbound:s-old");
        when(unifiedSessionMapper.selectActiveByOwner("11:13800000000:wechat")).thenReturn(existing);

        OccupyResult result = guard.tryStart(session(), 60);

        assertThat(result.isAcquired()).isFalse();
        assertThat(result.getExistingBizId()).isEqualTo("s-old");
        verify(unifiedSessionMapper, never()).insertActiveSession(any());
    }

    @Test
    void fallsBackToDbWhenRedisTokenUnresolvable()
    {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq(TOKEN), any(Duration.class))).thenReturn(false);
        when(valueOps.get(KEY)).thenReturn("garbage");
        when(unifiedSessionMapper.selectActiveByOwner("11:13800000000:wechat")).thenReturn(null);
        when(unifiedSessionMapper.insertActiveSession(any())).thenReturn(1);

        OccupyResult result = guard.tryStart(session(), 60);

        assertThat(result.isAcquired()).isTrue();
        assertThat(result.isRedisHealthy()).isTrue();
    }

    @Test
    void degradesToDbWhenRedisThrows()
    {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq(TOKEN), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));
        when(unifiedSessionMapper.insertActiveSession(any())).thenReturn(1);

        OccupyResult result = guard.tryStart(session(), 60);

        assertThat(result.isAcquired()).isTrue();
        assertThat(result.isRedisHealthy()).isFalse();
    }

    @Test
    void conservativelyAcquiresWhenInsertAffectsNoRow()
    {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq(TOKEN), any(Duration.class))).thenReturn(true);
        when(unifiedSessionMapper.insertActiveSession(any())).thenReturn(0);

        OccupyResult result = guard.tryStart(session(), 60);

        assertThat(result.isAcquired()).isTrue();
    }

    @Test
    void blockedOnDuplicateKeyWithExistingSession()
    {
        AiUnifiedSession existing = new AiUnifiedSession();
        existing.setSessionId(9L);
        existing.setBizId("s-dup");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq(TOKEN), any(Duration.class))).thenReturn(true);
        when(unifiedSessionMapper.insertActiveSession(any())).thenThrow(new DuplicateKeyException("uk_active_owner"));
        when(unifiedSessionMapper.selectActiveByOwner("11:13800000000:wechat")).thenReturn(existing);

        OccupyResult result = guard.tryStart(session(), 60);

        assertThat(result.isAcquired()).isFalse();
        assertThat(result.getExistingBizId()).isEqualTo("s-dup");
    }

    @Test
    void acquiresOnDuplicateKeyWithoutExistingSession()
    {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq(TOKEN), any(Duration.class))).thenReturn(true);
        when(unifiedSessionMapper.insertActiveSession(any())).thenThrow(new DuplicateKeyException("uk_active_owner"));
        when(unifiedSessionMapper.selectActiveByOwner("11:13800000000:wechat")).thenReturn(null);

        OccupyResult result = guard.tryStart(session(), 60);

        assertThat(result.isAcquired()).isTrue();
    }

    // ---------- 释放 ----------

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void releaseFinishesDbAndDeletesRedisToken()
    {
        guard.release("inbound", "s1", session());

        verify(unifiedSessionMapper).finishSession(eq("inbound"), eq("s1"), any(Date.class));
        verify(stringRedisTemplate).execute(any(RedisScript.class),
                eq(Collections.singletonList(KEY)), eq(TOKEN));
    }

    @Test
    void releaseKeepsDbFinishWhenRedisDisabled()
    {
        ReflectionTestUtils.setField(guard, "enabled", false);

        guard.release("inbound", "s1", session());

        verify(unifiedSessionMapper).finishSession(eq("inbound"), eq("s1"), any(Date.class));
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void releaseSkipsRedisWhenIdentityMissing()
    {
        guard.release("inbound", "s1", null);

        verify(unifiedSessionMapper).finishSession(eq("inbound"), eq("s1"), any(Date.class));
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void releaseSwallowsDbFailureAndStillReleasesRedis()
    {
        when(unifiedSessionMapper.finishSession(anyString(), anyString(), any(Date.class)))
                .thenThrow(new RuntimeException("db down"));

        guard.release("inbound", "s1", session());

        verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any());
    }

    // ---------- 续期 ----------

    @Test
    void renewReturnsTrueWhenDisabled()
    {
        ReflectionTestUtils.setField(guard, "enabled", false);

        assertThat(guard.renew("k", "v", 30)).isTrue();
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void renewReturnsTrueWhenPexpireApplied()
    {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        assertThat(guard.renew(KEY, TOKEN, 30)).isTrue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void renewReturnsFalseWhenTokenMismatch()
    {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        assertThat(guard.renew(KEY, TOKEN, 30)).isFalse();
    }

    @Test
    void renewReturnsFalseOnRedisError()
    {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThat(guard.renew(KEY, TOKEN, 30)).isFalse();
    }

    // ---------- 活跃列表 ----------

    @Test
    void listActiveMergesProfileAndCallerSessions()
    {
        when(unifiedSessionMapper.selectActiveByProfileId(11L))
                .thenReturn(Arrays.asList(active(1L), active(2L)));
        when(unifiedSessionMapper.selectActiveByCallerNumber("13800000000"))
                .thenReturn(Arrays.asList(active(2L), active(3L)));

        List<AiUnifiedSession> merged = guard.listActive(11L, "13800000000");

        assertThat(merged).extracting(AiUnifiedSession::getSessionId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void listActiveSkipsMissingIdentity()
    {
        List<AiUnifiedSession> merged = guard.listActive(null, null);

        assertThat(merged).isEmpty();
        verify(unifiedSessionMapper, never()).selectActiveByProfileId(any());
        verify(unifiedSessionMapper, never()).selectActiveByCallerNumber(any());
    }

    // ---------- 辅助 ----------

    private static AiUnifiedSession session()
    {
        AiUnifiedSession session = new AiUnifiedSession();
        session.setProfileId(11L);
        session.setCallerNumber("13800000000");
        session.setChannelType("wechat");
        session.setBizType("inbound");
        session.setBizId("s1");
        return session;
    }

    private static AiUnifiedSession active(Long sessionId)
    {
        AiUnifiedSession session = new AiUnifiedSession();
        session.setSessionId(sessionId);
        return session;
    }
}
