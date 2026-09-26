package ai.lawyers.system.service.lawyers.trunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ai.lawyers.system.mapper.lawyers.trunk.AiCallEventDedupMapper;

/**
 * P3-B4：{@link CallEventIdempotencyGuard} 判重语义测试。
 */
class CallEventIdempotencyGuardTest
{
    private AiCallEventDedupMapper mapper;
    private CallEventIdempotencyGuard guard;

    @BeforeEach
    void setUp()
    {
        mapper = mock(AiCallEventDedupMapper.class);
        guard = new CallEventIdempotencyGuard();
        ReflectionTestUtils.setField(guard, "dedupMapper", mapper);
    }

    @Test
    void firstSeen_insertReturns1_true()
    {
        when(mapper.insertIgnore(anyString(), anyString(), anyString(), anyString())).thenReturn(1);

        assertThat(guard.firstSeen("ESL:h1:8021", "uuid-1", "CHANNEL_HANGUP")).isTrue();
        verify(mapper).insertIgnore("ESL:h1:8021|uuid-1|CHANNEL_HANGUP",
                "ESL:h1:8021", "uuid-1", "CHANNEL_HANGUP");
    }

    @Test
    void duplicate_insertReturns0_false()
    {
        when(mapper.insertIgnore(anyString(), anyString(), anyString(), anyString())).thenReturn(0);

        assertThat(guard.firstSeen("ESL:h1:8021", "uuid-1", "CHANNEL_HANGUP")).isFalse();
    }

    @Test
    void dbException_failOpen_true()
    {
        when(mapper.insertIgnore(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        // fail-open：幂等表故障不阻断事件流，由状态机终态兜底
        assertThat(guard.firstSeen("ESL:h1:8021", "uuid-1", "CHANNEL_HANGUP")).isTrue();
    }

    @Test
    void emptyKey_skipsDedup_true()
    {
        assertThat(guard.firstSeen("ESL:h1:8021", null, "CHANNEL_HANGUP")).isTrue();
        assertThat(guard.firstSeen("ESL:h1:8021", "", "CHANNEL_HANGUP")).isTrue();
        assertThat(guard.firstSeen("ESL:h1:8021", "uuid-1", null)).isTrue();
        verifyNoInteractions(mapper);
    }
}
