package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiChatSession;
import ai.lawyers.system.mapper.lawyers.AiChatSessionMapper;

/**
 * 图文会话服务单测。
 *
 * <p>覆盖会话查询、新增、修改、删除与统计委托。</p>
 *
 * @author ai-lawyers
 */
class AiChatSessionServiceImplTest
{
    private AiChatSessionServiceImpl service;

    @Mock private AiChatSessionMapper aiChatSessionMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiChatSessionServiceImpl();
        ReflectionTestUtils.setField(service, "aiChatSessionMapper", aiChatSessionMapper);
    }

    @Test
    void selectAiChatSessionBySessionId_delegates()
    {
        AiChatSession expected = new AiChatSession();
        when(aiChatSessionMapper.selectAiChatSessionBySessionId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiChatSessionBySessionId(1L));
    }

    @Test
    void selectAiChatSessionList_delegates()
    {
        AiChatSession query = new AiChatSession();
        List<AiChatSession> list = Collections.singletonList(new AiChatSession());
        when(aiChatSessionMapper.selectAiChatSessionList(query)).thenReturn(list);

        assertSame(list, service.selectAiChatSessionList(query));
    }

    @Test
    void insertAiChatSession_delegates()
    {
        AiChatSession session = new AiChatSession();
        when(aiChatSessionMapper.insertAiChatSession(session)).thenReturn(1);

        assertEquals(1, service.insertAiChatSession(session));
    }

    @Test
    void updateAiChatSession_delegates()
    {
        AiChatSession session = new AiChatSession();
        when(aiChatSessionMapper.updateAiChatSession(session)).thenReturn(1);

        assertEquals(1, service.updateAiChatSession(session));
    }

    @Test
    void deleteAiChatSessionBySessionId_delegates()
    {
        when(aiChatSessionMapper.deleteAiChatSessionBySessionId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiChatSessionBySessionId(1L));
    }

    @Test
    void deleteAiChatSessionBySessionIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiChatSessionMapper.deleteAiChatSessionBySessionIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiChatSessionBySessionIds(ids));
    }

    @Test
    void selectChatSessionStats_delegates()
    {
        Map<String, Object> stats = new HashMap<>();
        when(aiChatSessionMapper.selectChatSessionStats()).thenReturn(stats);

        assertSame(stats, service.selectChatSessionStats());
    }
}
