package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.mapper.lawyers.AiCallTicketMapper;
import ai.lawyers.system.mapper.lawyers.AiTodoMapper;
import ai.lawyers.system.service.lawyers.IAiTodoService;

@Service
public class AiTodoServiceImpl implements IAiTodoService
{
    /** F7：待办关联业务类型——工单 */
    private static final String BIZ_TYPE_TICKET = "ticket";

    @Autowired
    private AiTodoMapper aiTodoMapper;

    /** F7：直接依赖工单 Mapper，避免与工单服务形成循环依赖 */
    @Autowired
    private AiCallTicketMapper aiCallTicketMapper;

    @Override
    public AiTodo selectAiTodoByTodoId(Long todoId)
    {
        return aiTodoMapper.selectAiTodoByTodoId(todoId);
    }

    @Override
    public AiCallTicket selectLinkedTicket(Long todoId)
    {
        if (todoId == null)
        {
            return null;
        }
        AiTodo todo = aiTodoMapper.selectAiTodoByTodoId(todoId);
        if (todo == null || todo.getBizId() == null
                || !BIZ_TYPE_TICKET.equalsIgnoreCase(todo.getBizType()))
        {
            return null;
        }
        return aiCallTicketMapper.selectAiCallTicketByTicketId(todo.getBizId());
    }

    @Override
    public List<AiTodo> selectAiTodoList(AiTodo aiTodo)
    {
        return aiTodoMapper.selectAiTodoList(aiTodo);
    }

    @Override
    public List<AiTodo> selectAiTodoListByUserId(Long userId)
    {
        return aiTodoMapper.selectAiTodoListByUserId(userId);
    }

    @Override
    public int insertAiTodo(AiTodo aiTodo)
    {
        if (aiTodo.getStatus() == null || aiTodo.getStatus().isEmpty()) {
            aiTodo.setStatus("0");
        }
        if (aiTodo.getPriority() == null || aiTodo.getPriority().isEmpty()) {
            aiTodo.setPriority("2");
        }
        return aiTodoMapper.insertAiTodo(aiTodo);
    }

    @Override
    public int updateAiTodo(AiTodo aiTodo)
    {
        return aiTodoMapper.updateAiTodo(aiTodo);
    }

    @Override
    public int deleteAiTodoByTodoId(Long todoId)
    {
        return aiTodoMapper.deleteAiTodoByTodoId(todoId);
    }

    @Override
    public int deleteAiTodoByTodoIds(Long[] todoIds)
    {
        return aiTodoMapper.deleteAiTodoByTodoIds(todoIds);
    }
}
