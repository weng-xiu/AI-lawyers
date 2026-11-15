package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiChatMessage;
import ai.lawyers.system.mapper.lawyers.AiChatMessageMapper;

/**
 * 聊天消息服务单测。
 *
 * <p>覆盖消息查询、新增、修改、删除与按会话标记已读。</p>
 *
 * @author ai-lawyers
 */
class AiChatMessageServiceImplTest
{
    private AiChatMessageServiceImpl service;

    @Mock private AiChatMessageMapper aiChatMessageMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiChatMessageServiceImpl();
        ReflectionTestUtils.setField(service, "aiChatMessageMapper", aiChatMessageMapper);
    }

    @Test
    void selectAiChatMessageByMessageId_delegates()
    {
        AiChatMessage expected = new AiChatMessage();
        when(aiChatMessageMapper.selectAiChatMessageByMessageId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiChatMessageByMessageId(1L));
    }

    @Test
    void selectAiChatMessageList_delegates()
    {
        AiChatMessage query = new AiChatMessage();
        List<AiChatMessage> list = Collections.singletonList(new AiChatMessage());
        when(aiChatMessageMapper.selectAiChatMessageList(query)).thenReturn(list);

        assertSame(list, service.selectAiChatMessageList(query));
    }

    @Test
    void insertAiChatMessage_delegates()
    {
        AiChatMessage message = new AiChatMessage();
        when(aiChatMessageMapper.insertAiChatMessage(message)).thenReturn(1);

        assertEquals(1, service.insertAiChatMessage(message));
    }

    @Test
    void updateAiChatMessage_delegates()
    {
        AiChatMessage message = new AiChatMessage();
        when(aiChatMessageMapper.updateAiChatMessage(message)).thenReturn(1);

        assertEquals(1, service.updateAiChatMessage(message));
    }

    @Test
    void deleteAiChatMessageByMessageId_delegates()
    {
        when(aiChatMessageMapper.deleteAiChatMessageByMessageId(1L)).thenReturn(1);

        assertEquals(1, service.deleteAiChatMessageByMessageId(1L));
    }

    @Test
    void deleteAiChatMessageByMessageIds_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiChatMessageMapper.deleteAiChatMessageByMessageIds(ids)).thenReturn(2);

        assertEquals(2, service.deleteAiChatMessageByMessageIds(ids));
    }

    @Test
    void markReadBySessionId_delegates()
    {
        when(aiChatMessageMapper.markReadBySessionId(9L)).thenReturn(3);

        assertEquals(3, service.markReadBySessionId(9L));
    }
}
