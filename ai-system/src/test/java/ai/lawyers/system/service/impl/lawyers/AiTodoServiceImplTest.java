package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    // ---------------- F7：工单办结/归档自动关闭关联待办 ----------------

    @Test
    void closeTodosByBiz_delegatesWithGivenOperator()
    {
        when(aiTodoMapper.closeTodosByBiz("ticket", 1001L, "张三")).thenReturn(2);

        assertEquals(2, service.closeTodosByBiz("ticket", 1001L, "张三"));

        verify(aiTodoMapper).closeTodosByBiz("ticket", 1001L, "张三");
    }

    @Test
    void closeTodosByBiz_blankOperatorFallsBackToLoginContext()
    {
        // 单测无登录上下文：不阻断，操作人记为 null
        when(aiTodoMapper.closeTodosByBiz(eq("ticket"), eq(1001L), isNull())).thenReturn(1);

        assertEquals(1, service.closeTodosByBiz(" ticket ", 1001L, null));

        verify(aiTodoMapper).closeTodosByBiz("ticket", 1001L, null);
    }

    @Test
    void closeTodosByBiz_ignoresInvalidArgs()
    {
        assertEquals(0, service.closeTodosByBiz(null, 1001L, "张三"));
        assertEquals(0, service.closeTodosByBiz("  ", 1001L, "张三"));
        assertEquals(0, service.closeTodosByBiz("ticket", null, "张三"));

        verify(aiTodoMapper, never()).closeTodosByBiz(any(), any(), any());
    }

    // ---------------- F7：新增待办的服务端兜底 ----------------

    @Test
    void insertTodo_fillsTicketBizTypeWhenBizIdPresent()
    {
        AiTodo input = new AiTodo();
        input.setTodoTitle("跟进工单");
        input.setBizId(1001L);
        when(aiTodoMapper.insertAiTodo(any())).thenReturn(1);

        assertEquals(1, service.insertAiTodo(input));

        assertEquals("ticket", input.getBizType(), "填了工单ID 应自动补齐 bizType");
        assertEquals("0", input.getStatus(), "默认待办状态");
        assertEquals("2", input.getPriority(), "默认普通优先级");
    }

    @Test
    void insertTodo_keepsExplicitBizTypeAndDefaults()
    {
        AiTodo input = new AiTodo();
        input.setTodoTitle("审查材料");
        input.setBizType("ledger");
        input.setBizId(9L);
        input.setStatus("2");
        input.setPriority("1");
        when(aiTodoMapper.insertAiTodo(any())).thenReturn(1);

        service.insertAiTodo(input);

        assertEquals("ledger", input.getBizType(), "显式 bizType 不应被覆盖");
        assertEquals("2", input.getStatus());
        assertEquals("1", input.getPriority());
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
