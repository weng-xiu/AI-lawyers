package ai.lawyers.system.domain.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * 工作台待办实体测试：属性读写、默认值与字符串输出。
 *
 * @author ai-lawyers
 */
class AiTodoTest
{
    @Test
    void 属性读写()
    {
        Date dueDate = new Date();
        AiTodo todo = new AiTodo();
        todo.setTodoId(1L);
        todo.setTodoTitle("跟进客户回访");
        todo.setTodoContent("联系张先生确认委托意向");
        todo.setStatus("0");
        todo.setPriority("1");
        todo.setDueDate(dueDate);
        todo.setUserId(10L);
        todo.setBizType("ticket");
        todo.setBizId(20L);

        assertEquals(1L, todo.getTodoId());
        assertEquals("跟进客户回访", todo.getTodoTitle());
        assertEquals("联系张先生确认委托意向", todo.getTodoContent());
        assertEquals("0", todo.getStatus());
        assertEquals("1", todo.getPriority());
        assertEquals(dueDate, todo.getDueDate());
        assertEquals(10L, todo.getUserId());
        assertEquals("ticket", todo.getBizType());
        assertEquals(20L, todo.getBizId());
    }

    @Test
    void 默认值为空()
    {
        AiTodo todo = new AiTodo();

        assertNull(todo.getTodoId());
        assertNull(todo.getTodoTitle());
        assertNull(todo.getTodoContent());
        assertNull(todo.getStatus());
        assertNull(todo.getPriority());
        assertNull(todo.getDueDate());
        assertNull(todo.getUserId());
        assertNull(todo.getBizType());
        assertNull(todo.getBizId());
    }

    @Test
    void 字符串输出包含待办标题与关联业务类型()
    {
        AiTodo todo = new AiTodo();
        todo.setTodoTitle("审核合同");
        todo.setBizType("ticket");

        String text = todo.toString();
        assertTrue(text.contains("审核合同"));
        assertTrue(text.contains("ticket"));
    }
}
