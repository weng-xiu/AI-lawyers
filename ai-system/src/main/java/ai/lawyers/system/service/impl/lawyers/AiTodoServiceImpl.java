package ai.lawyers.system.service.impl.lawyers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiTodo;
import ai.lawyers.system.mapper.lawyers.AiTodoMapper;
import ai.lawyers.system.service.lawyers.IAiTodoService;

@Service
public class AiTodoServiceImpl implements IAiTodoService
{
    @Autowired
    private AiTodoMapper aiTodoMapper;

    @Override
    public AiTodo selectAiTodoByTodoId(Long todoId)
    {
        return aiTodoMapper.selectAiTodoByTodoId(todoId);
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
