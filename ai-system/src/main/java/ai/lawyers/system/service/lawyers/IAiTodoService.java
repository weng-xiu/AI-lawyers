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

    /**
     * F7：按关联业务自动关闭未完成待办（待办/延后 → 已完成），已完成与已忽略不变。
     *
     * <p>供工单办结/归档等任意入口调用，避免"仅工作台抽屉关闭才同步"的入口盲区。</p>
     *
     * @param bizType  业务类型（ticket=工单）
     * @param bizId    业务ID（工单ID）
     * @param updateBy 操作人，为空时取当前登录用户（无登录上下文则不记录）
     * @return 被关闭的待办数
     */
    public int closeTodosByBiz(String bizType, Long bizId, String updateBy);

    public int deleteAiTodoByTodoId(Long todoId);

    public int deleteAiTodoByTodoIds(Long[] todoIds);
}
