package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiTodoMapper;

/**
 * F7 待办-工单关联单测：仅 bizType=ticket 且 bizId 非空时回查工单，工单不存在返回 null。
 */
class AiTodoServiceImplTest
{
    @Mock
    private AiTodoMapper aiTodoMapper;

    @Mock
    private AiCallTicketMapper aiCallTicketMapper;

    private AiTodoServiceImpl service;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AiTodoServiceImpl();
        ReflectionTestUtils.setField(service, "aiTodoMapper", aiTodoMapper);
        ReflectionTestUtils.setField(service, "aiCallTicketMapper", aiCallTicketMapper);
    }

    @Test
    void linkedTicketReturnedWhenBizIsTicket()
    {
        AiTodo todo = todo("ticket", 1001L);
        AiCallTicket ticket = new AiCallTicket();
        ticket.setTicketId(1001L);
        when(aiTodoMapper.selectAiTodoByTodoId(5L)).thenReturn(todo);
        when(aiCallTicketMapper.selectAiCallTicketByTicketId(1001L)).thenReturn(ticket);

        assertEquals(ticket, service.selectLinkedTicket(5L));
    }

    @Test
    void noLinkReturnsNullWithoutTicketQuery()
    {
        when(aiTodoMapper.selectAiTodoByTodoId(5L)).thenReturn(todo(null, null));

        assertNull(service.selectLinkedTicket(5L));
        verify(aiCallTicketMapper, never()).selectAiCallTicketByTicketId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void nonTicketBizTypeReturnsNull()
    {
        when(aiTodoMapper.selectAiTodoByTodoId(5L)).thenReturn(todo("ledger", 9L));

        assertNull(service.selectLinkedTicket(5L));
        verify(aiCallTicketMapper, never()).selectAiCallTicketByTicketId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void missingTodoOrTodoIdReturnsNull()
    {
        assertNull(service.selectLinkedTicket(null));
        when(aiTodoMapper.selectAiTodoByTodoId(6L)).thenReturn(null);
        assertNull(service.selectLinkedTicket(6L));
    }

    @Test
    void deletedTicketReturnsNull()
    {
        when(aiTodoMapper.selectAiTodoByTodoId(5L)).thenReturn(todo("ticket", 1001L));
        when(aiCallTicketMapper.selectAiCallTicketByTicketId(1001L)).thenReturn(null);

        assertNull(service.selectLinkedTicket(5L));
    }

    private static AiTodo todo(String bizType, Long bizId)
    {
        AiTodo todo = new AiTodo();
        todo.setTodoId(5L);
        todo.setBizType(bizType);
        todo.setBizId(bizId);
        return todo;
    }
}
