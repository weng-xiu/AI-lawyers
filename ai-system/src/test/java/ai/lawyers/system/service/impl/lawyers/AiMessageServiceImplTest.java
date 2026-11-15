package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiMessage;
import ai.lawyers.system.mapper.lawyers.AiMessageMapper;

/**
 * 站内信服务单测。
 *
 * <p>覆盖消息查询、新增默认值补全、已读标记、未读数统计与按用户删除的入参校验。</p>
 *
 * @author ai-lawyers
 */
class AiMessageServiceImplTest
{
    private AiMessageServiceImpl service;

    @Mock private AiMessageMapper aiMessageMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiMessageServiceImpl();
        ReflectionTestUtils.setField(service, "aiMessageMapper", aiMessageMapper);
    }

    @Test
    void selectAiMessageByMessageId_delegates()
    {
        AiMessage expected = new AiMessage();
        when(aiMessageMapper.selectAiMessageByMessageId(1L)).thenReturn(expected);

        assertSame(expected, service.selectAiMessageByMessageId(1L));
    }

    @Test
    void selectAiMessageList_delegates()
    {
        AiMessage query = new AiMessage();
        List<AiMessage> list = Collections.singletonList(new AiMessage());
        when(aiMessageMapper.selectAiMessageList(query)).thenReturn(list);

        assertSame(list, service.selectAiMessageList(query));
    }

    @Test
    void insertAiMessage_fillsDefaults()
    {
        AiMessage message = new AiMessage();
        when(aiMessageMapper.insertAiMessage(message)).thenReturn(1);

        assertEquals(1, service.insertAiMessage(message));
        assertEquals("0", message.getIsRead());
        assertEquals("9", message.getMsgType());
        assertEquals("2", message.getPriority());
        assertEquals("system", message.getSender());
    }

    @Test
    void insertAiMessage_keepsExplicitValues()
    {
        AiMessage message = new AiMessage();
        message.setIsRead("1");
        message.setMsgType("1");
        message.setPriority("1");
        message.setSender("admin");
        when(aiMessageMapper.insertAiMessage(message)).thenReturn(1);

        assertEquals(1, service.insertAiMessage(message));
        assertEquals("1", message.getIsRead());
        assertEquals("1", message.getMsgType());
        assertEquals("1", message.getPriority());
        assertEquals("admin", message.getSender());
    }

    @Test
    void markRead_delegates()
    {
        when(aiMessageMapper.markRead(1L, 2L)).thenReturn(1);

        assertEquals(1, service.markRead(1L, 2L));
    }

    @Test
    void markAllRead_delegates()
    {
        when(aiMessageMapper.markAllRead(2L)).thenReturn(3);

        assertEquals(3, service.markAllRead(2L));
    }

    @Test
    void countUnread_nullUser_returnsZeroWithoutMapper()
    {
        assertEquals(0, service.countUnread(null));
        verify(aiMessageMapper, never()).countUnread(anyLong());
    }

    @Test
    void countUnread_user_delegates()
    {
        when(aiMessageMapper.countUnread(2L)).thenReturn(4);

        assertEquals(4, service.countUnread(2L));
    }

    @Test
    void deleteAiMessageByMessageIds_invalidInputs_returnsZero()
    {
        assertEquals(0, service.deleteAiMessageByMessageIds(null, 1L));
        assertEquals(0, service.deleteAiMessageByMessageIds(new Long[0], 1L));
        assertEquals(0, service.deleteAiMessageByMessageIds(new Long[] { 1L }, null));
        verify(aiMessageMapper, never()).deleteAiMessageByMessageIds(any(), any());
    }

    @Test
    void deleteAiMessageByMessageIds_valid_delegates()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(aiMessageMapper.deleteAiMessageByMessageIds(ids, 3L)).thenReturn(2);

        assertEquals(2, service.deleteAiMessageByMessageIds(ids, 3L));
    }
}
