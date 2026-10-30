package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiTodo;

public interface IAiTodoService
{
    public AiTodo selectAiTodoByTodoId(Long todoId);

    /**
     * F7：查询待办关联的工单。未关联、工单已删除或工单服务不可用时返回 null。
     */
    public AiCallTicket selectLinkedTicket(Long todoId);

    public List<AiTodo> selectAiTodoList(AiTodo aiTodo);

    public List<AiTodo> selectAiTodoListByUserId(Long userId);

    public int insertAiTodo(AiTodo aiTodo);

    public int updateAiTodo(AiTodo aiTodo);

    public int deleteAiTodoByTodoId(Long todoId);

    public int deleteAiTodoByTodoIds(Long[] todoIds);
}
