package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiTodo;

public interface AiTodoMapper
{
    public AiTodo selectAiTodoByTodoId(Long todoId);

    public List<AiTodo> selectAiTodoList(AiTodo aiTodo);

    public List<AiTodo> selectAiTodoListByUserId(Long userId);

    public int insertAiTodo(AiTodo aiTodo);

    public int updateAiTodo(AiTodo aiTodo);

    /**
     * F7：按关联业务关闭未完成待办（待办/延后 → 已完成），忽略已完成与已忽略。
     *
     * @param bizType  业务类型（ticket=工单）
     * @param bizId    业务ID（工单ID）
     * @param updateBy 操作人（可为空）
     * @return 受影响行数
     */
    public int closeTodosByBiz(@Param("bizType") String bizType,
                               @Param("bizId") Long bizId,
                               @Param("updateBy") String updateBy);

    public int deleteAiTodoByTodoId(Long todoId);

    public int deleteAiTodoByTodoIds(Long[] todoIds);
}
