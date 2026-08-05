package ai.lawyers.system.service.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiTodo;

public interface IAiTodoService
{
    public AiTodo selectAiTodoByTodoId(Long todoId);

    public List<AiTodo> selectAiTodoList(AiTodo aiTodo);

    public List<AiTodo> selectAiTodoListByUserId(Long userId);

    public int insertAiTodo(AiTodo aiTodo);

    public int updateAiTodo(AiTodo aiTodo);

    public int deleteAiTodoByTodoId(Long todoId);

    public int deleteAiTodoByTodoIds(Long[] todoIds);
}
